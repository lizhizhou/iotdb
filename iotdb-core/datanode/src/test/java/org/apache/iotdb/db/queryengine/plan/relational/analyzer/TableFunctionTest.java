/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.queryengine.plan.relational.analyzer;

import org.apache.iotdb.db.queryengine.plan.planner.plan.LogicalQueryPlan;
import org.apache.iotdb.db.queryengine.plan.relational.planner.PlanTester;
import org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanMatchPattern;
import org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.TableFunctionProcessorMatcher;
import org.apache.iotdb.db.queryengine.plan.relational.planner.node.JoinNode;
import org.apache.iotdb.udf.api.relational.EmptyTableFunctionHandle;
import org.apache.iotdb.udf.api.relational.table.MapTableFunctionHandle;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.util.function.Consumer;

import static org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanAssert.assertPlan;
import static org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanMatchPattern.aggregation;
import static org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanMatchPattern.aggregationFunction;
import static org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanMatchPattern.anyTree;
import static org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanMatchPattern.collect;
import static org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanMatchPattern.exchange;
import static org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanMatchPattern.group;
import static org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanMatchPattern.join;
import static org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanMatchPattern.mergeSort;
import static org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanMatchPattern.output;
import static org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanMatchPattern.project;
import static org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanMatchPattern.sort;
import static org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanMatchPattern.tableFunctionProcessor;
import static org.apache.iotdb.db.queryengine.plan.relational.planner.assertions.PlanMatchPattern.tableScan;

public class TableFunctionTest {

  @Test
  public void testSimpleRowSemantic() {
    PlanTester planTester = new PlanTester();

    String sql =
        "SELECT * FROM TABLE(HOP("
            + "DATA => TABLE(table1), "
            + "TIMECOL => 'time', "
            + "SLIDE => 30m, "
            + "SIZE => 1h))";
    LogicalQueryPlan logicalQueryPlan = planTester.createPlan(sql);
    PlanMatchPattern tableScan =
        tableScan(
            "testdb.table1",
            ImmutableList.of("time", "tag1", "tag2", "tag3", "attr1", "attr2", "s1", "s2", "s3"),
            ImmutableSet.of("time", "tag1", "tag2", "tag3", "attr1", "attr2", "s1", "s2", "s3"));
    Consumer<TableFunctionProcessorMatcher.Builder> tableFunctionMatcher =
        builder ->
            builder
                .name("hop")
                .properOutputs("window_start", "window_end")
                .requiredSymbols("time")
                .handle(
                    new MapTableFunctionHandle.Builder()
                        .addProperty("SIZE", 3600000L)
                        .addProperty("SLIDE", 1800000L)
                        .addProperty("ORIGIN", 0L)
                        .build());
    // Verify full LogicalPlan
    // Output - TableFunctionProcessor - TableScan
    assertPlan(logicalQueryPlan, anyTree(tableFunctionProcessor(tableFunctionMatcher, tableScan)));
    // Verify DistributionPlan

    /*
     *   └──OutputNode
     *         └──CollectNode
     *               ├──ExchangeNode
     *               │    └──TableFunctionProcessor
     *               │        └──TableScan
     *               ├──ExchangeNode
     *               │    └──TableFunctionProcessor
     *               │        └──TableScan
     *               └──ExchangeNode
     *                    └──TableFunctionProcessor
     *                        └──TableScan
     */
    assertPlan(planTester.getFragmentPlan(0), output(collect(exchange(), exchange(), exchange())));
    assertPlan(
        planTester.getFragmentPlan(1), tableFunctionProcessor(tableFunctionMatcher, tableScan));
    assertPlan(
        planTester.getFragmentPlan(2), tableFunctionProcessor(tableFunctionMatcher, tableScan));
    assertPlan(
        planTester.getFragmentPlan(3), tableFunctionProcessor(tableFunctionMatcher, tableScan));
  }

