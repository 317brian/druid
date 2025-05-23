/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.tests.coordinator.duty;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.apache.datasketches.hll.TgtHllType;
import org.apache.druid.data.input.MaxSizeSplitHintSpec;
import org.apache.druid.data.input.impl.DimensionSchema;
import org.apache.druid.data.input.impl.DimensionsSpec;
import org.apache.druid.data.input.impl.StringDimensionSchema;
import org.apache.druid.indexer.CompactionEngine;
import org.apache.druid.indexer.TaskState;
import org.apache.druid.indexer.granularity.UniformGranularitySpec;
import org.apache.druid.indexer.partitions.DimensionRangePartitionsSpec;
import org.apache.druid.indexer.partitions.DynamicPartitionsSpec;
import org.apache.druid.indexer.partitions.HashedPartitionsSpec;
import org.apache.druid.indexer.partitions.PartitionsSpec;
import org.apache.druid.indexing.common.task.CompactionIntervalSpec;
import org.apache.druid.indexing.common.task.CompactionTask;
import org.apache.druid.indexing.overlord.http.TaskPayloadResponse;
import org.apache.druid.java.util.common.DateTimes;
import org.apache.druid.java.util.common.ISE;
import org.apache.druid.java.util.common.Intervals;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.java.util.common.granularity.Granularities;
import org.apache.druid.java.util.common.granularity.Granularity;
import org.apache.druid.java.util.common.granularity.PeriodGranularity;
import org.apache.druid.java.util.common.logger.Logger;
import org.apache.druid.query.aggregation.AggregatorFactory;
import org.apache.druid.query.aggregation.CountAggregatorFactory;
import org.apache.druid.query.aggregation.DoubleSumAggregatorFactory;
import org.apache.druid.query.aggregation.FloatSumAggregatorFactory;
import org.apache.druid.query.aggregation.LongSumAggregatorFactory;
import org.apache.druid.query.aggregation.datasketches.hll.HllSketchBuildAggregatorFactory;
import org.apache.druid.query.aggregation.datasketches.quantiles.DoublesSketchAggregatorFactory;
import org.apache.druid.query.aggregation.datasketches.theta.SketchMergeAggregatorFactory;
import org.apache.druid.query.filter.SelectorDimFilter;
import org.apache.druid.segment.AutoTypeColumnSchema;
import org.apache.druid.segment.column.ColumnType;
import org.apache.druid.segment.transform.CompactionTransformSpec;
import org.apache.druid.server.compaction.FixedIntervalOrderPolicy;
import org.apache.druid.server.coordinator.AutoCompactionSnapshot;
import org.apache.druid.server.coordinator.ClusterCompactionConfig;
import org.apache.druid.server.coordinator.DataSourceCompactionConfig;
import org.apache.druid.server.coordinator.DruidCompactionConfig;
import org.apache.druid.server.coordinator.InlineSchemaDataSourceCompactionConfig;
import org.apache.druid.server.coordinator.UserCompactionTaskDimensionsConfig;
import org.apache.druid.server.coordinator.UserCompactionTaskGranularityConfig;
import org.apache.druid.server.coordinator.UserCompactionTaskIOConfig;
import org.apache.druid.server.coordinator.UserCompactionTaskQueryTuningConfig;
import org.apache.druid.testing.IntegrationTestingConfig;
import org.apache.druid.testing.clients.CompactionResourceTestClient;
import org.apache.druid.testing.clients.TaskResponseObject;
import org.apache.druid.testing.guice.DruidTestModuleFactory;
import org.apache.druid.testing.utils.ITRetryUtil;
import org.apache.druid.tests.TestNGGroup;
import org.apache.druid.tests.indexer.AbstractITBatchIndexTest;
import org.apache.druid.tests.indexer.AbstractIndexerTest;
import org.apache.druid.timeline.DataSegment;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.joda.time.chrono.ISOChronology;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Test(groups = {TestNGGroup.COMPACTION})
@Guice(moduleFactory = DruidTestModuleFactory.class)
public class ITAutoCompactionTest extends AbstractIndexerTest
{
  private static final Logger LOG = new Logger(ITAutoCompactionTest.class);
  private static final String INDEX_TASK = "/indexer/wikipedia_index_task.json";
  private static final String INDEX_TASK_WITH_GRANULARITY_SPEC = "/indexer/wikipedia_index_task_with_granularity_spec.json";
  private static final String INDEX_TASK_WITH_DIMENSION_SPEC = "/indexer/wikipedia_index_task_with_dimension_spec.json";
  private static final String INDEX_ROLLUP_QUERIES_RESOURCE = "/indexer/wikipedia_index_rollup_queries.json";
  private static final String INDEX_ROLLUP_SKETCH_QUERIES_RESOURCE = "/indexer/wikipedia_index_sketch_queries.json";
  private static final String INDEX_QUERIES_RESOURCE = "/indexer/wikipedia_index_queries.json";
  private static final String INDEX_TASK_WITH_ROLLUP_FOR_PRESERVE_METRICS = "/indexer/wikipedia_index_rollup_preserve_metric.json";
  private static final String INDEX_TASK_WITHOUT_ROLLUP_FOR_PRESERVE_METRICS = "/indexer/wikipedia_index_no_rollup_preserve_metric.json";
  private static final int MAX_ROWS_PER_SEGMENT_COMPACTED = 10000;
  private static final Period NO_SKIP_OFFSET = Period.seconds(0);
  private static final FixedIntervalOrderPolicy COMPACT_NOTHING_POLICY = new FixedIntervalOrderPolicy(List.of());

  @DataProvider(name = "engine")
  public static Object[][] engine()
  {
    return new Object[][]{{CompactionEngine.NATIVE}};
  }

  @DataProvider(name = "useSupervisors")
  public static Object[][] useSupervisors()
  {
    return new Object[][]{{true}, {false}};
  }

  @Inject
  protected CompactionResourceTestClient compactionResource;

  @Inject
  private IntegrationTestingConfig config;

  private String fullDatasourceName;

  @BeforeMethod
  public void setup() throws Exception
  {
    // Set compaction slot to 5
    updateCompactionTaskSlot(0.5, 10);
    fullDatasourceName = "wikipedia_index_test_" + UUID.randomUUID() + config.getExtraDatasourceNameSuffix();
  }

