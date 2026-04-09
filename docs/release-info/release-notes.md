---
id: release-notes
title: "Release notes"
---

<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one
  ~ or more contributor license agreements.  See the NOTICE file
  ~ distributed with this work for additional information
  ~ regarding copyright ownership.  The ASF licenses this file
  ~ to you under the Apache License, Version 2.0 (the
  ~ "License"); you may not use this file except in compliance
  ~ with the License.  You may obtain a copy of the License at
  ~
  ~   http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing,
  ~ software distributed under the License is distributed on an
  ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~ KIND, either express or implied.  See the License for the
  ~ specific language governing permissions and limitations
  ~ under the License.
  -->

<!--Replace {{DRUIDVERSION}} with the correct Druid version.-->

Apache Druid \{\{DRUIDVERSION}} contains over $NUMBER_FEATURES new features, bug fixes, performance enhancements, documentation improvements, and additional test coverage from $NUMBER_OF_CONTRIBUTORS contributors.

<!--
Replace {{MILESTONE}} with the correct milestone number. For example: https://github.com/apache/druid/issues?q=is%3Aclosed+milestone%3A28.0+sort%3Aupdated-desc+
-->

See the [complete set of changes](https://github.com/apache/druid/issues?q=is%3Aclosed+milestone%3A{{MILESTONE}}+sort%3Aupdated-desc+) for additional details, including bug fixes.

Review the [upgrade notes](#upgrade-notes) and [incompatible changes](#incompatible-changes) before you upgrade to Druid \{\{DRUIDVERSION}}.
If you are upgrading across multiple versions, see the [Upgrade notes](upgrade-notes.md) page, which lists upgrade notes for the most recent Druid versions.

<!-- 
This file is a collaborative work in process. Adding a release note to this file doesn't guarantee its presence in the next release until the release branch is cut and the release notes are finalized.

This file contains the following sections:
- Important features, changes, and deprecations
- Functional area and related changes
- Upgrade notes and incompatible changes

Please add your release note to the appropriate section and include the following:
- Detailed title
- Summary of the changes (a couple of sentences) aimed at Druid users
- Link to the associated PR

If your release note contains images, put the images in the release-info/assets folder.

For tips about how to write a good release note, see [Release notes](https://github.com/apache/druid/blob/master/CONTRIBUTING.md#release-notes).
-->

## Important features, changes, and deprecations

This section contains important information about new and existing features.

### Hadoop-based ingestion

Support for Hadoop-based ingestion has been removed. The feature was deprecated in Druid 34.

Use one of Druid's other supported ingestion methods, such as SQL-based ingestion or MiddleManager-less ingestion using Kubernetes.

[#19109](https://github.com/apache/druid/pull/19109)

### Query blocklist

You can now create a query blocklist for dynamically blocking queries without restarting your deployment. You can block queries by datasource, query type, or query context using the new `/druid/coordinator/v1/config/broker` API. Block rules use `AND` logic, which means all criteria must match. Druid stores the rules in the metadata database.

The following example blocks all groupBy queries on the `wikipedia` datasource with a query context parameter of `priority` equal to `0`:

```
POST /druid/coordinator/v1/config/broker
  {
    "queryBlocklist": [
      {
        "ruleName": "block-wikipedia-groupbys",
        "dataSources": ["wikipedia"],
        "queryTypes": ["groupBy"],
        "contextMatches": {"priority": "0"}
      }
    ]
  }
```

[#19011](https://github.com/apache/druid/pull/19011)

### Minor compaction for Overlord-based compaction (experimental)

You can now configure minor compaction. With minor compaction, Druid only compacts newly ingested segments while upgrading existing compacted segments. When Druid upgrades segments, it updates the metadata instead of using resources to compact it again. Support is currently limited to Overlord-based compaction supervisors using the MSQ task engine.

Use the `mostFragmentedFirst` compaction policy and set either a percentage of rows-based or byte-based threshold for minor compaction.

[#19059](https://github.com/apache/druid/pull/19059) [#19205](https://github.com/apache/druid/pull/19205)

### Thrift input format

As part of the Thrift contributor extension, Druid now supports Thrift-encoded data for Kafka and Kinesis streaming ingestion.

[#19111](https://github.com/apache/druid/pull/19111)

To use this feature, you must add `druid-thrift-extensions` to your extension load list.

### Kubernetes-based task management

This extension is now generally available.

[#19128](https://github.com/apache/druid/pull/19128)

### Tombstones

Tombstones for JSON-based native batch ingestion (the `dropExisting` flag for `ioConfig`) are now generally available.

[#19128](https://github.com/apache/druid/pull/19128)

### Dynamic default query context

You can now add default query context parameters as a dynamic configuration to the Broker. This allows you to override static defaults set in your runtime properties without restarting your deployment or having to update multiple queries individually. Druid resolves which query context parameters to use based on the following priority:

1. The query context included with the query
1. The query context set as a dynamic configuration on the Broker
1. The query context parameters set in the runtime properties
1. The defaults that ship with Druid

Note that like other Broker dynamic configuration, this is best-effort. Settings may not be applied in certain
cases, such as when a Broker has recently started and hasn't received the config yet, or if the
Broker can't contact the Coordinator. If a query context parameter is critical for all your queries, set it in the runtime properties.

[#19146](https://github.com/apache/druid/pull/19146)

### `sys.queries` table (experimental)

The new system queries table provides information about currently running and recently completed queries that use the Dart engine. This table is off by default. To enable the table, set the following config:

```
druid.sql.planner.enableSysQueriesTable = true
```

As part of this change, the `/druid/v2/sql/queries` API now supports an `includeComplete` parameter that shows recently completed queries.

[#18923](https://github.com/apache/druid/pull/18923)

### Auto-compaction with compaction supervisors

Auto-compaction using compaction supervisors has been improved and is now generally available.

As part of the improvement, instead of individual compacted segments having to store their full compaction state in the metadata store, states are now stored in a central location, a new `indexingStates` table, in the metadata store. Individual segments only need to store a unique reference (`indexing_state_fingerprint`) to their full compaction state. 

Since many segments in a single datasource share the same underlying compaction state, this greatly reduces metadata storage requirements for automatic compaction.

For backwards compatibility, detailed compaction state will continue to be persisted in each segment for now. This will be removed in a future release.

You can stop storing detailed compaction state by setting `storeCompactionStatePerSegment` to `false` in the cluster compaction config. If you turn it off and need to downgrade, Druid needs to re-compact any segments that have been compacted since you changed the config.

This change has upgrade impacts for metadata storage and metadata caching. For more information, see the [Metadata storage for auto-compaction with compaction supervisors](#metadata-storage-for-auto-compaction-with-compaction-supervisors) upgrade note.

[#19113](https://github.com/apache/druid/pull/19113) [#18844](https://github.com/apache/druid/pull/18844)

### Broker tier selection for realtime servers

Added `druid.broker.realtime.select.tier` and `druid.broker.realtime.balancer.type` on the Brokers to optionally override the Broker’s tier selection and balancer strategies for realtime servers. If these properties are not set (the default), realtime servers continue to use the existing `druid.broker.select` and `druid.broker.balancer` configurations that apply to both historical and realtime servers.

[#19062](https://github.com/apache/druid/pull/19062)

### Manual Broker routing in the web console

You can now configure which Broker the Router uses for queries issued from the web console. You may want to do this if there are Brokers that don't have visibility into certain data tiers, and you know you're querying data available only on a certain tier.

To specify a Broker, add the following config to `web-console/console-config.js`:

```js
consoleBrokerService: 'druid/BROKER_NAME'
```

[#19069](https://github.com/apache/druid/pull/19069)

## Functional area and related changes

This section contains detailed release notes separated by areas.

### Web console

#### Changed storage column displays

- Renamed **Current size** to **Assigned size**.
- Renamed **Max size** to **Effective size**. It now displays the smaller value between `max_size` and `storage_size`. The max size is still shown as a tooltip.
- Changed usage calculation to use `effective_size`

[#19007](https://github.com/apache/druid/pull/19007)

#### Other web console improvements

- Added `workerDesc` to `WorkerStats`, which makes it easier to identify where a worker is running [#19171](https://github.com/apache/druid/pull/19171)
- Added the Dart unique execution ID (`dartQueryId`) and the `sqlQueryId` to the **Details** pane in the web console [#19185](https://github.com/apache/druid/pull/19185)
- Added support for showing completed Dart queries in the web console [#18940](https://github.com/apache/druid/pull/18940)
- Added a detail dialog to the **Services** page [#18960](https://github.com/apache/druid/pull/18960)
- Added icons to indicate when data is loaded into virtual storage, including a tooltip that shows all the counters for the data [#19010](https://github.com/apache/druid/pull/19010)
- Added support for Dart reports [#18897](https://github.com/apache/druid/pull/18897)
- Changed what's considered an active worker: any nonzero rows, files, bytes, frames, or wall time is enough to consider a worker active [#19183](https://github.com/apache/druid/pull/19183)
- Changed the **Cancel query** option to show only if a query is in an accepted or running state [#19182](https://github.com/apache/druid/pull/19182)

### Ingestion

#### SQL-based ingestion

##### Other SQL-based ingestion improvements

- Added support for virtual storage fabric when performing SQL-based ingestion [#18873](https://github.com/apache/druid/pull/18873)
- Added support for `StorageMonitor` so that MSQ task engine tasks always emit `taskId` and `groupId` [#19048](https://github.com/apache/druid/pull/19048)
- Improved worker cancellation [#18931](https://github.com/apache/druid/pull/18931)

#### Streaming ingestion

##### Changed how tasks get launched for autoscaling

The behavior of `taskCountMin` and `taskCountStart` has been changed for autoscaling. Druid now computes the initial number of tasks to launch by checking the configs in the following order: `taskCountStart` (optional), then `taskCount` (in `ioConfig`), then `taskCountMin`.

[#19091](https://github.com/apache/druid/pull/19091)

##### Improved query isolation

Added `serverPriorityToReplicas` parameter to the streaming supervisor specs (Kafka, Kinesis, and Rabbit). This allows operators to distribute task replicas across different server priorities for realtime indexing tasks. Similar to Historical tiering, this enables query isolation for mixed workload scenarios on the Peons, allowing some task replicas to handle queries of specific priorities.

[#19040](https://github.com/apache/druid/pull/19040)

##### Other streaming ingestion improvements

- Improved cost-based autoscaler performance in high lag scenarios [#19045](https://github.com/apache/druid/pull/19045)


#### Ingestion improvements

- Sped up task scheduling on the Overlord [#19199](https://github.com/apache/druid/pull/19199)

### Querying

#### groupBy query configuration

Added a new groupBy query configuration property `druid.query.groupBy.maxSpillFileCount` to limit the maximum number of spill files created per query. When the limit is exceeded, the query fails with a clear error message instead of causing Historical nodes to run out of memory during spill file merging. The limit can also be overridden per query using the query context parameter `maxSpillFileCount`.

[#19141](https://github.com/apache/druid/pull/19141)

#### Improved handling of nested aggregates

Druid can now merge two Aggregates with a Projection between them. For example, the following query:

```sql
SELECT
  hr,
  UPPER(t1.x) x,
  SUM(t1.cnt) cnt,
  MIN(t1.mn) mn,
  MAX(t1.mx) mx
FROM (
  SELECT
    floor(__time to hour) hr,
    dim2 x,
    COUNT(*) cnt,
    MIN(m1 * 5) mn,
    MAX(m1 + m2) mx
  FROM druid.foo
  WHERE dim2 IN ('abc', 'def', 'a', 'b', '')
  GROUP BY 1, 2
) t1
WHERE t1.x IN ('abc', 'foo', 'bar', 'a', '')
GROUP BY 1, 2
```

can be simplified to the following:

```sql
SELECT
  FLOOR(__time TO hour) hr,
  UPPER(dim2) x,
  COUNT(*) cnt,
  MIN(m1 * 5) mn,
  MAX(m1 + m2) mx
FROM druid.foo
WHERE dim2 IN ('abc', 'a', '')
GROUP BY 1, 2
```

[#18498](https://github.com/apache/druid/pull/18498)

#### Other querying improvements

- Added `durationMs` to Dart query reports [#19169](https://github.com/apache/druid/pull/19169)
- Improved error handling so that row signature column order is preserved when column analysis encounters an error [#19162](https://github.com/apache/druid/pull/19162)
- Improved GROUP BY performance [#18952](https://github.com/apache/druid/pull/18952)
- Improved expression filters to take advantage of specialized virtual columns when possible, resulting in better performance for the query [#18965](https://github.com/apache/druid/pull/18965)

### Cluster management

#### New Broker tier selection strategies

Operators can now configure two new Broker `TierSelectorStrategy` implementations:

- `strict` - Only selects servers whose priorities match the configured list. Example configuration: `druid.broker.select.tier=strict` and `druid.broker.select.tier.strict.priorities=[1]`.
- `pooled` - Pools servers across the configured priorities and selects among them, allowing queries to utilize multiple priority tiers for improved availability. Example configuration: `druid.broker.select.tier=pooled` and `druid.broker.select.tier.pooled.priorities=[2,1]`.

These strategies can also be configured for realtime servers using `druid.broker.realtime.select.tier`.

[#19094](https://github.com/apache/druid/pull/19094)

#### Druid operator

The Druid operator now resides in its own repository: [`apache/druid-operator`](https://github.com/apache/druid-operator).

[#19156](https://github.com/apache/druid/pull/19156)

#### Cost-based autoscaler algorithm

The algorithm for cost-based autoscaling has been changed:

- Scale up more aggressively when per-partition lag is meaningful
- Relax the partitions-per-task increase limit based on lag severity and headroom
- Keep behavior conservative near `taskCountMax` and avoid negative headroom effects 

[#18936](https://github.com/apache/druid/pull/18936)

#### Other cluster management improvements

- Added `/status/ready` endpoint for service health so that external load balancers can handle a graceful shutdown better [#19148](https://github.com/apache/druid/pull/19148)
- Added a configurable option to scale-down during task run time for cost-based autoscaler [#18958](https://github.com/apache/druid/pull/18958)
- Added `storage_size` to `sys.servers` to facilitate retrieving disk cache size for Historicals when using the virtual storage fabric [#18979](https://github.com/apache/druid/pull/18979)
- Added a log for new task count computation for the cost-based auto scaler [#18929](https://github.com/apache/druid/pull/18929)
- Changed how the scaling is calculated from a square root-based scaling formula to a logarithmic formula that provides better emergency recovery at low task counts and millions of lag [#18976](https://github.com/apache/druid/pull/18976)
- Improved the load speed of cached segments during Historical startup [#18489](https://github.com/apache/druid/pull/18489)
- Improved Broker startup time by parallelizing buffer initialization [#19025](https://github.com/apache/druid/pull/19025)
- Improved the stack trace for MSQ task engine worker failures so that they're preserved [#19049](https://github.com/apache/druid/pull/19049)
- Improved the performance of the cost-based autoscaler during loaded lag conditions [#18991](https://github.com/apache/druid/pull/18991)

### Data management

#### Durable storage cleaner

The durable storage cleaner now supports configurable time-based retention for MSQ query results. Previously, query results were retained for all known tasks, which was unreliable for completed tasks. With this change, query results are retained for a configurable time period based on the task creation time.

The new configuration property `druid.msq.intermediate.storage.cleaner.durationToRetain` controls the retention period for query results. The default retention period is 6 hours.

[#19074](https://github.com/apache/druid/pull/19074)

#### Other data management improvements

- Changed the default value for `druid.indexing.formats.maxStringLength` to null from 0 [#19198](https://github.com/apache/druid/pull/19198)

### Metrics and monitoring

#### Monitoring supervisor state

Added a new `supervisor/count` metric when `SupervisorStatsMonitor` is enabled in `druid.monitoring.monitors`. The metric reports each supervisor’s state, such as `RUNNING` or `SUSPENDED`, for Prometheus, StatsD, and other metric systems.

[#19114](https://github.com/apache/druid/pull/19114)

#### Improved `groupBy` metrics

`GroupByStatsMonitor` now provides the following metrics:

- `mergeBuffer/bytesUsed` 
- `mergeBuffer/maxAcquisitionTimeNs`
- `groupBy/maxSpilledBytes`
- `groupBy/maxMergeDictionarySize`

[#18731](https://github.com/apache/druid/pull/18731) [#18970](https://github.com/apache/druid/pull/18970) [#18934](https://github.com/apache/druid/pull/18934)

#### Filtering metrics

Operators can limit which metrics the logging emitter writes by setting `druid.emitter.logging.shouldFilterMetrics=true` and, if desired, `druid.emitter.logging.allowedMetricsPath` to a JSON object file where the keys are metric names. A missing custom file results in a warning and use of the bundled `defaultMetrics.json`. Alerts and other non-metric events are always logged.

[#19030](https://github.com/apache/druid/pull/19030)

#### New Broker metrics

Added `segment/schemaCache/rowSignature/changed` and `segment/schemaCache/rowSignature/column/count` metrics to get visibility into when the Broker's segment metadata cache's row signature for each datasource is initialized and updated.

[#18966](https://github.com/apache/druid/pull/18966)

#### Other metrics and monitoring improvements

- Added the following metrics to the default for Prometheus: `mergeBuffer/bytesUsed` and `mergeBuffer/maxBytesUsed` [#19110](https://github.com/apache/druid/pull/19110)
- Added compaction mode to what gets emitted for the `compact/task/count` metric [#19151](https://github.com/apache/druid/pull/19151)
- Added support for logging and emitting SQL dynamic parameter values [#19067](https://github.com/apache/druid/pull/19067)
- Added `ingest/rows/published`, which gets emitted from all task types to denote the total row count of successfully published segments [#19177](https://github.com/apache/druid/pull/19177)
- Added `queries` and `totalQueries` counters, which reflect queries made to realtime servers to get realtime data
- Added `tier/storage/capacity` metric for the Coordinator to emit that is guaranteed to reflect the total `StorageLocation` size configured across all Historicals in a tier [#18962](https://github.com/apache/druid/pull/18962)
- Added new metrics for virtual storage fabric to the MSQ task engine `ChannelCounters`: `loadBytes`, `loadTime`, `loadWait`, and `loadFiles` [#18971](https://github.com/apache/druid/pull/18971)
- Added `storage/virtual/hit/bytes` metric to `StorageMonitor`
[#18895](https://github.com/apache/druid/pull/18895)
- Added `supervisorId` dimension for streaming tasks to `TaskCountStatsMonitor` [#18920](https://github.com/apache/druid/pull/18920)
- Changed `StorageMonitor` to always be on [#19048](https://github.com/apache/druid/pull/19048)
- Improved the metrics for autoscalers, so that they all emit the same metrics: `supervisorId`, `dataSource`, and `stream` [#19097](https://github.com/apache/druid/pull/19097)

### Extensions

#### gRPC 

The gRPC query extension now cancels in-flight queries when clients cancel or disconnect.

[#19005](https://github.com/apache/druid/pull/19005)

#### Iceberg 

You can now configure residual filters for non-partition columns when using the Iceberg input source. Set `residualFilterMode` in the Iceberg input source to one of the following:

- `ignore`: (default) Ingest the residual rows with a warning log unless filtered by `transformSpec`.
- `fail`: Fail the ingestion job when residual filters are detected. Use this to ensure that filters only target partition columns.

[#18953](https://github.com/apache/druid/pull/18953)

#### HDFS storage

Added support for `lz4` compression. As part of this change, the following metrics are now available:

- `hdfs/pull/size`
- `hdfs/pull/duration`
- `hdfs/push/size`
- `hdfs/push/duration`

[#18982](https://github.com/apache/druid/pull/18982)

## Upgrade notes and incompatible changes

### Upgrade notes

#### Hadoop-based ingestion

Support for Hadoop-based ingestion has been removed. The feature was deprecated in Druid 34.

Use one of Druid's other supported ingestion methods, such as SQL-based ingestion or MiddleManager-less ingestion using Kubernetes.

[#19109](https://github.com/apache/druid/pull/19109)

#### Segment metadata cache on by default

Starting in Druid 37, the segment metadata cache is on by default. This feature allows the Broker to cache segment metadata polled from the Coordinator, rather than having to fetch metadata for every query against the `sys.segments` table. This improves performance but increases memory usage on Brokers.

The `druid.sql.planner.metadataSegmentCacheEnable` config controls this feature.

[#19075](https://github.com/apache/druid/pull/19075)

#### Streaming ingestion `parser`

Support for the deprecated `parser` has been removed for streaming ingest tasks such as Kafka and Kinesis. Operators must now specify `inputSource`/`inputFormat` on the `ioConfig` of the supervisor spec, and the `dataSchema` must not specify a parser. Do this before upgrading to Druid 37 or newer.

[#19173](https://github.com/apache/druid/pull/19173) [#19166](https://github.com/apache/druid/pull/19166)

#### Rolling upgrades from Druid versions prior to version 0.23

You can't perform a rolling upgrade from versions earlier than Druid 0.23.

[#18961](https://github.com/apache/druid/pull/18961)

#### Metadata storage for auto-compaction with compaction supervisors

Automatic compaction using compaction supervisors now requires incremental segment metadata caching to be enabled on the Overlord and Coordinator via runtime properties. Specifically, the `druid.manager.segments.useIncrementalCache` config must be set to `always` or `ifSynced`. For more information about the config, see [Segment metadata cache](https://druid.apache.org/docs/latest/configuration/#segment-metadata-cache-experimental).

Additionally, metadata store changes are required for this upgrade.

If you already have `druid.metadata.storage.connector.createTables` set to `true`, no action is needed.

If you have this feature turned off, you will need to alter the segments table and create the `compactionStates` table. The Postgres DDL is provided below as a guide:

```
-- create the indexing states lookup table and associated indices
CREATE TABLE druid_indexingStates (
    created_date VARCHAR(255) NOT NULL,
    datasource VARCHAR(255) NOT NULL,
    fingerprint VARCHAR(255) NOT NULL,
    payload BYTEA NOT NULL,
    used BOOLEAN NOT NULL,
    pending BOOLEAN NOT NULL,
    used_status_last_updated VARCHAR(255) NOT NULL,
    PRIMARY KEY (fingerprint),
  );

  CREATE INDEX idx_druid_compactionStates_used ON druid_compactionStates(used, used_status_last_updated);
```

```
-- modify druid_segments table to have a column for storing compaction state fingerprints
ALTER TABLE druid_segments ADD COLUMN indexing_state_fingerprint VARCHAR(255);
```

You may have to adapt the syntax to fit your table naming prefix and metadata store backend.

[#18844](https://github.com/apache/druid/pull/18844)

#### Segment locking

Segment locking and `NumberedOverwriteShardSpec` are deprecated and will be removed in a future release. Use time chunk locking instead. You can make sure only time chunk locking is used by setting `druid.indexer.tasklock.forceTimeChunkLock` to `true`.

[#19050](https://github.com/apache/druid/pull/19050)

### Incompatible changes

#### Removed `defaultProcessingRate` config

This config allowed scaling actions to begin prior to the first metrics becoming available. 

[#19028](https://github.com/apache/druid/pull/19028)

#### Front-coding format

Druid now defaults to v1 of the front-coded format instead of version 0 if enabled. Version 1 was introduced in Druid 26. Downgrading to or upgrading from a version of Druid prior to 26 may require reindexing if you have front-coding enabled with version 0.

[#18984](https://github.com/apache/druid/pull/18984)

### Developer notes

- Added `AGENTS.md` [#19084](https://github.com/apache/druid/pull/19084)
- Added a requirement to use [conventional commit syntax](https://www.conventionalcommits.org/en/v1.0.0/) [#19089](https://github.com/apache/druid/pull/19089)
- Updated `checkstyle` from `3.0.0` to `3.6.0` [#19064](https://github.com/apache/druid/pull/19064)

#### Dependency updates

The following dependencies have been updated:

- Added `software.amazon.awssdk` to support `WebIdentityTokenProvider` [#19178](https://github.com/apache/druid/pull/19178)
- `org.apache.iceberg` from `1.6.1` to `1.7.2` [#19172](https://github.com/apache/druid/pull/19172)
- `diff` node module from 4.0.1 to 4.0.4 [#18933](https://github.com/apache/druid/pull/18933)
- `org.apache.avro` from 1.11.4 to 1.11.5 [#19103](https://github.com/apache/druid/pull/19103)
- `bytebuddy` from `1.17.7` to `1.18.3` [#19000](https://github.com/apache/druid/pull/19000)
- `slf4j` from `2.0.16` to `2.0.17` [#18990](https://github.com/apache/druid/pull/18990)
- Apache Commons Codec from `1.16.1` to `1.17.1` [#18990](https://github.com/apache/druid/pull/18990)
- `jacoco` from `0.8.12` to `0.8.14` [#18990](https://github.com/apache/druid/pull/18990)
- `docker-java-bom` from `3.6.0` to `3.7.0` [#18990](https://github.com/apache/druid/pull/18990)
- `assertj-core` from `3.24.2` to `3.27.7` [#18994](https://github.com/apache/druid/pull/18994)
- `maven-surefire-plugin` from `3.2.5` to `3.5.4` [#18847](https://github.com/apache/druid/pull/18847)
- `guice` from `5.1.0` to `6.0.0` [#18986](https://github.com/apache/druid/pull/18986)
- JDK compiler from 11 to 17 [#18977](https://github.com/apache/druid/pull/18977)
- `vertx` from `4.5.14` to `4.5.24` [#18947](https://github.com/apache/druid/pull/18947)
- `fabric8` from `7.4.0` to `7.5.2` [#18947](https://github.com/apache/druid/pull/18947)
- `mockito` from `5.14.2` to `5.23` [#19145](https://github.com/apache/druid/pull/19145)
- `easymock` from `5.2.0` to `5.6.0` [#19145](https://github.com/apache/druid/pull/19145)
- `equalsverifier` from `3.15.8` to `4.4.1` [#19145](https://github.com/apache/druid/pull/19145)
- `bytebuddy` from `1.18.3` to `1.18.5` [#19145](https://github.com/apache/druid/pull/19145)
- Added `objenesis` `3.5` [#19145](https://github.com/apache/druid/pull/19145)
- `org.apache.zookeeper` from 3.8.4 to 3.8.6 [#19135](https://github.com/apache/druid/pull/19135)
- Added AWS SDK `2.40.0` [#18891](https://github.com/apache/druid/pull/18891)
- `com.lmax.disruptor` from `3.3.6` to `3.4.4` [#19122](https://github.com/apache/druid/pull/19122)
- `org.junit.junit-bom` from `5.13.3` to `5.14.3` [#19122](https://github.com/apache/druid/pull/19122)
- Several Kubernetes related dependencies have been updated. For more information, see [#19071](https://github.com/apache/druid/pull/19071)