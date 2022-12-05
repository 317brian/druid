---
title: "WIP release notes for 25.0"
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

## Query engine

### BIG_SUM SQL function

Added SQL function `BIG_SUM` that uses the [Compressed Big Decimal](https://github.com/apache/druid/pull/10705) Druid extension.

https://github.com/apache/druid/pull/13102

### Added Compressed Big Decimal min and max functions

Added min and max functions for Compressed Big Decimal and exposed these functions via SQL: BIG_MIN and BIG_MAX.

https://github.com/apache/druid/pull/13141

### Metrics used to downsample bucket

Changed the way the MSQ task engine determines whether or not to downsample data, to improve accuracy. The task engine now uses the number of bytes instead of number of keys.

https://github.com/apache/druid/pull/12998

### MSQ heap footprint

When determining partition boundaries, the heap footprint of the sketches that MSQ uses is capped at 10% of available memory or 300 MB, whichever is lower. Previously, the cap was strictly 300 MB.

https://github.com/apache/druid/pull/13274

### MSQ Docker improvement

Enabled MSQ task query engine for Docker by default.

https://github.com/apache/druid/pull/13069

### Improved MSQ warnings

For disallowed MSQ warnings of certain types, the warning is now surfaced as the error.

https://github.com/apache/druid/pull/13198

### Added support for indexSpec

The MSQ task engine now supports the `indexSpec` context parameter. This context parameter can also be configured through the web console.

https://github.com/apache/druid/pull/13275

### Added task start status to the worker report

Added `pendingTasks` and `runningTasks` fields to the worker report for the MSQ task engine.
See [Query task status information](#query-task-status-information) for related web console changes.

https://github.com/apache/druid/pull/13263

### Improved handling of secrets

When MSQ submits tasks containing SQL with sensitive keys, the keys can get logged in the file.
Druid now masks the sensitive keys in the log files using regular expressions.

https://github.com/apache/druid/pull/13231

### Use worker number to communicate between tasks

Changed the way WorkerClient communicates between the worker tasks, to abstract away the complexity of resolving the `workerNumber` to the `taskId` from the callers.
Once the WorkerClient writes it's outputs to the durable storage, it adds a file with `__success` in the `workerNumber` output directory for that stage and with its `taskId`. This allows you to determine the worker, which has successfully written its outputs to the durable storage, and differentiate from the partial outputs by orphan or failed worker tasks.

https://github.com/apache/druid/pull/13062

### Sketch merging mode

When a query requires key statistics to generate partition boundaries, key statistics are gathered by the workers while reading rows from the datasource.You can now configure whether the MSQ task engine does this task in parallel or sequentially. Configure the behavior using `clusterStatisticsMergeMode` context parameter. For more information, see [Sketch merging mode](https://druid.apache.org/docs/latest/multi-stage-query/reference.html#sketch-merging-mode).

https://github.com/apache/druid/pull/13205 

## Querying

### Improvements to querying user experience

This release includes several improvements for querying:

* Exposed HTTP response headers for SQL queries (https://github.com/apache/druid/pull/13052)
* Added the `shouldFinalize` feature for HLL and quantiles sketches. Druid will no longer finalize aggregators when:
    - aggregators appear in the outer level of a query
    - aggregators are used as input to an expression or finalizing-field-access post-aggregator

    To provide backwards compatibility, we added a `sqlFinalizeOuterSketches` query context parameter that restores the old behavior (https://github.com/apache/druid/pull/13247)

### Enabled async reads for JDBC

Prevented JDBC timeouts on long queries by returning empty batches when a batch fetch takes too long. Uses an async model to run the result fetch concurrently with JDBC requests.

https://github.com/apache/druid/pull/13196

### Enabled composite approach for checking in-filter values set in column dictionary

To accommodate large value sets arising from large in-filters or from joins pushed down as in-filters, Druid now uses sorted merge algorithm for merging the set and dictionary for larger values.

https://github.com/apache/druid/pull/13133

### Added new configuration keys to query context security model

Added the following configuration keys that refine the query context security model controlled by `druid.auth.authorizeQueryContextParams`:
* `druid.auth.unsecuredContextKeys`: The set of query context keys that do not require a security check.
* `druid.auth.securedContextKeys`: The set of query context keys that do require a security check.

## Nested columns

### Support for more formats

Druid nested columns and associated JSON transform functions now supports Avro, ORC, and Parquet.

https://github.com/apache/druid/pull/13325 

https://github.com/apache/druid/pull/13375 

### Refactored a data source before unnest 

When data requires "flattening" during processing, the operator now takes in an array and then flattens the array into N (N=number of elements in the array) rows where each row has one of the values from the array.

https://github.com/apache/druid/pull/13085

## Ingestion

### Improved filtering for cloud objects

You can now stop at arbitrary subfolders using glob syntax in the `ioConfig.inputSource.filter` field for native batch ingestion from cloud storage, such as S3. 

https://github.com/apache/druid/pull/13027

### CLUSTERED BY limit

When using the MSQ task engine to ingest data, there is now a 1,500 column limit to the number of columns that can be passed in the CLUSTERED BY clause.

https://github.com/apache/druid/pull/13352

### Async task client for streaming ingestion

You can now use asynchronous communication with indexing tasks by setting `chatAsync` to true in the `tuningConfig`. Enabling asynchronous communication means that the `chatThreads` property is ignored.

https://github.com/apache/druid/pull/13354 

### Improved control for how Druid reads JSON data for streaming ingestion

You can now better control how Druid reads JSON data for streaming ingestion by setting the following fields in the input format specification:

* `assumedNewlineDelimited` to parse lines of JSON independently.
* `useJsonNodeReader` to retain valid JSON events when parsing multi-line JSON events when a parsing exception occurs.

The web console has been updated to include these options.

https://github.com/apache/druid/pull/13089

## Updated Kafka version

Updated the Apache Kafka core dependency to version 3.3.1.

https://github.com/apache/druid/pull/13176

### Kafka Consumer improvement

Allowed Kafka Consumer's custom deserializer to be configured after its instantiation.

https://github.com/apache/druid/pull/13097

### Kafka supervisor logging

Kafka supervisor logs are now less noisy. The supervisors now log events at the DEBUG level instead of INFO. 

https://github.com/apache/druid/pull/13392

### Fixed Overlord leader election

Fixed a problem where Overlord leader election failed due to lock reacquisition issues. Druid now fails these tasks and clears all locks so that the Overlord leader election isn't blocked.

https://github.com/apache/druid/pull/13172

### Support for inline protobuf descriptor

Added a new `inline` type `protoBytesDecoder` that allows a user to pass inline the contents of a Protobuf descriptor file, encoded as a Base64 string.

https://github.com/apache/druid/pull/13192

### Duplicate notices

For streaming ingestion, notices that are the same as one already in queue won't be enqueued. This will help reduce notice queue size. 

https://github.com/apache/druid/pull/13334

### When a Kafka stream becomes inactive, prevent Supervisor from creating new indexing tasks

Added Idle feature to `SeekableStreamSupervisor` for inactive stream.

https://github.com/apache/druid/pull/13144


### Sampling from stream input now respects the configured timeout

Fixed a problem where sampling from a stream input, such as Kafka or Kinesis, failed to respect the configured timeout when the stream had no records available. You can now set the maximum amount of time in which the entry iterator will return results.

https://github.com/apache/druid/pull/13296

### Streaming tasks resume on Overlord switch

Fixed a problem where streaming ingestion tasks continued to run until their duration elapsed after the Overlord leader had issued a pause to the tasks. Now, when the Overlord switch occurs right after it has issued a pause to the task, the task remains in a paused state even after the Overlord re-election.

https://github.com/apache/druid/pull/13223

### Fixed Parquet list conversion

Fixes an issue with Parquet list conversion, where lists of complex objects could unexpectedly be wrapped in an extra object, appearing as `[{"element":<actual_list_element>},{"element":<another_one>}...]` instead of the direct list. This changes the behavior of the parquet reader for lists of structured objects to be consistent with other parquet logical list conversions. The data is now fetched directly, more closely matching its expected structure.

https://github.com/apache/druid/pull/13294

### Introduced a tree type to flattenSpec

Introduced a `tree` type to `flattenSpec`. In the event that a simple hierarchical lookup is required, the `tree` type allows for faster JSON parsing than `jq` and `path` parsing types.

https://github.com/apache/druid/pull/12177

## Operations

### Compaction

Compaction behavior has changed to improve the amount of time it takes and disk space it takes:

- When segments need to be fetched, download them one at a time and delete them when Druid is done with them. This still takes time but minimizes the required disk space.
- Don't fetch segments on the main compact task when they aren't needed. If the user provides a full `granularitySpec`, `dimensionsSpec`, and `metricsSpec`, Druid skips fetching segments.

For more information, see the documentation on [Compaction](https://druid.apache.org/docs/latest/data-management/compaction.html) and [Automatic compaction](https://druid.apache.org/docs/latest/data-management/automatic-compaction.html).

https://github.com/apache/druid/pull/13280

### New metric for segments

`segment/handoff/time` captures the total time taken for handoff for a given set of published segments.

https://github.com/apache/druid/pull/13238 

### Idle configs for the Supervisor

You can now configure the following properties:

| Property | Description | Default |
| - | - | -|
|`druid.supervisor.idleConfig.enabled`| (Cluster wide) If `true`, supervisor can become idle if there is no data on input stream/topic for some time.|false|
|`druid.supervisor.idleConfig.inactiveAfterMillis`| (Cluster wide) Supervisor is marked as idle if all existing data has been read from input topic and no new data has been published for `inactiveAfterMillis` milliseconds.|`600_000`|
| `inactiveAfterMillis` | (Individual Supervisor) Supervisor is marked as idle if all existing data has been read from input topic and no new data has been published for `inactiveAfterMillis` milliseconds. | no (default == `600_000`) |

https://github.com/apache/druid/pull/13311

https://github.com/apache/druid/pull/13321

### New metrics for streaming ingestion

The following metrics related to streaming ingestion have been added:

- `ingest/kafka/partitionLag`: Partition-wise lag between the offsets consumed by the Kafka indexing tasks and latest offsets in Kafka brokers. 
- `ingest/kinesis/partitionLag/time`: Partition-wise lag time in milliseconds between the current message sequence number consumed by the Kinesis indexing tasks and latest sequence number in Kinesis.
- `ingest/pause/time`: Milliseconds spent by a task in a paused state without ingesting.|dataSource, taskId| < 10 seconds.|

https://github.com/apache/druid/pull/13331
https://github.com/apache/druid/pull/13313

### Backoff for HttpPostEmitter

The `HttpPostEmitter` option now has a backoff. This means that there should be less noise in the logs and lower CPU usage if you use this option for logging. 

https://github.com/apache/druid/pull/12102

### taskActionType dimension for task/action/run/time metric

The `task/action/run/time` metric for the Indexing service now includes the `taskActionType` dimension.

https://github.com/apache/druid/pull/13333

### DumpSegment tool for nested columns

The DumpSegment tool can now be used on nested columns with the `--dump nested` option. 

For more information, see [dump-segment tool](https://druid.apache.org/docs/latest/operations/dump-segment).

https://github.com/apache/druid/pull/13356

### Segment loading and balancing

#### cachingCost balancer strategy

The `cachingCost` balancer strategy now behaves more similarly to cost strategy. When computing the cost of moving a segment to a server, the following calculations are performed:

- Subtract the self cost of a segment if it is being served by the target server
- Subtract the cost of segments that are marked to be dropped

#### Segment assignment

You can now use a round-robin segment strategy to speed up initial segment assignments.

Set `useRoundRobinSegmentAssigment` to `true` in the Coordinator dynamic config to enable this feature.

https://github.com/apache/druid/pull/13367

#### Sampling segments for balancing

Batch sampling is now the default method for sampling segments during balancing as it performs significantly better than the alternative when there is a large number of used segments in the cluster.

As part of this change, the following have been deprecated and will be removed in future releases:

- coordinator dynamic config `useBatchedSegmentSampler`
- coordinator dynamic config `percentOfSegmentsToConsiderPerMove`
- non-batch method of sampling segments used by coordinator duty `BalanceSegments`

The unused coordinator property `druid.coordinator.loadqueuepeon.repeatDelay` has been removed.

Use only `druid.coordinator.loadqueuepeon.http.repeatDelay` to configure repeat delay for the HTTP-based segment loading queue.

https://github.com/apache/druid/pull/13391

#### Segment discovery

The default segment discovery method now uses HTTP instead of ZooKeeper.

https://github.com/apache/druid/pull/13092

#### Segment replication

Improved the process of checking server inventory to prevent over-replication of segments during segment balancing.

https://github.com/apache/druid/pull/13114

### Memory estimates

The task context flag `useMaxMemoryEstimates` is now set to false by default to improve memory usage estimation.

https://github.com/apache/druid/pull/13178

### Docker improvements

Updated dependencies for the Druid image for Docker, including JRE 11. Docker BuildKit cache is enabled to speed up building.

https://github.com/apache/druid/pull/13059



### Kill tasks do not include markAsUnuseddone

When you kill a task, Druid no longer automatically marks segments as unused. You must explicitly mark them as unused with `POST /druid/coordinator/v1/datasources/{dataSourceName}/markUnused`. 
For more information, see the [API reference](https://druid.apache.org/docs/latest/operations/api-reference.html#coordinator)

https://github.com/apache/druid/pull/13104

### Nested columns performance improvement

Improved `NestedDataColumnSerializer` to no longer explicitly write null values to the field writers for the missing values of every row. Instead, passing the row counter is moved to the field writers so that they can backfill null values in bulk.

https://github.com/apache/druid/pull/13101

### Provided service specific log4j overrides in containerized deployments

Provided an option to override log4j configs setup at the service level directories so that it works with Druid-operator based deployments.

https://github.com/apache/druid/pull/13020

### Various Docker improvements

* Updated Docker to run with JRE 11 by default.
* Updated Docker to use [`gcr.io/distroless/java11-debian11`](https://github.com/GoogleContainerTools/distroless) image as base by default.
* Enabled Docker buildkit cache to speed up building.
* Downloaded [`bash-static`](https://github.com/robxu9/bash-static) to the Docker image so that scripts that require bash can be executed.
* Bumped builder image from `3.8.4-jdk-11-slim` to `3.8.6-jdk-11-slim`.
* Switched busybox from `amd64/busybox:1.30.0-glibc` to `busybox:1.35.0-glibc`.
* Added support to build arm64-based image.

https://github.com/apache/druid/pull/13059

### Improved supervisor termination

Fixed issues with delayed supervisor termination during certain transient states.

https://github.com/apache/druid/pull/13072

### Fixed a problem when running Druid with JDK11+

Export `com.sun.management.internal` when running Druid under JRE11 and JRE17.

https://github.com/apache/druid/pull/13068

### Enabled cleaner JSON for various input sources and formats

Added `JsonInclude` to various properties, to avoid population of default values in serialized JSON.

https://github.com/apache/druid/pull/13064

### Improved metric reporting

Improved global-cached-lookups metric reporting.

https://github.com/apache/druid/pull/13219

### Fixed a bug in HttpPostEmitter

Fixed a bug in HttpPostEmitter where the emitting thread was prematurely stopped while there was data to be flushed.

https://github.com/apache/druid/pull/13237

### Improved direct memory check on startup

Improved direct memory check on startup by providing better support for Java 9+ in `RuntimeInfo`, and clearer log messages where validation fails.

https://github.com/apache/druid/pull/13207

### Added a new way of storing STRING type columns

Added support for 'front coded' string dictionaries for smaller string columns.

https://github.com/apache/druid/pull/12277

### Improved the run time of the MarkAsUnusedOvershadowedSegments duty

Improved the run time of the MarkAsUnusedOvershadowedSegments duty by iterating over all overshadowed segments and marking segments as unused in batches.

https://github.com/apache/druid/pull/13287

## Extensions

### Extension optimization

Optimized the `compareTo` function in `CompressedBigDecimal`.

https://github.com/apache/druid/pull/13086

### CompressedBigDecimal cleanup and extension

Removed unnecessary generic type from CompressedBigDecimal, added support for number input types, added support for reading aggregator input types directly (uningested data), and fixed scaling bug in buffer aggregator.

https://github.com/apache/druid/pull/13048

### Support for running tasks as Kubernetes jobs

Added an extension that allows Druid to use Kubernetes for launching and managing tasks, eliminating the need for MiddleManagers.
To use this extension, [include](../extensions.md#loading-extensions) `druid-kubernetes-overlord-extensions` in the extensions load list for your Overlord process.

https://github.com/apache/druid/pull/13156

### Support for Kubernetes discovery

Added `POD_NAME` and `POD_NAMESPACE` env variables to all Kubernetes Deployments and StatefulSets.
Helm deployment is now compatible with `druid-kubernetes-extension`.

https://github.com/apache/druid/pull/13262

## Web console

### Removed the old query view

The old query view is removed. Use the new query view with tabs.
For more information, see [Web console](https://druid.apache.org/docs/latest/operations/web-console.html#query).

https://github.com/apache/druid/pull/13169

### Filter column values in query results

The web console now allows you to add to existing filters for a selected column.

https://github.com/apache/druid/pull/13169

### Ability to add issue comments

You can now add an issue comment in SQL, for example `--:ISSUE: this is an issue` that is rendered in red and prevents the SQL from running. The comments are used by the spec-to-SQL converter to indicate that something could not be converted.

https://github.com/apache/druid/pull/13136

### Support for Kafka lookups

Added support for Kafka-based lookups rendering and input in the web console.

https://github.com/apache/druid/pull/13098

### Improved array detection

Added better detection for arrays containing objects.

https://github.com/apache/druid/pull/13077

### Updated Druid Query Toolkit version

[Druid Query Toolkit](https://www.npmjs.com/package/druid-query-toolkit) version 0.16.1 adds quotes to references in auto generated queries by default.

https://github.com/apache/druid/pull/13243

### Query task status information

The web console now exposes a textual indication about running and pending tasks when a query is stuck due to lack of task slots.

https://github.com/apache/druid/pull/13291

### Query history

Multi-stage queries no longer show up in the Query history dialog. They are still available in the **Recent query tasks** panel.