  @Test
  public void testAutoCompactionRowWithMetricAndRowWithoutMetricShouldPreserveExistingMetricsUsingAggregatorWithDifferentReturnType() throws Exception
  {
    // added = null, count = 2, sum_added = 62, quantilesDoublesSketch = 2, thetaSketch = 2, HLLSketchBuild = 2
    loadData(INDEX_TASK_WITH_ROLLUP_FOR_PRESERVE_METRICS);
    // added = 31, count = null, sum_added = null, quantilesDoublesSketch = null, thetaSketch = null, HLLSketchBuild = null
    loadData(INDEX_TASK_WITHOUT_ROLLUP_FOR_PRESERVE_METRICS);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 2 segments across 1 days...
      verifySegmentsCount(2);
      ArrayList<Object> nullList = new ArrayList<>();
      nullList.add(null);
      Map<String, Object> queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(nullList)), ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(31))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "count",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(2))), ImmutableMap.of("events", ImmutableList.of(nullList)))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "sum_added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(62))), ImmutableMap.of("events", ImmutableList.of(nullList)))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%QUANTILESRESULT%%", 2,
          "%%THETARESULT%%", 2.0,
          "%%HLLRESULT%%", 2
      );
      verifyQuery(INDEX_ROLLUP_SKETCH_QUERIES_RESOURCE, queryAndResultFields);

      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(null, null, true),
          new UserCompactionTaskDimensionsConfig(DimensionsSpec.getDefaultSchemas(ImmutableList.of("language"))),
          null,
          new AggregatorFactory[]{
              new CountAggregatorFactory("count"),
              // FloatSumAggregator combine method takes in two Float but return Double
              new FloatSumAggregatorFactory("sum_added", "added"),
              new SketchMergeAggregatorFactory("thetaSketch", "user", 16384, true, false, null),
              new HllSketchBuildAggregatorFactory("HLLSketchBuild", "user", 12, TgtHllType.HLL_4.name(), null, false, false),
              new DoublesSketchAggregatorFactory("quantilesDoublesSketch", "delta", 128, 1000000000L, null)
          },
          false,
          CompactionEngine.NATIVE
      );
      // should now only have 1 row after compaction
      // added = null, count = 3, sum_added = 93.0
      forceTriggerAutoCompaction(1);

      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(nullList)))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "count",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(3))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "sum_added",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(93.0f))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%QUANTILESRESULT%%", 3,
          "%%THETARESULT%%", 3.0,
          "%%HLLRESULT%%", 3
      );
      verifyQuery(INDEX_ROLLUP_SKETCH_QUERIES_RESOURCE, queryAndResultFields);

      verifySegmentsCompacted(1, MAX_ROWS_PER_SEGMENT_COMPACTED);
      checkCompactionIntervals(intervalsBeforeCompaction);

      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      // Verify rollup segments does not get compacted again
      forceTriggerAutoCompaction(1);
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertEquals(compactTasksAfter.size(), compactTasksBefore.size());
    }
  }

  @Test
  public void testAutoCompactionRowWithMetricAndRowWithoutMetricShouldPreserveExistingMetrics() throws Exception
  {
    // added = null, count = 2, sum_added = 62, quantilesDoublesSketch = 2, thetaSketch = 2, HLLSketchBuild = 2
    loadData(INDEX_TASK_WITH_ROLLUP_FOR_PRESERVE_METRICS);
    // added = 31, count = null, sum_added = null, quantilesDoublesSketch = null, thetaSketch = null, HLLSketchBuild = null
    loadData(INDEX_TASK_WITHOUT_ROLLUP_FOR_PRESERVE_METRICS);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 2 segments across 1 days...
      verifySegmentsCount(2);
      ArrayList<Object> nullList = new ArrayList<>();
      nullList.add(null);
      Map<String, Object> queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(nullList)), ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(31))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "count",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(2))), ImmutableMap.of("events", ImmutableList.of(nullList)))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "sum_added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(62))), ImmutableMap.of("events", ImmutableList.of(nullList)))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%QUANTILESRESULT%%", 2,
          "%%THETARESULT%%", 2.0,
          "%%HLLRESULT%%", 2
      );
      verifyQuery(INDEX_ROLLUP_SKETCH_QUERIES_RESOURCE, queryAndResultFields);

      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(null, null, true),
          new UserCompactionTaskDimensionsConfig(DimensionsSpec.getDefaultSchemas(ImmutableList.of("language"))),
          null,
          new AggregatorFactory[]{
              new CountAggregatorFactory("count"),
              new LongSumAggregatorFactory("sum_added", "added"),
              new SketchMergeAggregatorFactory("thetaSketch", "user", 16384, true, false, null),
              new HllSketchBuildAggregatorFactory(
                  "HLLSketchBuild",
                  "user",
                  12,
                  TgtHllType.HLL_4.name(),
                  null,
                  false,
                  false
              ),
              new DoublesSketchAggregatorFactory("quantilesDoublesSketch", "delta", 128, 1000000000L, null)
          },
          false,
          CompactionEngine.NATIVE
      );
      // should now only have 1 row after compaction
      // added = null, count = 3, sum_added = 93
      forceTriggerAutoCompaction(1);

      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(nullList)))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "count",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(3))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "sum_added",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(93))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%QUANTILESRESULT%%", 3,
          "%%THETARESULT%%", 3.0,
          "%%HLLRESULT%%", 3
      );
      verifyQuery(INDEX_ROLLUP_SKETCH_QUERIES_RESOURCE, queryAndResultFields);

      verifySegmentsCompacted(1, MAX_ROWS_PER_SEGMENT_COMPACTED);
      checkCompactionIntervals(intervalsBeforeCompaction);

      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      // Verify rollup segments does not get compacted again
      forceTriggerAutoCompaction(1);
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertEquals(compactTasksAfter.size(), compactTasksBefore.size());
    }
  }

  @Test()
  public void testAutoCompactionOnlyRowsWithoutMetricShouldAddNewMetrics() throws Exception
  {
    // added = 31, count = null, sum_added = null
    loadData(INDEX_TASK_WITHOUT_ROLLUP_FOR_PRESERVE_METRICS);
    // added = 31, count = null, sum_added = null
    loadData(INDEX_TASK_WITHOUT_ROLLUP_FOR_PRESERVE_METRICS);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 2 segments across 1 days...
      verifySegmentsCount(2);
      ArrayList<Object> nullList = new ArrayList<>();
      nullList.add(null);
      Map<String, Object> queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(31))), ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(31))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);

      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(null, null, true),
          new UserCompactionTaskDimensionsConfig(DimensionsSpec.getDefaultSchemas(ImmutableList.of("language"))),
          null,
          new AggregatorFactory[] {new CountAggregatorFactory("count"), new LongSumAggregatorFactory("sum_added", "added")},
          false,
          CompactionEngine.NATIVE
      );
      // should now only have 1 row after compaction
      // added = null, count = 2, sum_added = 62
      forceTriggerAutoCompaction(1);

      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(nullList)))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "count",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(2))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "sum_added",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(62))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);

      verifySegmentsCompacted(1, MAX_ROWS_PER_SEGMENT_COMPACTED);
      checkCompactionIntervals(intervalsBeforeCompaction);

      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      // Verify rollup segments does not get compacted again
      forceTriggerAutoCompaction(1);
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertEquals(compactTasksAfter.size(), compactTasksBefore.size());
    }
  }

  @Test(dataProvider = "engine")
  public void testAutoCompactionWithMetricColumnSameAsInputColShouldOverwriteInputWithMetrics(CompactionEngine engine)
      throws Exception
  {
    // added = 31
    loadData(INDEX_TASK_WITHOUT_ROLLUP_FOR_PRESERVE_METRICS);
    // added = 31
    loadData(INDEX_TASK_WITHOUT_ROLLUP_FOR_PRESERVE_METRICS);
    if (engine == CompactionEngine.MSQ) {
      updateCompactionTaskSlot(0.1, 2);
    }
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 2 segments across 1 days...
      verifySegmentsCount(2);
      Map<String, Object> queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(31))), ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(31))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);

      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(null, null, true),
          new UserCompactionTaskDimensionsConfig(DimensionsSpec.getDefaultSchemas(ImmutableList.of("language"))),
          null,
          new AggregatorFactory[] {new LongSumAggregatorFactory("added", "added")},
          false,
          engine
      );
      // should now only have 1 row after compaction
      // added = 62
      forceTriggerAutoCompaction(1);

      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(62))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);

      verifySegmentsCompacted(1, MAX_ROWS_PER_SEGMENT_COMPACTED);
      checkCompactionIntervals(intervalsBeforeCompaction);

      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      // Verify rollup segments does not get compacted again
      forceTriggerAutoCompaction(1);
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertEquals(compactTasksAfter.size(), compactTasksBefore.size());
    }
  }

  @Test
  public void testAutoCompactionOnlyRowsWithMetricShouldPreserveExistingMetrics() throws Exception
  {
    // added = null, count = 2, sum_added = 62
    loadData(INDEX_TASK_WITH_ROLLUP_FOR_PRESERVE_METRICS);
    // added = null, count = 2, sum_added = 62
    loadData(INDEX_TASK_WITH_ROLLUP_FOR_PRESERVE_METRICS);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 2 segments across 1 days...
      verifySegmentsCount(2);
      Map<String, Object> queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "count",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(2))), ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(2))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "sum_added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(62))), ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(62))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);

      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(null, null, true),
          new UserCompactionTaskDimensionsConfig(DimensionsSpec.getDefaultSchemas(ImmutableList.of("language"))),
          null,
          new AggregatorFactory[] {new CountAggregatorFactory("count"), new LongSumAggregatorFactory("sum_added", "added")},
          false,
          CompactionEngine.NATIVE
      );
      // should now only have 1 row after compaction
      // added = null, count = 4, sum_added = 124
      forceTriggerAutoCompaction(1);

      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "count",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(4))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "sum_added",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(124))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);

      verifySegmentsCompacted(1, MAX_ROWS_PER_SEGMENT_COMPACTED);
      checkCompactionIntervals(intervalsBeforeCompaction);

      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      // Verify rollup segments does not get compacted again
      forceTriggerAutoCompaction(1);
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertEquals(compactTasksAfter.size(), compactTasksBefore.size());
    }
  }

  @Test(dataProvider = "engine")
  public void testAutoCompactionPreservesCreateBitmapIndexInDimensionSchema(CompactionEngine engine) throws Exception
  {
    loadData(INDEX_TASK);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)
      verifySegmentsCount(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);

      LOG.info("Auto compaction test with YEAR segment granularity, dropExisting is true");
      Granularity newSegmentGranularity = Granularities.YEAR;

      List<DimensionSchema> dimensionSchemas = ImmutableList.of(
          new StringDimensionSchema("language", DimensionSchema.MultiValueHandling.SORTED_ARRAY, false),
          new AutoTypeColumnSchema("deleted", ColumnType.DOUBLE)
      );

      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(newSegmentGranularity, null, true),
          new UserCompactionTaskDimensionsConfig(dimensionSchemas),
          null,
          new AggregatorFactory[] {new LongSumAggregatorFactory("added", "added")},
          true,
          engine
      );
      // Compacted into 1 segment for the entire year.
      forceTriggerAutoCompaction(1);
      verifySegmentsCompacted(1, MAX_ROWS_PER_SEGMENT_COMPACTED);
      verifySegmentsCompactedDimensionSchema(dimensionSchemas);
    }
  }

  @Test(dataProvider = "engine")
  public void testAutoCompactionRollsUpMultiValueDimensionsWithoutUnnest(CompactionEngine engine) throws Exception
  {
    loadData(INDEX_TASK);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)
      verifySegmentsCount(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);

      LOG.info("Auto compaction test with YEAR segment granularity, DAY query granularity, dropExisting is true");

      List<DimensionSchema> dimensionSchemas = ImmutableList.of(
          new StringDimensionSchema("language", null, true),
          new StringDimensionSchema("tags", DimensionSchema.MultiValueHandling.SORTED_ARRAY, true)
      );

      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(Granularities.YEAR, Granularities.DAY, true),
          new UserCompactionTaskDimensionsConfig(dimensionSchemas),
          null,
          new AggregatorFactory[] {new LongSumAggregatorFactory("added", "added")},
          true,
          engine
      );
      // Compacted into 1 segment for the entire year.
      forceTriggerAutoCompaction(1);
      Map<String, Object> queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%",
          "added",
          "%%EXPECTED_COUNT_RESULT%%",
          1,
          "%%EXPECTED_SCAN_RESULT%%",
          ImmutableList.of(
              ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(516)))
          )
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      verifySegmentsCompacted(1, MAX_ROWS_PER_SEGMENT_COMPACTED);
      verifySegmentsCompactedDimensionSchema(dimensionSchemas);
    }
  }

  @Test
  public void testAutoCompactionDutySubmitAndVerifyCompaction() throws Exception
  {
    loadData(INDEX_TASK);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);

      submitCompactionConfig(MAX_ROWS_PER_SEGMENT_COMPACTED, Period.days(1), CompactionEngine.NATIVE);
      //...compacted into 1 new segment for 1 day. 1 day compacted and 1 day skipped/remains uncompacted. (3 total)
      forceTriggerAutoCompaction(3);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(1, MAX_ROWS_PER_SEGMENT_COMPACTED);
      checkCompactionIntervals(intervalsBeforeCompaction);
      getAndAssertCompactionStatus(
          fullDatasourceName,
          AutoCompactionSnapshot.ScheduleStatus.RUNNING,
          Matchers.equalTo(0L),
          Matchers.greaterThan(0L),
          Matchers.greaterThan(0L),
          0,
          2,
          2,
          0,
          1,
          1);
      submitCompactionConfig(MAX_ROWS_PER_SEGMENT_COMPACTED, NO_SKIP_OFFSET, CompactionEngine.NATIVE);
      //...compacted into 1 new segment for the remaining one day. 2 day compacted and 0 day uncompacted. (2 total)
      forceTriggerAutoCompaction(2);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(2, MAX_ROWS_PER_SEGMENT_COMPACTED);
      checkCompactionIntervals(intervalsBeforeCompaction);
      getAndAssertCompactionStatus(
          fullDatasourceName,
          AutoCompactionSnapshot.ScheduleStatus.RUNNING,
          Matchers.equalTo(0L),
          Matchers.greaterThan(0L),
          Matchers.equalTo(0L),
          0,
          3,
          0,
          0,
          2,
          0);
    }
  }

  @Test(dataProvider = "engine")
  public void testAutoCompactionDutyCanUpdateCompactionConfig(CompactionEngine engine) throws Exception
  {
    loadData(INDEX_TASK);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);

      // Dummy compaction config which will be overwritten
      submitCompactionConfig(10000, NO_SKIP_OFFSET, engine);
      // New compaction config should overwrites the existing compaction config
      submitCompactionConfig(1, NO_SKIP_OFFSET, engine);

      LOG.info("Auto compaction test with dynamic partitioning");

      // Instead of merging segments, the updated config will split segments!
      //...compacted into 10 new segments across 2 days. 5 new segments each day (10 total)
      forceTriggerAutoCompaction(10);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(10, 1);
      checkCompactionIntervals(intervalsBeforeCompaction);

      if (engine == CompactionEngine.NATIVE) {
        // HashedPartitionsSpec not supported by MSQ.
        LOG.info("Auto compaction test with hash partitioning");

        final HashedPartitionsSpec hashedPartitionsSpec = new HashedPartitionsSpec(null, 3, null);
        submitCompactionConfig(hashedPartitionsSpec, NO_SKIP_OFFSET, 1, null, null, null, null, false, engine);
        // 3 segments for both 2013-08-31 and 2013-09-01. (Note that numShards guarantees max shards but not exact
        // number of final shards, since some shards may end up empty.)
        forceTriggerAutoCompaction(6);
        verifyQuery(INDEX_QUERIES_RESOURCE);
        verifySegmentsCompacted(hashedPartitionsSpec, 6);
        checkCompactionIntervals(intervalsBeforeCompaction);
      }

      LOG.info("Auto compaction test with range partitioning");

      final DimensionRangePartitionsSpec inputRangePartitionsSpec = new DimensionRangePartitionsSpec(
          5,
          null,
          ImmutableList.of("city"),
          false
      );
      DimensionRangePartitionsSpec expectedRangePartitionsSpec = inputRangePartitionsSpec;
      if (engine == CompactionEngine.MSQ) {
        // Range spec is transformed to its effective maxRowsPerSegment equivalent in MSQ
        expectedRangePartitionsSpec = new DimensionRangePartitionsSpec(
            null,
            7,
            ImmutableList.of("city"),
            false
        );
      }
      submitCompactionConfig(inputRangePartitionsSpec, NO_SKIP_OFFSET, 1, null, null, null, null, false, engine);
      forceTriggerAutoCompaction(2);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(expectedRangePartitionsSpec, 2);
      checkCompactionIntervals(intervalsBeforeCompaction);
    }
  }

  @Test(dataProvider = "engine")
  public void testAutoCompactionDutyCanDeleteCompactionConfig(CompactionEngine engine) throws Exception
  {
    loadData(INDEX_TASK);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);

      submitCompactionConfig(MAX_ROWS_PER_SEGMENT_COMPACTED, NO_SKIP_OFFSET, engine);
      deleteCompactionConfig();

      // ...should remains unchanged (4 total)
      forceTriggerAutoCompaction(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(0, null);
      // Auto compaction stats should be deleted as compacation config was deleted
      Assert.assertNull(compactionResource.getCompactionStatus(fullDatasourceName));
      checkCompactionIntervals(intervalsBeforeCompaction);
    }
  }

  @Test
  public void testAutoCompactionDutyCanUpdateTaskSlots() throws Exception
  {
    // Set compactionTaskSlotRatio to 0 to prevent any compaction
    updateCompactionTaskSlot(0, 0);
    loadData(INDEX_TASK);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);

      submitCompactionConfig(MAX_ROWS_PER_SEGMENT_COMPACTED, NO_SKIP_OFFSET, CompactionEngine.NATIVE);
      // ...should remains unchanged (4 total)
      forceTriggerAutoCompaction(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(0, null);
      checkCompactionIntervals(intervalsBeforeCompaction);
      Assert.assertNull(compactionResource.getCompactionStatus(fullDatasourceName));
      // Update compaction slots to be 1
      updateCompactionTaskSlot(1, 1);
      // One day compacted (1 new segment) and one day remains uncompacted. (3 total)
      forceTriggerAutoCompaction(3);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(1, MAX_ROWS_PER_SEGMENT_COMPACTED);
      checkCompactionIntervals(intervalsBeforeCompaction);
      getAndAssertCompactionStatus(
          fullDatasourceName,
          AutoCompactionSnapshot.ScheduleStatus.RUNNING,
          Matchers.greaterThan(0L),
          Matchers.greaterThan(0L),
          Matchers.equalTo(0L),
          2,
          2,
          0,
          1,
          1,
          0);
      MatcherAssert.assertThat(
          Long.parseLong(compactionResource.getCompactionProgress(fullDatasourceName).get("remainingSegmentSize")),
          Matchers.greaterThan(0L)
      );
      // Run compaction again to compact the remaining day
      // Remaining day compacted (1 new segment). Now both days compacted (2 total)
      forceTriggerAutoCompaction(2);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(2, MAX_ROWS_PER_SEGMENT_COMPACTED);
      checkCompactionIntervals(intervalsBeforeCompaction);
      getAndAssertCompactionStatus(
          fullDatasourceName,
          AutoCompactionSnapshot.ScheduleStatus.RUNNING,
          Matchers.equalTo(0L),
          Matchers.greaterThan(0L),
          Matchers.equalTo(0L),
          0,
          3,
          0,
          0,
          2,
          0);
    }
  }

  @Test(dataProvider = "engine")
  public void testAutoCompactionDutyWithSegmentGranularityAndWithDropExistingTrue(CompactionEngine engine) throws Exception
  {
    // Interval is "2013-08-31/2013-09-02", segment gran is DAY,
    // "maxRowsPerSegment": 3
    // input files:
    //    "/resources/data/batch_index/json/wikipedia_index_data1.json",
    //        3rows -> "2013-08-31T01:02:33Z", "2013-08-31T03:32:45Z", "2013-08-31T07:11:21Z"
    //     "/resources/data/batch_index/json/wikipedia_index_data2.json",
    //       3 rows -> "2013-08-31T11:58:39Z", "2013-08-31T12:41:27Z", "2013-09-01T01:02:33Z"
    //     "/resources/data/batch_index/json/wikipedia_index_data3.json"
    //       4 rows -> "2013-09-01T03:32:45Z", "2013-09-01T07:11:21Z", "2013-09-01T11:58:39Z", "2013-09-01T12:41:27Z"
    //      Summary of data:
    //       5 rows @ 2013-08031 and 5 at 2013-0901, TWO days have data only
    //      Initial load/ingestion: DAY, "intervals" : [ "2013-08-31/2013-09-02" ], Four segments, no tombstones
    //      1st compaction: YEAR: 10 rows during 2013 (4 segments of at most three rows each)
    //              "interval": "2013-01-01T00:00:00.000Z/2014-01-01T00:00:00.000Z",
    //      2nd compaction: MONTH: 5 rows @ 2013-08 (two segments), 5 rows @ 2013-09 (two segments)
    //              "interval": "2013-01-01T00:00:00.000Z/2014-01-01T00:00:00.000Z",
    //                             Four data segments (two months) and 10 tombstones for remaining months
    //      3d compaction: SEMESTER:  5 rows @ 2013-08-31 (two segments), 5 rows @ 2013-09-01 (two segments),
    //               2 compactions were generated for year 2013; one for each semester to be compacted of the whole year.
    //
    loadData(INDEX_TASK);

    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);


      LOG.info("Auto compaction test with YEAR segment granularity, dropExisting is true");
      Granularity newGranularity = Granularities.YEAR;
      // Set dropExisting to true
      // "interval": "2013-01-01T00:00:00.000Z/2014-01-01T00:00:00.000Z",
      submitCompactionConfig(
          1000,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(newGranularity, null, null),
          true,
          engine
      );

      List<String> expectedIntervalAfterCompaction = new ArrayList<>();
      for (String interval : intervalsBeforeCompaction) {
        for (Interval newinterval : newGranularity.getIterable(new Interval(interval, ISOChronology.getInstanceUTC()))) {
          expectedIntervalAfterCompaction.add(newinterval.toString());
        }
      }
      forceTriggerAutoCompaction(1);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(1, 1000);
      checkCompactionIntervals(expectedIntervalAfterCompaction);


      LOG.info("Auto compaction test with MONTH segment granularity, dropExisting is true");
      //  "interval": "2013-01-01T00:00:00.000Z/2014-01-01T00:00:00.000Z",
      newGranularity = Granularities.MONTH;
      // Set dropExisting to true
      submitCompactionConfig(
          1000,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(newGranularity, null, null),
          true,
          engine
      );

      // Since dropExisting is set to true...
      // Again data is only in two days
      // The earlier segment with YEAR granularity will be completely covered, overshadowed, by the
      // new MONTH segments for data and tombstones for days with no data
      // Hence, we will only have 2013-08 to 2013-09 months with data
      // plus 12 tombstones
      final List<String> intervalsAfterYEARCompactionButBeforeMONTHCompaction =
          coordinator.getSegmentIntervals(fullDatasourceName);
      expectedIntervalAfterCompaction = new ArrayList<>();
      for (String interval : intervalsAfterYEARCompactionButBeforeMONTHCompaction) {
        for (Interval newinterval : newGranularity.getIterable(new Interval(interval, ISOChronology.getInstanceUTC()))) {
          expectedIntervalAfterCompaction.add(newinterval.toString());
        }
      }
      forceTriggerAutoCompaction(12);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifyTombstones(10);
      verifySegmentsCompacted(12, 1000);
      checkCompactionIntervals(expectedIntervalAfterCompaction);

      LOG.info("Auto compaction test with SEMESTER segment granularity, dropExisting is true, over tombstones");
      // only reason is semester and not quarter or month is to minimize time in the test but to
      // ensure that one of the compactions compacts *only* tombstones. The first semester will
      // compact only tombstones, so it should be a tombstone itself.
      newGranularity = new PeriodGranularity(new Period("P6M"), null, DateTimeZone.UTC);
      // Set dropExisting to true
      submitCompactionConfig(
          1000,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(newGranularity, null, null),
          true,
          engine
      );

      // Since dropExisting is set to true...
      // The earlier 12 segments with MONTH granularity will be completely covered, overshadowed, by the
      // new PT6M segments for data and tombstones for days with no data
      // Hence, we will have two segments, one tombstone for the first semester and one data segment for the second.
      forceTriggerAutoCompaction(2); // two semesters compacted
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifyTombstones(1);
      verifySegmentsCompacted(2, 1000);

      expectedIntervalAfterCompaction =
          Arrays.asList("2013-01-01T00:00:00.000Z/2013-07-01T00:00:00.000Z",
                        "2013-07-01T00:00:00.000Z/2014-01-01T00:00:00.000Z"
          );
      checkCompactionIntervals(expectedIntervalAfterCompaction);

      // verify that autocompaction completed  before
      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      forceTriggerAutoCompaction(2);
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertEquals(compactTasksAfter.size(), compactTasksBefore.size());

    }
  }

  @Test(dataProvider = "engine")
  public void testAutoCompactionDutyWithSegmentGranularityAndWithDropExistingTrueThenFalse(CompactionEngine engine) throws Exception
  {
    // Interval is "2013-08-31/2013-09-02", segment gran is DAY,
    // "maxRowsPerSegment": 3
    // input files:
    //    "/resources/data/batch_index/json/wikipedia_index_data1.json",
    //        3rows -> "2013-08-31T01:02:33Z", "2013-08-31T03:32:45Z", "2013-08-31T07:11:21Z"
    //     "/resources/data/batch_index/json/wikipedia_index_data2.json",
    //       3 rows -> "2013-08-31T11:58:39Z", "2013-08-31T12:41:27Z", "2013-09-01T01:02:33Z"
    //     "/resources/data/batch_index/json/wikipedia_index_data3.json"
    //       4 rows -> "2013-09-01T03:32:45Z", "2013-09-01T07:11:21Z", "2013-09-01T11:58:39Z", "2013-09-01T12:41:27Z"
    //      Summary of data:
    //       5 rows @ 2013-08031 and 5 at 2013-0901, TWO days have data only
    //      Initial load/ingestion: DAY, "intervals" : [ "2013-08-31/2013-09-02" ], Four segments, no tombstones
    //      1st compaction: YEAR: 10 rows during 2013 (4 segments of at most three rows each)
    //              "interval": "2013-01-01T00:00:00.000Z/2014-01-01T00:00:00.000Z",
    //      2nd compaction: MONTH: 5 rows @ 2013-08 (two segments), 5 rows @ 2013-09 (two segments)
    //              "interval": "2013-01-01T00:00:00.000Z/2014-01-01T00:00:00.000Z",
    //                             Four data segments (two months) and 10 tombstones for remaining months
    //      3d compaction: SEMESTER:  5 rows @ 2013-08-31, 5 rows @ 2013-09-01 (two segment),
    //               2 compactions were generated for year 2013; one for each semester to be compacted of the whole year.
    //
    loadData(INDEX_TASK);

    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);


      LOG.info("Auto compaction test with YEAR segment granularity, dropExisting is true");
      Granularity newGranularity = Granularities.YEAR;
      // Set dropExisting to true
      // "interval": "2013-01-01T00:00:00.000Z/2014-01-01T00:00:00.000Z",
      submitCompactionConfig(
          1000,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(newGranularity, null, null),
          true,
          engine
      );

      List<String> expectedIntervalAfterCompaction = new ArrayList<>();
      for (String interval : intervalsBeforeCompaction) {
        for (Interval newinterval : newGranularity.getIterable(new Interval(interval, ISOChronology.getInstanceUTC()))) {
          expectedIntervalAfterCompaction.add(newinterval.toString());
        }
      }
      forceTriggerAutoCompaction(1);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(1, 1000);
      checkCompactionIntervals(expectedIntervalAfterCompaction);


      LOG.info("Auto compaction test with MONTH segment granularity, dropExisting is true");
      //  "interval": "2013-01-01T00:00:00.000Z/2014-01-01T00:00:00.000Z",
      newGranularity = Granularities.MONTH;
      // Set dropExisting to true
      submitCompactionConfig(
          1000,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(newGranularity, null, null),
          true,
          engine
      );

      // Since dropExisting is set to true...
      // Again data is only in two days
      // The earlier segment with YEAR granularity will be completely covered, overshadowed, by the
      // new MONTH segments for data and tombstones for days with no data
      // Hence, we will only have 2013-08 to 2013-09 months with data
      // plus 12 tombstones
      final List<String> intervalsAfterYEARCompactionButBeforeMONTHCompaction =
          coordinator.getSegmentIntervals(fullDatasourceName);
      expectedIntervalAfterCompaction = new ArrayList<>();
      for (String interval : intervalsAfterYEARCompactionButBeforeMONTHCompaction) {
        for (Interval newinterval : newGranularity.getIterable(new Interval(interval, ISOChronology.getInstanceUTC()))) {
          expectedIntervalAfterCompaction.add(newinterval.toString());
        }
      }
      forceTriggerAutoCompaction(12);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifyTombstones(10);
      verifySegmentsCompacted(12, 1000);
      checkCompactionIntervals(expectedIntervalAfterCompaction);

      // Now compact again over tombstones but with dropExisting set to false:
      LOG.info("Auto compaction test with SEMESTER segment granularity, dropExisting is false, over tombstones");
      newGranularity = new PeriodGranularity(new Period("P6M"), null, DateTimeZone.UTC);
      // Set dropExisting to false
      submitCompactionConfig(
          1000,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(newGranularity, null, null),
          false,
          engine
      );

      // Since dropExisting is set to false the first semester will be forced to dropExisting true
      // Hence, we will have two, one tombstone for the first semester and one data segment for the second.
      forceTriggerAutoCompaction(2); // two semesters compacted
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifyTombstones(1);
      verifySegmentsCompacted(2, 1000);

      expectedIntervalAfterCompaction =
          Arrays.asList(
              "2013-01-01T00:00:00.000Z/2013-07-01T00:00:00.000Z",
              "2013-07-01T00:00:00.000Z/2014-01-01T00:00:00.000Z"
          );
      checkCompactionIntervals(expectedIntervalAfterCompaction);

      // verify that autocompaction completed  before
      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      forceTriggerAutoCompaction(2);
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertEquals(compactTasksAfter.size(), compactTasksBefore.size());
    }
  }

  @Test
  public void testAutoCompactionDutyWithSegmentGranularityAndWithDropExistingFalse() throws Exception
  {
    loadData(INDEX_TASK);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);

      Granularity newGranularity = Granularities.YEAR;
      // Set dropExisting to false
      submitCompactionConfig(
          1000,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(newGranularity, null, null),
          false,
          CompactionEngine.NATIVE
      );

      LOG.info("Auto compaction test with YEAR segment granularity");

      List<String> expectedIntervalAfterCompaction = new ArrayList<>();
      for (String interval : intervalsBeforeCompaction) {
        for (Interval newinterval : newGranularity.getIterable(new Interval(interval, ISOChronology.getInstanceUTC()))) {
          expectedIntervalAfterCompaction.add(newinterval.toString());
        }
      }
      forceTriggerAutoCompaction(1);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(1, 1000);
      checkCompactionIntervals(expectedIntervalAfterCompaction);

      newGranularity = Granularities.DAY;
      // Set dropExisting to false
      submitCompactionConfig(
          1000,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(newGranularity, null, null),
          false,
          CompactionEngine.NATIVE
      );

      LOG.info("Auto compaction test with DAY segment granularity");

      // Since dropExisting is set to false...
      // The earlier segment with YEAR granularity is still 'used' as it’s not fully overshaowed.
      // This is because we only have newer version on 2013-08-31 to 2013-09-01 and 2013-09-01 to 2013-09-02.
      // The version for the YEAR segment is still the latest for 2013-01-01 to 2013-08-31 and 2013-09-02 to 2014-01-01.
      // Hence, all three segments are available and the expected intervals are combined from the DAY and YEAR segment granularities
      // (which are 2013-08-31 to 2013-09-01, 2013-09-01 to 2013-09-02 and 2013-01-01 to 2014-01-01)
      for (String interval : intervalsBeforeCompaction) {
        for (Interval newinterval : newGranularity.getIterable(new Interval(interval, ISOChronology.getInstanceUTC()))) {
          expectedIntervalAfterCompaction.add(newinterval.toString());
        }
      }
      forceTriggerAutoCompaction(3);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(3, 1000);
      checkCompactionIntervals(expectedIntervalAfterCompaction);
    }
  }

  @Test(dataProvider = "engine")
  public void testAutoCompactionDutyWithSegmentGranularityAndMixedVersion(CompactionEngine engine) throws Exception
  {
    loadData(INDEX_TASK);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);

      submitCompactionConfig(MAX_ROWS_PER_SEGMENT_COMPACTED, Period.days(1), engine);
      //...compacted into 1 new segment for 1 day. 1 day compacted and 1 day skipped/remains uncompacted. (3 total)
      forceTriggerAutoCompaction(3);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(1, MAX_ROWS_PER_SEGMENT_COMPACTED);

      Granularity newGranularity = Granularities.YEAR;
      submitCompactionConfig(1000, NO_SKIP_OFFSET, new UserCompactionTaskGranularityConfig(newGranularity, null, null), engine);

      LOG.info("Auto compaction test with YEAR segment granularity");

      List<String> expectedIntervalAfterCompaction = new ArrayList<>();
      for (String interval : intervalsBeforeCompaction) {
        for (Interval newinterval : newGranularity.getIterable(new Interval(interval, ISOChronology.getInstanceUTC()))) {
          expectedIntervalAfterCompaction.add(newinterval.toString());
        }
      }
      // Since the new segmentGranularity is YEAR, it will have mixed versions inside the same time chunk
      // There will be an old version (for the first day interval) from the initial ingestion and
      // a newer version (for the second day interval) from the first compaction
      forceTriggerAutoCompaction(1);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(1, 1000);
      checkCompactionIntervals(expectedIntervalAfterCompaction);
    }
  }

  @Test(dataProvider = "engine")
  public void testAutoCompactionDutyWithSegmentGranularityAndExistingCompactedSegmentsHaveSameSegmentGranularity(CompactionEngine engine) throws Exception
  {
    loadData(INDEX_TASK);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);

      // Compacted without SegmentGranularity in auto compaction config
      submitCompactionConfig(MAX_ROWS_PER_SEGMENT_COMPACTED, NO_SKIP_OFFSET, engine);
      forceTriggerAutoCompaction(2);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(2, MAX_ROWS_PER_SEGMENT_COMPACTED);

      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);

      // Segments were compacted and already has DAY granularity since it was initially ingested with DAY granularity.
      // Now set auto compaction with DAY granularity in the granularitySpec
      Granularity newGranularity = Granularities.DAY;
      submitCompactionConfig(MAX_ROWS_PER_SEGMENT_COMPACTED, NO_SKIP_OFFSET, new UserCompactionTaskGranularityConfig(newGranularity, null, null), engine);
      forceTriggerAutoCompaction(2);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(2, MAX_ROWS_PER_SEGMENT_COMPACTED);
      // should be no new compaction task as segmentGranularity is already DAY
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertEquals(compactTasksAfter.size(), compactTasksBefore.size());
    }
  }

  @Test(dataProvider = "engine")
  public void testAutoCompactionDutyWithSegmentGranularityAndExistingCompactedSegmentsHaveDifferentSegmentGranularity(CompactionEngine engine) throws Exception
  {
    loadData(INDEX_TASK);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);

      // Compacted without SegmentGranularity in auto compaction config
      submitCompactionConfig(MAX_ROWS_PER_SEGMENT_COMPACTED, NO_SKIP_OFFSET, engine);
      forceTriggerAutoCompaction(2);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(2, MAX_ROWS_PER_SEGMENT_COMPACTED);

      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);

      // Segments were compacted and already has DAY granularity since it was initially ingested with DAY granularity.
      // Now set auto compaction with DAY granularity in the granularitySpec
      Granularity newGranularity = Granularities.YEAR;
      submitCompactionConfig(MAX_ROWS_PER_SEGMENT_COMPACTED, NO_SKIP_OFFSET, new UserCompactionTaskGranularityConfig(newGranularity, null, null), engine);
      forceTriggerAutoCompaction(1);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(1, MAX_ROWS_PER_SEGMENT_COMPACTED);

      // There should be new compaction tasks since SegmentGranularity changed from DAY to YEAR
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertTrue(compactTasksAfter.size() > compactTasksBefore.size());
    }
  }

  @Test(dataProvider = "engine")
  public void testAutoCompactionDutyWithSegmentGranularityAndSmallerSegmentGranularityCoveringMultipleSegmentsInTimelineAndDropExistingTrue(CompactionEngine engine) throws Exception
  {
    loadData(INDEX_TASK);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);

      Granularity newGranularity = Granularities.YEAR;
      // Set dropExisting to true
      submitCompactionConfig(MAX_ROWS_PER_SEGMENT_COMPACTED, NO_SKIP_OFFSET, new UserCompactionTaskGranularityConfig(newGranularity, null, null), true, engine);

      List<String> expectedIntervalAfterCompaction = new ArrayList<>();
      // We will still have one visible segment with interval of 2013-01-01/2014-01-01 (compacted with YEAR)
      // and four overshadowed segments
      for (String interval : intervalsBeforeCompaction) {
        for (Interval newinterval : newGranularity.getIterable(new Interval(interval, ISOChronology.getInstanceUTC()))) {
          expectedIntervalAfterCompaction.add(newinterval.toString());
        }
      }
      forceTriggerAutoCompaction(1);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(1, MAX_ROWS_PER_SEGMENT_COMPACTED);
      checkCompactionIntervals(expectedIntervalAfterCompaction);

      loadData(INDEX_TASK);
      verifySegmentsCount(5);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      // 5 segments. 1 compacted YEAR segment and 4 newly ingested DAY segments across 2 days
      // We wil have one segment with interval of 2013-01-01/2014-01-01 (compacted with YEAR) from the compaction earlier
      // two segments with interval of 2013-08-31/2013-09-01 (newly ingested with DAY)
      // and two segments with interval of 2013-09-01/2013-09-02 (newly ingested with DAY)
      expectedIntervalAfterCompaction.addAll(intervalsBeforeCompaction);
      checkCompactionIntervals(expectedIntervalAfterCompaction);

      newGranularity = Granularities.MONTH;
      final List<String> intervalsAfterYEARButBeforeMONTHCompaction =
          coordinator.getSegmentIntervals(fullDatasourceName);
      // Since dropExisting is set to true...
      // This will submit a single compaction task for interval of 2013-01-01/2014-01-01 with MONTH granularity
      submitCompactionConfig(MAX_ROWS_PER_SEGMENT_COMPACTED, NO_SKIP_OFFSET, new UserCompactionTaskGranularityConfig(newGranularity, null, null), true, engine);
      // verify:
      expectedIntervalAfterCompaction = new ArrayList<>();
      // The previous segment with interval of 2013-01-01/2014-01-01 (compacted with YEAR) will be
      // completely overshadowed by a combination of tombstones and segments with data.
      // We will only have one segment with interval of 2013-08-01/2013-09-01 (compacted with MONTH)
      // and one segment with interval of 2013-09-01/2013-10-01 (compacted with MONTH)
      // plus ten tombstones for the remaining months, thus expecting 12 intervals...
      for (String interval : intervalsAfterYEARButBeforeMONTHCompaction) {
        for (Interval newinterval : Granularities.MONTH.getIterable(new Interval(interval, ISOChronology.getInstanceUTC()))) {
          expectedIntervalAfterCompaction.add(newinterval.toString());
        }
      }
      forceTriggerAutoCompaction(12);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifyTombstones(10);
      verifySegmentsCompacted(12, MAX_ROWS_PER_SEGMENT_COMPACTED);
      checkCompactionIntervals(expectedIntervalAfterCompaction);
    }
  }

  @Test
  public void testAutoCompactionDutyWithSegmentGranularityAndSmallerSegmentGranularityCoveringMultipleSegmentsInTimelineAndDropExistingFalse() throws Exception
  {
    loadData(INDEX_TASK);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);
      verifyQuery(INDEX_QUERIES_RESOURCE);

      Granularity newGranularity = Granularities.YEAR;
      // Set dropExisting to false
      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(newGranularity, null, null),
          false,
          CompactionEngine.NATIVE
      );

      List<String> expectedIntervalAfterCompaction = new ArrayList<>();
      // We wil have one segment with interval of 2013-01-01/2014-01-01 (compacted with YEAR)
      for (String interval : intervalsBeforeCompaction) {
        for (Interval newinterval : newGranularity.getIterable(new Interval(interval, ISOChronology.getInstanceUTC()))) {
          expectedIntervalAfterCompaction.add(newinterval.toString());
        }
      }
      forceTriggerAutoCompaction(1);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(1, MAX_ROWS_PER_SEGMENT_COMPACTED);
      checkCompactionIntervals(expectedIntervalAfterCompaction);

      loadData(INDEX_TASK);
      verifySegmentsCount(5);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      // 5 segments. 1 compacted YEAR segment and 4 newly ingested DAY segments across 2 days
      // We wil have one segment with interval of 2013-01-01/2014-01-01 (compacted with YEAR) from the compaction earlier
      // two segments with interval of 2013-08-31/2013-09-01 (newly ingested with DAY)
      // and two segments with interval of 2013-09-01/2013-09-02 (newly ingested with DAY)
      expectedIntervalAfterCompaction.addAll(intervalsBeforeCompaction);
      checkCompactionIntervals(expectedIntervalAfterCompaction);

      newGranularity = Granularities.MONTH;
      // Set dropExisting to false
      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(newGranularity, null, null),
          false,
          CompactionEngine.NATIVE
      );
      // Since dropExisting is set to true...
      // This will submit a single compaction task for interval of 2013-01-01/2014-01-01 with MONTH granularity
      expectedIntervalAfterCompaction = new ArrayList<>();
      // Since dropExisting is set to false...
      // We wil have one segment with interval of 2013-01-01/2014-01-01 (compacted with YEAR) from before the compaction
      for (String interval : intervalsBeforeCompaction) {
        for (Interval newinterval : Granularities.YEAR.getIterable(new Interval(interval, ISOChronology.getInstanceUTC()))) {
          expectedIntervalAfterCompaction.add(newinterval.toString());
        }
      }
      // one segments with interval of 2013-09-01/2013-10-01 (compacted with MONTH)
      // and one segments with interval of 2013-10-01/2013-11-01 (compacted with MONTH)
      for (String interval : intervalsBeforeCompaction) {
        for (Interval newinterval : Granularities.MONTH.getIterable(new Interval(interval, ISOChronology.getInstanceUTC()))) {
          expectedIntervalAfterCompaction.add(newinterval.toString());
        }
      }

      forceTriggerAutoCompaction(3);
      verifyQuery(INDEX_QUERIES_RESOURCE);
      verifySegmentsCompacted(3, MAX_ROWS_PER_SEGMENT_COMPACTED);
      checkCompactionIntervals(expectedIntervalAfterCompaction);
    }
  }

  @Test
  public void testAutoCompactionDutyWithSegmentGranularityFinerAndNotAlignWithSegment() throws Exception
  {
    updateCompactionTaskSlot(1, 1);
    final ISOChronology chrono = ISOChronology.getInstance(DateTimes.inferTzFromString("America/Los_Angeles"));
    Map<String, Object> specs = ImmutableMap.of("%%GRANULARITYSPEC%%", new UniformGranularitySpec(Granularities.MONTH, Granularities.DAY, false, ImmutableList.of(new Interval("2013-08-31/2013-09-02", chrono))));
    loadData(INDEX_TASK_WITH_GRANULARITY_SPEC, specs);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      Map<String, Object> queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(57.0), ImmutableList.of(459.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(Granularities.WEEK, null, null),
          false,
          CompactionEngine.NATIVE
      );
      // Before compaction, we have segments with the interval 2013-08-01/2013-09-01 and 2013-09-01/2013-10-01
      // We will compact the latest segment, 2013-09-01/2013-10-01, to WEEK.
      // Since the start of the week does not align with 2013-09-01 or 2013-10-01, we expect the compaction task's
      // interval to be adjusted so that the compacted WEEK segments does not unintentionally remove data of the
      // non compacted 2013-08-01/2013-09-01 segment.
      // Note that the compacted WEEK segment does not fully cover the original MONTH segment as the MONTH segment
      // does not have data on every week on the month
      forceTriggerAutoCompaction(3);
      // Make sure that no data is lost after compaction
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(57.0), ImmutableList.of(459.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      verifySegmentsCompacted(1, MAX_ROWS_PER_SEGMENT_COMPACTED);
      List<TaskResponseObject> tasks = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      TaskResponseObject compactTask = null;
      for (TaskResponseObject task : tasks) {
        if (task.getType().equals("compact")) {
          compactTask = task;
        }
      }
      Assert.assertNotNull(compactTask);
      TaskPayloadResponse task = indexer.getTaskPayload(compactTask.getId());
      // Verify that compaction task interval is adjusted to align with segmentGranularity
      Assert.assertEquals(Intervals.of("2013-08-26T00:00:00.000Z/2013-10-07T00:00:00.000Z"), ((CompactionIntervalSpec) ((CompactionTask) task.getPayload()).getIoConfig().getInputSpec()).getInterval());
    }
  }

  @Test(dataProvider = "engine")
  public void testAutoCompactionDutyWithSegmentGranularityCoarserAndNotAlignWithSegment(CompactionEngine engine) throws Exception
  {
    updateCompactionTaskSlot(1, 1);
    final ISOChronology chrono = ISOChronology.getInstance(DateTimes.inferTzFromString("America/Los_Angeles"));
    Map<String, Object> specs = ImmutableMap.of("%%GRANULARITYSPEC%%", new UniformGranularitySpec(Granularities.WEEK, Granularities.DAY, false, ImmutableList.of(new Interval("2013-08-31/2013-09-02", chrono))));
    loadData(INDEX_TASK_WITH_GRANULARITY_SPEC, specs);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      Map<String, Object> queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(57.0), ImmutableList.of(459.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(Granularities.MONTH, null, null),
          false,
          engine
      );
      // Before compaction, we have segments with the interval 2013-08-26T00:00:00.000Z/2013-09-02T00:00:00.000Z
      // We will compact the latest segment to MONTH.
      // Although the segments before compaction only cover 2013-08-26 to 2013-09-02,
      // we expect the compaction task's interval to align with the MONTH segmentGranularity (2013-08-01 to 2013-10-01)
      forceTriggerAutoCompaction(2);
      // Make sure that no data is lost after compaction
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(57.0), ImmutableList.of(459.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      verifySegmentsCompacted(2, MAX_ROWS_PER_SEGMENT_COMPACTED);
      List<TaskResponseObject> tasks = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      TaskResponseObject compactTask = null;
      for (TaskResponseObject task : tasks) {
        if (task.getType().equals("compact")) {
          compactTask = task;
        }
      }
      Assert.assertNotNull(compactTask);
      TaskPayloadResponse task = indexer.getTaskPayload(compactTask.getId());
      // Verify that compaction task interval is adjusted to align with segmentGranularity
      Assert.assertEquals(Intervals.of("2013-08-01T00:00:00.000Z/2013-10-01T00:00:00.000Z"), ((CompactionIntervalSpec) ((CompactionTask) task.getPayload()).getIoConfig().getInputSpec()).getInterval());
    }
  }

  @Test()
  public void testAutoCompactionDutyWithRollup() throws Exception
  {
    final ISOChronology chrono = ISOChronology.getInstance(DateTimes.inferTzFromString("America/Los_Angeles"));
    Map<String, Object> specs = ImmutableMap.of("%%GRANULARITYSPEC%%", new UniformGranularitySpec(Granularities.DAY, Granularities.DAY, false, ImmutableList.of(new Interval("2013-08-31/2013-09-02", chrono))));
    loadData(INDEX_TASK_WITH_GRANULARITY_SPEC, specs);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      Map<String, Object> queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(57.0), ImmutableList.of(459.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(null, null, true),
          false,
          CompactionEngine.NATIVE
      );
      forceTriggerAutoCompaction(2);
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(516.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      verifySegmentsCompacted(2, MAX_ROWS_PER_SEGMENT_COMPACTED);

      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      // Verify rollup segments does not get compacted again
      forceTriggerAutoCompaction(2);
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertEquals(compactTasksAfter.size(), compactTasksBefore.size());
    }
  }

  @Test(dataProvider = "engine")
  public void testAutoCompactionDutyWithQueryGranularity(CompactionEngine engine) throws Exception
  {
    final ISOChronology chrono = ISOChronology.getInstance(DateTimes.inferTzFromString("America/Los_Angeles"));
    Map<String, Object> specs = ImmutableMap.of("%%GRANULARITYSPEC%%", new UniformGranularitySpec(Granularities.DAY, Granularities.NONE, true, ImmutableList.of(new Interval("2013-08-31/2013-09-02", chrono))));
    loadData(INDEX_TASK_WITH_GRANULARITY_SPEC, specs);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      Map<String, Object> queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(57.0), ImmutableList.of(459.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          new UserCompactionTaskGranularityConfig(null, Granularities.DAY, null),
          false,
          engine
      );
      forceTriggerAutoCompaction(2);
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(516.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      verifySegmentsCompacted(2, MAX_ROWS_PER_SEGMENT_COMPACTED);

      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      // Verify rollup segments does not get compacted again
      forceTriggerAutoCompaction(2);
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertEquals(compactTasksAfter.size(), compactTasksBefore.size());
    }
  }

  @Test(dataProvider = "engine")
  public void testAutoCompactionDutyWithDimensionsSpec(CompactionEngine engine) throws Exception
  {
    // Index data with dimensions "page", "language", "user", "unpatrolled", "newPage", "robot", "anonymous",
    // "namespace", "continent", "country", "region", "city"
    loadData(INDEX_TASK_WITH_DIMENSION_SPEC);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(null);
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);

      // Result is not rollup
      Map<String, Object> queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(57.0), ImmutableList.of(459.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);

      // Compact and change dimension to only "language"
      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          null,
          new UserCompactionTaskDimensionsConfig(DimensionsSpec.getDefaultSchemas(ImmutableList.of("language"))),
          null,
          null,
          false,
          engine
      );
      forceTriggerAutoCompaction(2);

      // Result should rollup on language dimension
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(516.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      verifySegmentsCompacted(2, MAX_ROWS_PER_SEGMENT_COMPACTED);

      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      // Verify compacted segments does not get compacted again
      forceTriggerAutoCompaction(2);
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertEquals(compactTasksAfter.size(), compactTasksBefore.size());
    }
  }

  @Test(dataProvider = "useSupervisors")
  public void testAutoCompactionDutyWithFilter(boolean useSupervisors) throws Exception
  {
    updateClusterConfig(new ClusterCompactionConfig(0.5, 10, null, useSupervisors, null));

    loadData(INDEX_TASK_WITH_DIMENSION_SPEC);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(Ordering.natural().reversed());
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);

      // Result is not rollup
      // For dim "page", result has values "Gypsy Danger" and "Striker Eureka"
      Map<String, Object> queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(57.0), ImmutableList.of(459.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);

      // Compact and filter with selector on dim "page" and value "Striker Eureka"
      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          null,
          null,
          new CompactionTransformSpec(new SelectorDimFilter("page", "Striker Eureka", null)),
          null,
          false,
          CompactionEngine.NATIVE
      );
      forceTriggerAutoCompaction(intervalsBeforeCompaction, useSupervisors, 2);

      // For dim "page", result should only contain value "Striker Eureka"
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 1,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(459.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);
      verifySegmentsCompacted(2, MAX_ROWS_PER_SEGMENT_COMPACTED);

      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      // Verify compacted segments does not get compacted again
      forceTriggerAutoCompaction(intervalsBeforeCompaction, useSupervisors, 2);
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertEquals(compactTasksAfter.size(), compactTasksBefore.size());
    }
  }

  @Test(dataProvider = "useSupervisors")
  public void testAutoCompationDutyWithMetricsSpec(boolean useSupervisors) throws Exception
  {
    updateClusterConfig(new ClusterCompactionConfig(0.5, 10, null, useSupervisors, null));

    loadData(INDEX_TASK_WITH_DIMENSION_SPEC);
    try (final Closeable ignored = unloader(fullDatasourceName)) {
      final List<String> intervalsBeforeCompaction = coordinator.getSegmentIntervals(fullDatasourceName);
      intervalsBeforeCompaction.sort(Ordering.natural().reversed());
      // 4 segments across 2 days (4 total)...
      verifySegmentsCount(4);

      // For dim "page", result has values "Gypsy Danger" and "Striker Eureka"
      Map<String, Object> queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(57.0), ImmutableList.of(459.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);

      // Compact and add longSum and doubleSum metrics
      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          null,
          null,
          null,
          new AggregatorFactory[] {new DoubleSumAggregatorFactory("double_sum_added", "added"), new LongSumAggregatorFactory("long_sum_added", "added")},
          false,
          CompactionEngine.NATIVE
      );
      forceTriggerAutoCompaction(intervalsBeforeCompaction, useSupervisors, 2);

      // Result should be the same with the addition of new metrics, "double_sum_added" and "long_sum_added".
      // These new metrics should have the same value as the input field "added"
      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "double_sum_added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(57.0), ImmutableList.of(459.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);

      queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "long_sum_added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(57), ImmutableList.of(459))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);

      verifySegmentsCompacted(2, MAX_ROWS_PER_SEGMENT_COMPACTED);

      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      // Verify compacted segments does not get compacted again
      forceTriggerAutoCompaction(intervalsBeforeCompaction, useSupervisors, 2);
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertEquals(compactTasksAfter.size(), compactTasksBefore.size());
    }
  }

  @Test
  public void testAutoCompactionDutyWithOverlappingInterval() throws Exception
  {
    final ISOChronology chrono = ISOChronology.getInstance(DateTimes.inferTzFromString("America/Los_Angeles"));
    Map<String, Object> specs = ImmutableMap.of("%%GRANULARITYSPEC%%", new UniformGranularitySpec(Granularities.WEEK, Granularities.NONE, false, ImmutableList.of(new Interval("2013-08-31/2013-09-02", chrono))));
    // Create WEEK segment with 2013-08-26 to 2013-09-02
    loadData(INDEX_TASK_WITH_GRANULARITY_SPEC, specs);
    specs = ImmutableMap.of("%%GRANULARITYSPEC%%", new UniformGranularitySpec(Granularities.MONTH, Granularities.NONE, false, ImmutableList.of(new Interval("2013-09-01/2013-09-02", chrono))));
    // Create MONTH segment with 2013-09-01 to 2013-10-01
    loadData(INDEX_TASK_WITH_GRANULARITY_SPEC, specs);

    try (final Closeable ignored = unloader(fullDatasourceName)) {
      verifySegmentsCount(2);

      // Result is not rollup
      // For dim "page", result has values "Gypsy Danger" and "Striker Eureka"
      Map<String, Object> queryAndResultFields = ImmutableMap.of(
          "%%FIELD_TO_QUERY%%", "added",
          "%%EXPECTED_COUNT_RESULT%%", 2,
          "%%EXPECTED_SCAN_RESULT%%", ImmutableList.of(ImmutableMap.of("events", ImmutableList.of(ImmutableList.of(57.0), ImmutableList.of(459.0))))
      );
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);

      submitCompactionConfig(
          MAX_ROWS_PER_SEGMENT_COMPACTED,
          NO_SKIP_OFFSET,
          null,
          null,
          null,
          null,
          false,
          CompactionEngine.NATIVE
      );
      // Compact the MONTH segment
      forceTriggerAutoCompaction(2);
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);

      // Compact the WEEK segment
      forceTriggerAutoCompaction(2);
      verifyQuery(INDEX_ROLLUP_QUERIES_RESOURCE, queryAndResultFields);

      // Verify all task succeed
      List<TaskResponseObject> compactTasksBefore = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      for (TaskResponseObject taskResponseObject : compactTasksBefore) {
        Assert.assertEquals(TaskState.SUCCESS, taskResponseObject.getStatus());
      }

      // Verify compacted segments does not get compacted again
      forceTriggerAutoCompaction(2);
      List<TaskResponseObject> compactTasksAfter = indexer.getCompleteTasksForDataSource(fullDatasourceName);
      Assert.assertEquals(compactTasksAfter.size(), compactTasksBefore.size());
    }
  }

  private void loadData(String indexTask) throws Exception
  {
    loadData(indexTask, ImmutableMap.of());
  }

  private void loadData(String indexTask, Map<String, Object> specs) throws Exception
  {
    String taskSpec = getResourceAsString(indexTask);
    taskSpec = StringUtils.replace(taskSpec, "%%DATASOURCE%%", fullDatasourceName);
    taskSpec = StringUtils.replace(
        taskSpec,
        "%%SEGMENT_AVAIL_TIMEOUT_MILLIS%%",
        jsonMapper.writeValueAsString("0")
    );
    for (Map.Entry<String, Object> entry : specs.entrySet()) {
      taskSpec = StringUtils.replace(
          taskSpec,
          entry.getKey(),
          jsonMapper.writeValueAsString(entry.getValue())
      );
    }
    final String taskId = indexer.submitTask(taskSpec);
    LOG.info("Submitted task[%s] to load data", taskId);
    indexer.waitUntilTaskCompletes(taskId);

    ITRetryUtil.retryUntilTrue(
        () -> coordinator.areSegmentsLoaded(fullDatasourceName),
        "Segments are loaded"
    );
  }

  private void verifyQuery(String queryResource) throws Exception
  {
    verifyQuery(queryResource, ImmutableMap.of());
  }

  private void verifyQuery(String queryResource, Map<String, Object> keyValueToReplace) throws Exception
  {
    String queryResponseTemplate;
    try {
      InputStream is = AbstractITBatchIndexTest.class.getResourceAsStream(queryResource);
      queryResponseTemplate = IOUtils.toString(is, StandardCharsets.UTF_8);
    }
    catch (IOException e) {
      throw new ISE(e, "could not read query file: %s", queryResource);
    }
    queryResponseTemplate = StringUtils.replace(
        queryResponseTemplate,
        "%%DATASOURCE%%",
        fullDatasourceName
    );
    for (Map.Entry<String, Object> entry : keyValueToReplace.entrySet()) {
      queryResponseTemplate = StringUtils.replace(
          queryResponseTemplate,
          entry.getKey(),
          jsonMapper.writeValueAsString(entry.getValue())
      );
    }
    queryHelper.testQueriesFromString(queryResponseTemplate);
  }

  private void updateClusterConfig(ClusterCompactionConfig clusterConfig) throws Exception
  {
    compactionResource.updateClusterConfig(clusterConfig);
    LOG.info("Updated cluster config to [%s]", clusterConfig);
  }

  private void submitCompactionConfig(
      Integer maxRowsPerSegment,
      Period skipOffsetFromLatest,
      CompactionEngine engine
  ) throws Exception
  {
    submitCompactionConfig(maxRowsPerSegment, skipOffsetFromLatest, null, engine);
  }

  private void submitCompactionConfig(
      Integer maxRowsPerSegment,
      Period skipOffsetFromLatest,
      UserCompactionTaskGranularityConfig granularitySpec,
      CompactionEngine engine
  ) throws Exception
  {
    submitCompactionConfig(maxRowsPerSegment, skipOffsetFromLatest, granularitySpec, false, engine);
  }

  private void submitCompactionConfig(
      Integer maxRowsPerSegment,
      Period skipOffsetFromLatest,
      UserCompactionTaskGranularityConfig granularitySpec,
      boolean dropExisting,
      CompactionEngine engine
  ) throws Exception
  {
    submitCompactionConfig(
        maxRowsPerSegment,
        skipOffsetFromLatest,
        granularitySpec,
        null,
        null,
        null,
        dropExisting,
        engine
    );
  }

  private void submitCompactionConfig(
      Integer maxRowsPerSegment,
      Period skipOffsetFromLatest,
      UserCompactionTaskGranularityConfig granularitySpec,
      UserCompactionTaskDimensionsConfig dimensionsSpec,
      CompactionTransformSpec transformSpec,
      AggregatorFactory[] metricsSpec,
      boolean dropExisting,
      CompactionEngine engine
  ) throws Exception
  {
    submitCompactionConfig(
        new DynamicPartitionsSpec(maxRowsPerSegment, null),
        skipOffsetFromLatest,
        1,
        granularitySpec,
        dimensionsSpec,
        transformSpec,
        metricsSpec,
        dropExisting,
        engine
    );
  }

  private void submitCompactionConfig(
      PartitionsSpec partitionsSpec,
      Period skipOffsetFromLatest,
      int maxNumConcurrentSubTasks,
      UserCompactionTaskGranularityConfig granularitySpec,
      UserCompactionTaskDimensionsConfig dimensionsSpec,
      CompactionTransformSpec transformSpec,
      AggregatorFactory[] metricsSpec,
      boolean dropExisting,
      CompactionEngine engine
  ) throws Exception
  {
    DataSourceCompactionConfig dataSourceCompactionConfig =
        InlineSchemaDataSourceCompactionConfig.builder()
                                              .forDataSource(fullDatasourceName)
                                              .withSkipOffsetFromLatest(skipOffsetFromLatest)
                                              .withTuningConfig(
                                            new UserCompactionTaskQueryTuningConfig(
                                                null,
                                                null,
                                                null,
                                                null,
                                                new MaxSizeSplitHintSpec(null, 1),
                                                partitionsSpec,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                maxNumConcurrentSubTasks,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                1,
                                                null
                                            )
                                        )
                                              .withGranularitySpec(granularitySpec)
                                              .withDimensionsSpec(dimensionsSpec)
                                              .withMetricsSpec(metricsSpec)
                                              .withTransformSpec(transformSpec)
                                              .withIoConfig(
                                      !dropExisting ? null : new UserCompactionTaskIOConfig(true)
                                  )
                                              .withEngine(engine)
                                              .withTaskContext(ImmutableMap.of("maxNumTasks", 2))
                                              .build();
    compactionResource.submitCompactionConfig(dataSourceCompactionConfig);

    // Wait for compaction config to persist
    Thread.sleep(2000);

    // Verify that the compaction config is updated correctly.
    DataSourceCompactionConfig foundDataSourceCompactionConfig
        = compactionResource.getDataSourceCompactionConfig(fullDatasourceName);
    Assert.assertNotNull(foundDataSourceCompactionConfig);
    Assert.assertNotNull(foundDataSourceCompactionConfig.getTuningConfig());
    Assert.assertEquals(foundDataSourceCompactionConfig.getTuningConfig().getPartitionsSpec(), partitionsSpec);
    Assert.assertEquals(foundDataSourceCompactionConfig.getSkipOffsetFromLatest(), skipOffsetFromLatest);
  }

  private void deleteCompactionConfig() throws Exception
  {
    compactionResource.deleteDataSourceCompactionConfig(fullDatasourceName);

    // Verify that the compaction config is updated correctly.
    DruidCompactionConfig compactionConfig = DruidCompactionConfig
        .empty().withDatasourceConfigs(compactionResource.getAllCompactionConfigs());
    DataSourceCompactionConfig foundDataSourceCompactionConfig
        = compactionConfig.findConfigForDatasource(fullDatasourceName).orNull();
    Assert.assertNull(foundDataSourceCompactionConfig);
  }

  /**
   * Performs compaction of the given intervals of the test datasource,
   * {@link #fullDatasourceName}, and verifies the total number of segments in
   * the datasource after compaction.
   */
  private void forceTriggerAutoCompaction(
      List<String> intervals,
      boolean useSupervisors,
      int numExpectedSegmentsAfterCompaction
  ) throws Exception
  {
    if (useSupervisors) {
      // Enable compaction for the requested intervals
      final FixedIntervalOrderPolicy policy = new FixedIntervalOrderPolicy(
          intervals.stream().map(
              interval -> new FixedIntervalOrderPolicy.Candidate(fullDatasourceName, Intervals.of(interval))
          ).collect(Collectors.toList())
      );
      updateClusterConfig(
          new ClusterCompactionConfig(0.5, intervals.size(), policy, true, null)
      );

      // Wait for scheduler to pick up the compaction job
      Thread.sleep(30_000);
      waitForCompactionToFinish(numExpectedSegmentsAfterCompaction);

      // Disable all compaction
      updateClusterConfig(
          new ClusterCompactionConfig(0.5, intervals.size(), COMPACT_NOTHING_POLICY, true, null)
      );
    } else {
      forceTriggerAutoCompaction(numExpectedSegmentsAfterCompaction);
    }
  }

  private void forceTriggerAutoCompaction(int numExpectedSegmentsAfterCompaction) throws Exception
  {
    compactionResource.forceTriggerAutoCompaction();
    waitForCompactionToFinish(numExpectedSegmentsAfterCompaction);
  }

  private void waitForCompactionToFinish(int numExpectedSegmentsAfterCompaction)
  {
    waitForAllTasksToCompleteForDataSource(fullDatasourceName);
    ITRetryUtil.retryUntilTrue(
        () -> coordinator.areSegmentsLoaded(fullDatasourceName),
        "Segments are loaded"
    );
    verifySegmentsCount(numExpectedSegmentsAfterCompaction);
  }

  private void verifySegmentsCount(int numExpectedSegments)
  {
    ITRetryUtil.retryUntilEquals(
        () -> coordinator.getSegments(fullDatasourceName).size(),
        numExpectedSegments,
        "Segment count"
    );
  }

  private void checkCompactionIntervals(List<String> expectedIntervals)
  {
    final Set<String> expectedIntervalsSet = new HashSet<>(expectedIntervals);
    ITRetryUtil.retryUntilEquals(
        () -> Set.copyOf(coordinator.getSegmentIntervals(fullDatasourceName)),
        expectedIntervalsSet,
        "Segment intervals"
    );
  }

  private void verifySegmentsCompacted(int expectedCompactedSegmentCount, Integer expectedMaxRowsPerSegment)
  {
    verifySegmentsCompacted(
        new DynamicPartitionsSpec(expectedMaxRowsPerSegment, Long.MAX_VALUE),
        expectedCompactedSegmentCount
    );
  }

  private void verifyTombstones(int expectedCompactedTombstoneCount)
  {
    List<DataSegment> segments = coordinator.getFullSegmentsMetadata(fullDatasourceName);
    int actualTombstoneCount = 0;
    for (DataSegment segment : segments) {
      if (segment.isTombstone()) {
        actualTombstoneCount++;
      }
    }
    Assert.assertEquals(actualTombstoneCount, expectedCompactedTombstoneCount);
  }

  private void verifySegmentsCompacted(PartitionsSpec partitionsSpec, int expectedCompactedSegmentCount)
  {
    List<DataSegment> segments = coordinator.getFullSegmentsMetadata(fullDatasourceName);
    List<DataSegment> foundCompactedSegments = new ArrayList<>();
    for (DataSegment segment : segments) {
      if (segment.getLastCompactionState() != null) {
        foundCompactedSegments.add(segment);
      }
    }
    Assert.assertEquals(foundCompactedSegments.size(), expectedCompactedSegmentCount);
    for (DataSegment compactedSegment : foundCompactedSegments) {
      Assert.assertNotNull(compactedSegment.getLastCompactionState());
      Assert.assertNotNull(compactedSegment.getLastCompactionState().getPartitionsSpec());
      Assert.assertEquals(compactedSegment.getLastCompactionState().getPartitionsSpec(), partitionsSpec);
    }

  }

  private void verifySegmentsCompactedDimensionSchema(List<DimensionSchema> dimensionSchemas)
  {
    List<DataSegment> segments = coordinator.getFullSegmentsMetadata(fullDatasourceName);
    List<DataSegment> foundCompactedSegments = new ArrayList<>();
    for (DataSegment segment : segments) {
      if (segment.getLastCompactionState() != null) {
        foundCompactedSegments.add(segment);
      }
    }
    for (DataSegment compactedSegment : foundCompactedSegments) {
      MatcherAssert.assertThat(
          dimensionSchemas,
          Matchers.containsInAnyOrder(
              compactedSegment.getLastCompactionState()
                              .getDimensionsSpec()
                              .getDimensions()
                              .toArray(new DimensionSchema[0]))
      );
    }
  }

  private void updateCompactionTaskSlot(double compactionTaskSlotRatio, int maxCompactionTaskSlots) throws Exception
  {
    final ClusterCompactionConfig oldConfig = compactionResource.getClusterConfig();
    compactionResource.updateClusterConfig(
        new ClusterCompactionConfig(
            compactionTaskSlotRatio,
            maxCompactionTaskSlots,
            oldConfig.getCompactionPolicy(),
            oldConfig.isUseSupervisors(),
            oldConfig.getEngine()
        )
    );

    // Verify that the compaction config is updated correctly
    final ClusterCompactionConfig updatedConfig = compactionResource.getClusterConfig();
    Assert.assertEquals(updatedConfig.getCompactionTaskSlotRatio(), compactionTaskSlotRatio);
    Assert.assertEquals(updatedConfig.getMaxCompactionTaskSlots(), maxCompactionTaskSlots);
    LOG.info(
        "Updated compactionTaskSlotRatio[%s] and maxCompactionTaskSlots[%d]",
        compactionTaskSlotRatio, maxCompactionTaskSlots
    );
  }

  private void getAndAssertCompactionStatus(
      String fullDatasourceName,
      AutoCompactionSnapshot.ScheduleStatus scheduleStatus,
      Matcher<Long> bytesAwaitingCompactionMatcher,
      Matcher<Long> bytesCompactedMatcher,
      Matcher<Long> bytesSkippedMatcher,
      long segmentCountAwaitingCompaction,
      long segmentCountCompacted,
      long segmentCountSkipped,
      long intervalCountAwaitingCompaction,
      long intervalCountCompacted,
      long intervalCountSkipped
  ) throws Exception
  {
    AutoCompactionSnapshot actualStatus = compactionResource.getCompactionStatus(fullDatasourceName);
    Assert.assertNotNull(actualStatus);
    Assert.assertEquals(actualStatus.getScheduleStatus(), scheduleStatus);
    MatcherAssert.assertThat(actualStatus.getBytesAwaitingCompaction(), bytesAwaitingCompactionMatcher);
    MatcherAssert.assertThat(actualStatus.getBytesCompacted(), bytesCompactedMatcher);
    MatcherAssert.assertThat(actualStatus.getBytesSkipped(), bytesSkippedMatcher);
    Assert.assertEquals(actualStatus.getSegmentCountAwaitingCompaction(), segmentCountAwaitingCompaction);
    Assert.assertEquals(actualStatus.getSegmentCountCompacted(), segmentCountCompacted);
    Assert.assertEquals(actualStatus.getSegmentCountSkipped(), segmentCountSkipped);
    Assert.assertEquals(actualStatus.getIntervalCountAwaitingCompaction(), intervalCountAwaitingCompaction);
    Assert.assertEquals(actualStatus.getIntervalCountCompacted(), intervalCountCompacted);
    Assert.assertEquals(actualStatus.getIntervalCountSkipped(), intervalCountSkipped);
  }
}