  @Test
  public void testSimpleRowSemantic2() {
    PlanTester planTester = new PlanTester();
    String sql = "SELECT * FROM TABLE(EXCLUDE(DATA => TABLE(table1), EXCLUDE => 'attr1'))";
    LogicalQueryPlan logicalQueryPlan = planTester.createPlan(sql);
    PlanMatchPattern tableScan =
        tableScan(
            "testdb.table1",
            ImmutableMap.of(
                "time_0", "time", "tag1_1", "tag1", "tag2_2", "tag2", "tag3_3", "tag3", "attr2_4",
                "attr2", "s1_5", "s1", "s2_6", "s2", "s3_7", "s3"));

    Consumer<TableFunctionProcessorMatcher.Builder> tableFunctionMatcher =
        builder ->
            builder
                .name("exclude")
                .properOutputs("time", "tag1", "tag2", "tag3", "attr2", "s1", "s2", "s3")
                .requiredSymbols(
                    "time_0", "tag1_1", "tag2_2", "tag3_3", "attr2_4", "s1_5", "s2_6", "s3_7")
                .handle(new EmptyTableFunctionHandle());
    // Verify full LogicalPlan
    // Output - TableFunctionProcessor - TableScan
    assertPlan(logicalQueryPlan, anyTree(tableFunctionProcessor(tableFunctionMatcher, tableScan)));
    // Verify DistributionPlan
    /*
     *   └──OutputNode
     *         └──CollectNode
     *               ├──ExchangeNode
     *               │    └──TableFunctionProcessor
     *               │        └──TableScan
     *               ├──ExchangeNode
     *               │    └──TableFunctionProcessor
     *               │        └──TableScan
     *               └──ExchangeNode
     *                    └──TableFunctionProcessor
     *                         └──TableScan
     *                            ├──ExchangeNode
     *                            │     └──TableScan
     *                            └──ExchangeNode
     *                                  └──TableScan
     */
    assertPlan(planTester.getFragmentPlan(0), output(collect(exchange(), exchange(), exchange())));
    assertPlan(
        planTester.getFragmentPlan(1), tableFunctionProcessor(tableFunctionMatcher, tableScan));
    assertPlan(
        planTester.getFragmentPlan(2), tableFunctionProcessor(tableFunctionMatcher, tableScan));
    assertPlan(
        planTester.getFragmentPlan(3), tableFunctionProcessor(tableFunctionMatcher, tableScan));
  }

  @Test
  public void testSimpleSetSemantic() {
    PlanTester planTester = new PlanTester();
    String sql =
        "SELECT * FROM TABLE(REPEAT(DATA => TABLE(table1) PARTITION BY (tag1,tag2,tag3), N => 2))";
    LogicalQueryPlan logicalQueryPlan = planTester.createPlan(sql);
    PlanMatchPattern tableScan =
        tableScan(
            "testdb.table1",
            ImmutableList.of("time", "tag1", "tag2", "tag3", "attr1", "attr2", "s1", "s2", "s3"),
            ImmutableSet.of("time", "tag1", "tag2", "tag3", "attr1", "attr2", "s1", "s2", "s3"));

    Consumer<TableFunctionProcessorMatcher.Builder> tableFunctionMatcher =
        builder ->
            builder
                .name("repeat")
                .properOutputs("repeat_index")
                .requiredSymbols("time")
                .handle(new MapTableFunctionHandle.Builder().addProperty("N", 2).build());
    // Verify full LogicalPlan
    // Output - TableFunctionProcessor - GroupNode - TableScan
    assertPlan(
        logicalQueryPlan, anyTree(tableFunctionProcessor(tableFunctionMatcher, group(tableScan))));
    // Verify DistributionPlan
    /*
     *   └──OutputNode
     *         └──CollectNode
     *               ├──ExchangeNode
     *               │    └──TableFunctionProcessor
     *               │        └──TableScan
     *               ├──ExchangeNode
     *               │    └──TableFunctionProcessor
     *               │        └──TableScan
     *               └──ExchangeNode
     *                    └──TableFunctionProcessor
     *                              └──MergeSortNode
     *                                     ├──ExchangeNode
     *                                     │     └──TableScan
     *                                     └──ExchangeNode
     *                                           └──TableScan
     */
    assertPlan(planTester.getFragmentPlan(0), output(collect(exchange(), exchange(), exchange())));
    assertPlan(
        planTester.getFragmentPlan(1), tableFunctionProcessor(tableFunctionMatcher, tableScan));
    assertPlan(
        planTester.getFragmentPlan(2), tableFunctionProcessor(tableFunctionMatcher, tableScan));
    assertPlan(
        planTester.getFragmentPlan(3),
        tableFunctionProcessor(tableFunctionMatcher, mergeSort(exchange(), exchange())));
    assertPlan(planTester.getFragmentPlan(4), tableScan);
    assertPlan(planTester.getFragmentPlan(5), tableScan);
  }

