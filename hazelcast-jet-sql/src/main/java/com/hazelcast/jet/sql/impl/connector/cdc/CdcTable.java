/*
 * Copyright (c) 2008-2020, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.sql.impl.connector.cdc;

import com.hazelcast.jet.sql.JetSqlConnector;
import com.hazelcast.jet.sql.impl.schema.JetTable;
import com.hazelcast.sql.impl.schema.TableField;
import com.hazelcast.sql.impl.schema.TableStatistics;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class CdcTable extends JetTable {

    private final String valueClass;
    private final Properties cdcProperties;

    public CdcTable(
            @Nonnull JetSqlConnector sqlConnector,
            @Nonnull String schemaName,
            @Nonnull String name,
            @Nonnull TableStatistics statistics,
            @Nonnull List<TableField> fields,
            @Nonnull String valueClass,
            @Nonnull Properties cdcProperties,
            @Nonnull Map<String, String> ddlOptions
    ) {
        super(sqlConnector, fields, schemaName, name, statistics, ddlOptions);

        this.valueClass = valueClass;
        this.cdcProperties = cdcProperties;
    }

    public String getValueClass() {
        return valueClass;
    }

    public Properties getCdcProperties() {
        return cdcProperties;
    }

    @Override
    public boolean isStream() {
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{tableName=" + getName() + '}';
    }
}
