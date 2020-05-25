package com.hazelcast.jet.sql.impl.connector.cdc;

import com.hazelcast.function.FunctionEx;
import com.hazelcast.jet.cdc.ChangeRecord;
import com.hazelcast.jet.cdc.impl.CdcSource;
import com.hazelcast.jet.core.DAG;
import com.hazelcast.jet.core.Vertex;
import com.hazelcast.jet.impl.util.ReflectionUtils;
import com.hazelcast.jet.sql.JetSqlConnector;
import com.hazelcast.spi.impl.NodeEngine;
import com.hazelcast.sql.impl.expression.ConstantExpression;
import com.hazelcast.sql.impl.expression.Expression;
import com.hazelcast.sql.impl.row.Row;
import com.hazelcast.sql.impl.schema.ConstantTableStatistics;
import com.hazelcast.sql.impl.schema.ExternalTable.ExternalField;
import com.hazelcast.sql.impl.schema.Table;
import com.hazelcast.sql.impl.schema.TableField;
import com.hazelcast.sql.impl.type.QueryDataType;
import org.apache.commons.beanutils.PropertyUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.hazelcast.jet.core.Edge.between;
import static com.hazelcast.jet.core.EventTimePolicy.noEventTime;
import static com.hazelcast.jet.core.processor.Processors.mapP;
import static com.hazelcast.jet.core.processor.SourceProcessors.convenientTimestampedSourceP;
import static com.hazelcast.jet.impl.util.ExceptionUtil.sneakyThrow;
import static com.hazelcast.jet.impl.util.Util.toList;
import static com.hazelcast.jet.sql.impl.expression.ExpressionUtil.ZERO_ARGUMENTS_CONTEXT;

public class CdcSqlConnector implements JetSqlConnector {

    public static final String TYPE_NAME = "com.hazelcast.Cdc";

    public static final String TO_RECORD_CLASS = "recordClass";
    private static final String NAME = "name";
    private static final String INCLUDE_SCHEMA_CHANGES = "include.schema.changes";
    private static final String TOMBSTONES_ON_DELETE = "tombstones.on.delete";
    private static final String DATABASE_HISTORY = "database.history";

    @Override
    public boolean isStream() {
        return true;
    }

    @Override
    public String typeName() {
        return TYPE_NAME;
    }

    @Nonnull
    @Override
    public Table createTable(@Nonnull NodeEngine nodeEngine,
                             @Nonnull String schemaName,
                             @Nonnull String tableName,
                             @Nonnull List<ExternalField> externalFields,
                             @Nonnull Map<String, String> options) {
        // TODO validate options
        Properties cdcProperties = new Properties();
        cdcProperties.putAll(options);
        cdcProperties.remove(TO_RECORD_CLASS);
        cdcProperties.put(NAME, tableName);
        cdcProperties.put(INCLUDE_SCHEMA_CHANGES, false);
        cdcProperties.put(TOMBSTONES_ON_DELETE, false);
        cdcProperties.put(DATABASE_HISTORY, CdcSource.DatabaseHistoryImpl.class.getName());
        // TODO: "database.whitelist" & "table.whitelist" in theory could be inferred <- schemaName & tableName
        return new CdcTable(this, schemaName, tableName, new ConstantTableStatistics(0),
                toList(externalFields, TableField::new), options.get(TO_RECORD_CLASS), cdcProperties, options);
    }

    @Override
    public boolean supportsFullScanReader() {
        return true;
    }

    @Nullable
    @Override
    public Vertex fullScanReader(
            @Nonnull DAG dag,
            @Nonnull Table table0,
            @Nullable String timestampField,
            @Nonnull Expression<Boolean> predicate,
            @Nonnull List<Expression<?>> projections
    ) {
        CdcTable table = (CdcTable) table0;

        String tableName = table.getName();
        Properties properties = table.getCdcProperties();
        Vertex sourceVertex = dag.newVertex("cdc(" + tableName + ")",
                convenientTimestampedSourceP(ctx -> new CdcSource(properties),
                        CdcSource::fillBuffer,
                        noEventTime(), // TODO: should use timestamps ?
                        CdcSource::createSnapshot,
                        CdcSource::restoreSnapshot,
                        CdcSource::destroy,
                        0) // TODO: is it the correct value ?
        );

        FunctionEx<ChangeRecord, Object[]> mapFn = projectionFn(table, predicate, projections, table.getValueClass());
        Vertex filterProjectVertex = dag.newVertex("cdc-filter-project", mapP(mapFn));

        dag.edge(between(sourceVertex, filterProjectVertex).isolated());
        return filterProjectVertex;
    }

    private static FunctionEx<ChangeRecord, Object[]> projectionFn(
            Table jetTable,
            Expression<Boolean> predicate,
            List<Expression<?>> projections,
            String valueClass
    ) {
        List<String> fieldNames = toList(jetTable.getFields(), TableField::getName);

        @SuppressWarnings("unchecked")
        Expression<Boolean> predicate0 = predicate != null ? predicate
                : (Expression<Boolean>) ConstantExpression.create(QueryDataType.BOOLEAN, true);

        Class<?> recordClazz = ReflectionUtils.loadClass(valueClass);
        return record -> {
            // Operation operation = record.operation(); // TODO: filter out certain operations? expose them to the user?
            Object object = record.value().toObject(recordClazz);

            Row row = new ObjectRow(fieldNames, object);
            if (!Boolean.TRUE.equals(predicate0.eval(row, ZERO_ARGUMENTS_CONTEXT))) {
                return null;
            }
            Object[] result = new Object[projections.size()];
            for (int i = 0; i < projections.size(); i++) {
                result[i] = projections.get(i).eval(row, ZERO_ARGUMENTS_CONTEXT);
            }
            return result;
        };
    }

    private static class ObjectRow implements Row {

        private final List<String> fieldNames;
        private final Object object;

        ObjectRow(List<String> fieldNames, Object object) {
            this.fieldNames = fieldNames;
            this.object = object;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T get(int index) {
            try {
                return (T) PropertyUtils.getProperty(object, fieldNames.get(index));
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw sneakyThrow(e);
            }
        }

        @Override
        public int getColumnCount() {
            return fieldNames.size();
        }
    }
}