  @Test
  public void testLeafFunction() {
    PlanTester planTester = new PlanTester();
    String sql = "SELECT * FROM TABLE(SPLIT('1,2,3,4,5'))";
    LogicalQueryPlan logicalQueryPlan = planTester.createPlan(sql);
    Consumer<TableFunctionProcessorMatcher.Builder> tableFunctionMatcher =
        builder ->
            builder
                .name("split")
                .properOutputs("output")
                .requiredSymbols()
                .handle(
                    new MapTableFunctionHandle.Builder()
                        .addProperty("INPUT", "1,2,3,4,5")
                        .addProperty("SPLIT", ",")
                        .build());
    // Verify full LogicalPlan
    // Output - TableFunctionProcessor - TableScan
    assertPlan(logicalQueryPlan, anyTree(tableFunctionProcessor(tableFunctionMatcher)));

    sql =
        "select * from TABLE(SPLIT('1,2,4,5')) a join TABLE(SPLIT('2,3,4')) b on a.output=b.output";
    logicalQueryPlan = planTester.createPlan(sql);
    Consumer<TableFunctionProcessorMatcher.Builder> tableFunctionMatcher1 =
        builder ->
            builder
                .name("split")
                .properOutputs("output")
                .requiredSymbols()
                .handle(
                    new MapTableFunctionHandle.Builder()
                        .addProperty("INPUT", "1,2,4,5")
                        .addProperty("SPLIT", ",")
                        .build());
    Consumer<TableFunctionProcessorMatcher.Builder> tableFunctionMatcher2 =
        builder ->
            builder
                .name("split")
                .properOutputs("output_0")
                .requiredSymbols()
                .handle(
                    new MapTableFunctionHandle.Builder()
                        .addProperty("INPUT", "2,3,4")
                        .addProperty("SPLIT", ",")
                        .build());
    // Verify full LogicalPlan
    // Output - TableFunctionProcessor - TableScan
    assertPlan(
        logicalQueryPlan,
        anyTree(
            join(
                JoinNode.JoinType.INNER,
                builder ->
                    builder
                        .left(sort(tableFunctionProcessor(tableFunctionMatcher1)))
                        .right(sort(tableFunctionProcessor(tableFunctionMatcher2)))
                        .equiCriteria("output", "output_0"))));
  }

  @Test
  public void testHybrid() {
    PlanTester planTester = new PlanTester();
    String sql =
        "SELECT tag1, tag2, tag3, window_start, window_end, count(*) FROM TABLE(HOP("
            + "DATA => TABLE(SELECT * FROM TABLE(EXCLUDE(TABLE(table1), 'attr1'))), "
            + "TIMECOL => 'time', "
            + "SLIDE => 30m, "
            + "SIZE => 1h))"
            + "group by (tag1, tag2, tag3, window_start, window_end)";
    LogicalQueryPlan logicalQueryPlan = planTester.createPlan(sql);
    PlanMatchPattern tableScan =
        tableScan(
            "testdb.table1",
            ImmutableMap.of(
                "time_0", "time", "tag1_1", "tag1", "tag2_2", "tag2", "tag3_3", "tag3", "attr2_4",
                "attr2", "s1_5", "s1", "s2_6", "s2", "s3_7", "s3"));
    Consumer<TableFunctionProcessorMatcher.Builder> excludeMatcher =
        builder ->
            builder
                .name("exclude")
                .properOutputs("time", "tag1", "tag2", "tag3", "attr2", "s1", "s2", "s3")
                .requiredSymbols(
                    "time_0", "tag1_1", "tag2_2", "tag3_3", "attr2_4", "s1_5", "s2_6", "s3_7")
                .handle(new EmptyTableFunctionHandle());
    Consumer<TableFunctionProcessorMatcher.Builder> hopMatcher =
        builder ->
            builder
                .name("hop")
                .properOutputs("window_start", "window_end")
                .requiredSymbols("time")
                .handle(
                    new MapTableFunctionHandle.Builder()
                        .addProperty("SIZE", 3600000L)
                        .addProperty("SLIDE", 1800000L)
                        .addProperty("ORIGIN", 0L)
                        .build());
    // Verify full LogicalPlan
    // Output - Aggregation - HOP - Project - EXCLUDE - TableScan
    assertPlan(
        logicalQueryPlan,
        anyTree(
            aggregation(
                ImmutableMap.of("count", aggregationFunction("count", ImmutableList.of())),
                tableFunctionProcessor(
                    hopMatcher, project(tableFunctionProcessor(excludeMatcher, tableScan))))));
  }

  @Test
  public void testSerDeserializeMapTableFunctionHandle() {
    MapTableFunctionHandle mapTableFunctionHandle =
        new MapTableFunctionHandle.Builder()
            .addProperty("key1", "value1")
            .addProperty("key2", 2)
            .addProperty("key3", 1L)
            .addProperty("key4", 3.0)
            .addProperty("key5", true)
            .addProperty("key6", 2.3f)
            .build();
    byte[] serialized = mapTableFunctionHandle.serialize();
    MapTableFunctionHandle deserialized = new MapTableFunctionHandle();
    deserialized.deserialize(serialized);
    assert mapTableFunctionHandle.equals(deserialized);
  }
}
