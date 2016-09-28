/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.jet2;

import com.hazelcast.core.PartitioningStrategy;
import com.hazelcast.jet.strategy.HashingStrategy;
import com.hazelcast.jet.strategy.MemberDistributionStrategy;
import com.hazelcast.jet.strategy.RoutingStrategy;
import com.hazelcast.jet.strategy.SerializedHashingStrategy;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.partition.strategy.StringAndPartitionAwarePartitioningStrategy;

import java.io.IOException;

/**
 * Represents an edge between two vertices in a DAG
 */
public class Edge implements IdentifiedDataSerializable {


    private Vertex from;
    private Vertex to;

    private boolean isLocal = true;
    private HashingStrategy hashingStrategy = SerializedHashingStrategy.INSTANCE;
    private MemberDistributionStrategy memberDistributionStrategy;
    private RoutingStrategy routingStrategy = RoutingStrategy.ROUND_ROBIN;
    private PartitioningStrategy partitioningStrategy = StringAndPartitionAwarePartitioningStrategy.INSTANCE;

    Edge() {

    }

    /**
     * Creates an edge between two vertices.
     *
     * @param from the origin vertex
     * @param to   the destination vertex
     */
    public Edge(Vertex from,
                Vertex to) {
        this.to = to;
        this.from = from;
    }

    /**
     * @return output vertex of edge
     */
    public Vertex getOutputVertex() {
        return to;
    }

    /**
     * Returns if the edge is local
     */
    public boolean isLocal() {
        return isLocal;
    }

    /**
     * @return input vertex of edge
     */
    public Vertex getInputVertex() {
        return from;
    }

    /**
     * @return the member distribution strategy for the edge
     */
    public MemberDistributionStrategy getMemberDistributionStrategy() {
        return memberDistributionStrategy;
    }

    /**
     * @return the routing strategy for the edge
     */
    public RoutingStrategy getRoutingStrategy() {
        return routingStrategy;
    }

    /**
     * @return the partitioning strategy for the edge
     */
    public PartitioningStrategy getPartitioningStrategy() {
        return partitioningStrategy;
    }

    /**
     * @return the hashing strategy for the edge
     */
    public HashingStrategy getHashingStrategy() {
        return hashingStrategy;
    }

    /**
     * Sets the edge to be distributed. The output of the producers will be consumed by consumers on other
     * members. When a {@link MemberDistributionStrategy} is not provided, the record will be consumed by the
     * member which is the owner of the partition the record belongs to.
     * If there are several consumers in the target member, the consumer will be determined by
     * {@link RoutingStrategy}
     */
    public Edge distributed() {
        isLocal = false;
        return this;
    }

    /**
     * Sets the edge to be local only. The output of the producers will only be consumed by consumers on the same
     * member.
     * The specific consumer instance will be determined by {@link RoutingStrategy}
     */
    public Edge local() {
        isLocal = true;
        return this;
    }

    /**
     * Sets the edge to be distributed. The output of the producers will be consumed by consumers on other
     * members. The member or members to consume will be determined by the {@link MemberDistributionStrategy}.
     * If there are several consumers in the target member(s), the consumer will be determined by
     * {@link RoutingStrategy}
     *
     * @see com.hazelcast.jet.strategy.SingleMemberDistributionStrategy
     */
    public Edge distributed(MemberDistributionStrategy distributionStrategy) {
        isLocal = false;
        memberDistributionStrategy = distributionStrategy;
        return this;
    }

    /**
     * Sets the {@link RoutingStrategy} for the edge to broadcast.
     * The output of the producer vertex will be available to all instances of the consumer vertex.
     *
     * @link RoutingStrategy.BROADCAST
     */
    public Edge broadcast() {
        routingStrategy = RoutingStrategy.BROADCAST;
        return this;
    }

    /**
     * Sets the {@link RoutingStrategy} for the edge to partitioned.
     * The output of the producer vertex will be partitioned and the partitions will be allocated
     * between instances of the consumer vertex, with the same consumer always receiving the same partitions.
     *
     * @link RoutingStrategy.PARTITIONED
     */
    public Edge partitioned() {
        routingStrategy = RoutingStrategy.PARTITIONED;
        return this;
    }

    /**
     * Sets the {@link RoutingStrategy} for the edge to partitioned with a custom partitioning strategy.
     * <p/>
     * The output of the producer vertex will be partitioned and the partitions will be allocated
     * between instances of the consumer vertex, with the same consumer always receiving the same partitions.
     *
     * @link RoutingStrategy.PARTITIONED
     */
    public Edge partitioned(PartitioningStrategy partitioningStrategy) {
        routingStrategy = RoutingStrategy.PARTITIONED;
        this.partitioningStrategy = partitioningStrategy;
        return this;
    }

    /**
     * Sets the {@link RoutingStrategy} for the edge to partitioned with a custom hashing strategy.
     * <p/>
     * The output of the producer vertex will be partitioned and the partitions will be allocated
     * between instances of the consumer vertex, with the same consumer always receiving the same partitions.
     *
     * @link RoutingStrategy.PARTITIONED
     * @see HashingStrategy
     */
    public Edge partitioned(HashingStrategy hashingStrategy) {
        routingStrategy = RoutingStrategy.PARTITIONED;
        this.hashingStrategy = hashingStrategy;
        return this;
    }

    /**
     * Sets the {@link RoutingStrategy} for the edge to partitioned with a custom partitioning and hashing strategy.
     * <p/>
     * The output of the producer vertex will be partitioned and the partitions will be allocated
     * between instances of the consumer vertex, with the same consumer always receiving the same partitions.
     *
     * @link RoutingStrategy.PARTITIONED
     * @see HashingStrategy
     * @see PartitioningStrategy
     */
    public Edge partitioned(PartitioningStrategy partitioningStrategy, HashingStrategy hashingStrategy) {
        this.routingStrategy = RoutingStrategy.PARTITIONED;
        this.partitioningStrategy = partitioningStrategy;
        this.hashingStrategy = hashingStrategy;
        return this;
    }


    @Override
    public String toString() {
        return "Edge{"
                + "from=" + from
                + ", to=" + to
                + '}';
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeObject(from);
        out.writeObject(to);
        out.writeBoolean(isLocal);

        out.writeObject(hashingStrategy);
        out.writeObject(memberDistributionStrategy);
        out.writeObject(routingStrategy);
        out.writeObject(partitioningStrategy);
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        from = in.readObject();
        to = in.readObject();
        isLocal = in.readBoolean();

        hashingStrategy = in.readObject();
        memberDistributionStrategy = in.readObject();
        routingStrategy = in.readObject();
        partitioningStrategy = in.readObject();
    }

    @Override
    public int getFactoryId() {
        return JetDataSerializerHook.FACTORY_ID;
    }

    @Override
    public int getId() {
        return JetDataSerializerHook.EDGE;
    }
}
