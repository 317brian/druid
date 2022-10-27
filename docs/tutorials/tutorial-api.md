---
id: tutorial-api-intro
title: "Tutorial: Learn the basics of the Druid API"
sidebar_label: "Learn the Druid API"
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

This tutorial introduces you to the basics of the Druid API and some of the endpoints you might use frequently. The quickstart deployment that this tutorial uses configures Druid to listen on port `8888` by default, so you'll be making API calls against `http://localhost:8888`. 

For more information about the API, see the following pages:

- [API reference](./../operations/api-reference.md).
-  [Druid SQL API](./../querying/sql-api.md)
-  [JDBC driver API](./../querying/sql-jdbc.md)

## Before you start

To complete this tutorial, you'll need 

- Druid cluster
  - You can use the `micro-quickstart` single-machine configuration described in the [quickstart](index.md). You don't need to have any data loaded at this point. That'll be part of this tutorial. 
- [Avatica JDBC driver version 1.17.0 or later](https://calcite.apache.org/avatica/downloads/)  
  - Only needed f you want to complete the JDBC portion of this tutorial.

When ready, start up the Druid services:

```bash
./bin/start-micro-quickstart
```

## Get the status of your cluster

### Basic cluster information 

You can get basic information about your cluster from the `/status` endpoint, including the Druid version, loaded extensions, and resource consumption.

#### Sample request

<!--DOCUSAURUS_CODE_TABS-->

<!--HTTP-->

```
GET /status
```

<!-- curl -->

This example pipes the response to pretty print:

```bash
curl --location --request GET 'http://localhost:8888/status' | json_pp
```

<!--Python-->

```python
import requests

url = "http://localhost:8888/status"

payload={}
headers = {}

response = requests.request("GET", url, headers=headers, data=payload)

print(response.text)
```

<!--END_DOCUSAURUS_CODE_TABS-->

#### Sample response

<details><summary>Show the pretty printed response</summary>

```json
{
   "memory" : {
      "directMemory" : 134217728,
      "freeMemory" : 30675512,
      "maxMemory" : 134217728,
      "totalMemory" : 134217728,
      "usedMemory" : 103542216
   },
   "modules" : [
      {
         "artifact" : "druid-gcp-common",
         "name" : "org.apache.druid.common.gcp.GcpModule",
         "version" : "24.0.0"
      },
      {
         "artifact" : "druid-aws-common",
         "name" : "org.apache.druid.common.aws.AWSModule",
         "version" : "24.0.0"
      },
      {
         "artifact" : "druid-hdfs-storage",
         "name" : "org.apache.druid.storage.hdfs.HdfsStorageDruidModule",
         "version" : "24.0.0"
      },
      {
         "artifact" : "druid-kafka-indexing-service",
         "name" : "org.apache.druid.indexing.kafka.KafkaIndexTaskModule",
         "version" : "24.0.0"
      },
      {
         "artifact" : "druid-datasketches",
         "name" : "org.apache.druid.query.aggregation.datasketches.theta.SketchModule",
         "version" : "24.0.0"
      },
      {
         "artifact" : "druid-datasketches",
         "name" : "org.apache.druid.query.aggregation.datasketches.theta.oldapi.OldApiSketchModule",
         "version" : "24.0.0"
      },
      {
         "artifact" : "druid-datasketches",
         "name" : "org.apache.druid.query.aggregation.datasketches.quantiles.DoublesSketchModule",
         "version" : "24.0.0"
      },
      {
         "artifact" : "druid-datasketches",
         "name" : "org.apache.druid.query.aggregation.datasketches.tuple.ArrayOfDoublesSketchModule",
         "version" : "24.0.0"
      },
      {
         "artifact" : "druid-datasketches",
         "name" : "org.apache.druid.query.aggregation.datasketches.hll.HllSketchModule",
         "version" : "24.0.0"
      },
      {
         "artifact" : "druid-multi-stage-query",
         "name" : "org.apache.druid.msq.guice.MSQExternalDataSourceModule",
         "version" : "24.0.0"
      },
      {
         "artifact" : "druid-multi-stage-query",
         "name" : "org.apache.druid.msq.guice.MSQIndexingModule",
         "version" : "24.0.0"
      },
      {
         "artifact" : "druid-multi-stage-query",
         "name" : "org.apache.druid.msq.guice.MSQDurableStorageModule",
         "version" : "24.0.0"
      },
      {
         "artifact" : "druid-multi-stage-query",
         "name" : "org.apache.druid.msq.guice.MSQServiceClientModule",
         "version" : "24.0.0"
      },
      {
         "artifact" : "druid-multi-stage-query",
         "name" : "org.apache.druid.msq.guice.MSQSqlModule",
         "version" : "24.0.0"
      },
      {
         "artifact" : "druid-multi-stage-query",
         "name" : "org.apache.druid.msq.guice.SqlTaskModule",
         "version" : "24.0.0"
      }
   ],
   "version" : "24.0.0"
}
```

</details>

### Cluster health

Another useful endpoint is `/status/health.` This endpoint returns `true` with a 200 OK response. 

#### Sample request

<!--DOCUSAURUS_CODE_TABS-->

<!--HTTP-->

```
GET /status/health
```

<!-- curl -->

```bash
curl --location --request GET 'http://localhost:8888/status/health'
```

<!--Python-->

```python
import requests

url = "http://localhost:8888/status/health"

payload={}
headers = {}

response = requests.request("GET", url, headers=headers, data=payload)

print(response.text)
```

<!--END_DOCUSAURUS_CODE_TABS-->

#### Sample response

```
true
```

You can learn more about similar endpoints in [Process information](./../operations/api-reference.md#process-information).

## Ingest data

There are different ways to ingest data based on what your needs are. For more information, see [Ingestion methods](../ingestion/index.md).

This tutorial uses the multi-stage query (MSQ) task engine and its `sql/task` endpoint to perform SQL-based ingestion. To learn more about SQL-based ingestion, see [SQL-based ingestion](./../multi-stage-query/index.md). For information about the endpoint specifically, see [SQL-based ingestion and multi-stage query task API](./../multi-stage-query/api.md).

<!--DOCUSAURUS_CODE_TABS-->

<!--HTTP-->

```
POST /druid/v2/sql/task
```

```json
{
  "query": "INSERT INTO wikipedia\nSELECT\n  TIME_PARSE(\"timestamp\") AS __time,\n  *\nFROM TABLE(\n  EXTERN(\n    '{\"type\": \"http\", \"uris\": [\"https://druid.apache.org/data/wikipedia.json.gz\"]}',\n    '{\"type\": \"json\"}',\n    '[{\"name\": \"added\", \"type\": \"long\"}, {\"name\": \"channel\", \"type\": \"string\"}, {\"name\": \"cityName\", \"type\": \"string\"}, {\"name\": \"comment\", \"type\": \"string\"}, {\"name\": \"commentLength\", \"type\": \"long\"}, {\"name\": \"countryIsoCode\", \"type\": \"string\"}, {\"name\": \"countryName\", \"type\": \"string\"}, {\"name\": \"deleted\", \"type\": \"long\"}, {\"name\": \"delta\", \"type\": \"long\"}, {\"name\": \"deltaBucket\", \"type\": \"string\"}, {\"name\": \"diffUrl\", \"type\": \"string\"}, {\"name\": \"flags\", \"type\": \"string\"}, {\"name\": \"isAnonymous\", \"type\": \"string\"}, {\"name\": \"isMinor\", \"type\": \"string\"}, {\"name\": \"isNew\", \"type\": \"string\"}, {\"name\": \"isRobot\", \"type\": \"string\"}, {\"name\": \"isUnpatrolled\", \"type\": \"string\"}, {\"name\": \"metroCode\", \"type\": \"string\"}, {\"name\": \"namespace\", \"type\": \"string\"}, {\"name\": \"page\", \"type\": \"string\"}, {\"name\": \"regionIsoCode\", \"type\": \"string\"}, {\"name\": \"regionName\", \"type\": \"string\"}, {\"name\": \"timestamp\", \"type\": \"string\"}, {\"name\": \"user\", \"type\": \"string\"}]'\n  )\n)\nPARTITIONED BY DAY",
  "context": {
    "maxNumTasks": 3
  }
}
```

<!--curl-->

```bash
# Make sure you replace `username`, `password`, `your-instance`, and `port` with the values for your deployment.
curl --location --request POST 'http://localhost:8888/druid/v2/sql/task/' \
  --header 'Content-Type: application/json' \
  --data-raw '{
    "query": "INSERT INTO wikipedia\nSELECT\n  TIME_PARSE(\"timestamp\") AS __time,\n  *\nFROM TABLE(\n  EXTERN(\n    '\''{\"type\": \"http\", \"uris\": [\"https://druid.apache.org/data/wikipedia.json.gz\"]}'\'',\n    '\''{\"type\": \"json\"}'\'',\n    '\''[{\"name\": \"added\", \"type\": \"long\"}, {\"name\": \"channel\", \"type\": \"string\"}, {\"name\": \"cityName\", \"type\": \"string\"}, {\"name\": \"comment\", \"type\": \"string\"}, {\"name\": \"commentLength\", \"type\": \"long\"}, {\"name\": \"countryIsoCode\", \"type\": \"string\"}, {\"name\": \"countryName\", \"type\": \"string\"}, {\"name\": \"deleted\", \"type\": \"long\"}, {\"name\": \"delta\", \"type\": \"long\"}, {\"name\": \"deltaBucket\", \"type\": \"string\"}, {\"name\": \"diffUrl\", \"type\": \"string\"}, {\"name\": \"flags\", \"type\": \"string\"}, {\"name\": \"isAnonymous\", \"type\": \"string\"}, {\"name\": \"isMinor\", \"type\": \"string\"}, {\"name\": \"isNew\", \"type\": \"string\"}, {\"name\": \"isRobot\", \"type\": \"string\"}, {\"name\": \"isUnpatrolled\", \"type\": \"string\"}, {\"name\": \"metroCode\", \"type\": \"string\"}, {\"name\": \"namespace\", \"type\": \"string\"}, {\"name\": \"page\", \"type\": \"string\"}, {\"name\": \"regionIsoCode\", \"type\": \"string\"}, {\"name\": \"regionName\", \"type\": \"string\"}, {\"name\": \"timestamp\", \"type\": \"string\"}, {\"name\": \"user\", \"type\": \"string\"}]'\''\n  )\n)\nPARTITIONED BY DAY",
    "context": {
        "maxNumTasks": 3
    }
  }'
```

<!--Python-->

```python
import json
import requests

url = "http://localhost:8888/druid/v2/sql/task/"

payload = json.dumps({
  "query": "INSERT INTO wikipedia\nSELECT\n  TIME_PARSE(\"timestamp\") AS __time,\n  *\nFROM TABLE(\n  EXTERN(\n    '{\"type\": \"http\", \"uris\": [\"https://druid.apache.org/data/wikipedia.json.gz\"]}',\n    '{\"type\": \"json\"}',\n    '[{\"name\": \"added\", \"type\": \"long\"}, {\"name\": \"channel\", \"type\": \"string\"}, {\"name\": \"cityName\", \"type\": \"string\"}, {\"name\": \"comment\", \"type\": \"string\"}, {\"name\": \"commentLength\", \"type\": \"long\"}, {\"name\": \"countryIsoCode\", \"type\": \"string\"}, {\"name\": \"countryName\", \"type\": \"string\"}, {\"name\": \"deleted\", \"type\": \"long\"}, {\"name\": \"delta\", \"type\": \"long\"}, {\"name\": \"deltaBucket\", \"type\": \"string\"}, {\"name\": \"diffUrl\", \"type\": \"string\"}, {\"name\": \"flags\", \"type\": \"string\"}, {\"name\": \"isAnonymous\", \"type\": \"string\"}, {\"name\": \"isMinor\", \"type\": \"string\"}, {\"name\": \"isNew\", \"type\": \"string\"}, {\"name\": \"isRobot\", \"type\": \"string\"}, {\"name\": \"isUnpatrolled\", \"type\": \"string\"}, {\"name\": \"metroCode\", \"type\": \"string\"}, {\"name\": \"namespace\", \"type\": \"string\"}, {\"name\": \"page\", \"type\": \"string\"}, {\"name\": \"regionIsoCode\", \"type\": \"string\"}, {\"name\": \"regionName\", \"type\": \"string\"}, {\"name\": \"timestamp\", \"type\": \"string\"}, {\"name\": \"user\", \"type\": \"string\"}]'\n  )\n)\nPARTITIONED BY DAY",
  "context": {
    "maxNumTasks": 3
  }
})
headers = {
  'Content-Type': 'application/json'
}

response = requests.request("POST", url, headers=headers, data=payload)

print(response.text)

```

<!--END_DOCUSAURUS_CODE_TABS-->


#### Response

```json
{
  "taskId": "query-3681dd02-d136-477e-b2d7-ada7765d7e7c",
  "state": "RUNNING",
}
```

**Response fields**

|Field|Description|
|-----|-----------|
| taskId | Controller task ID. You can use Druid's standard [task APIs](../operations/api-reference.md#overlord) to interact with this controller task.|
| state | Initial state for the query, which is "RUNNING".|

### Get the status of your upload

```json
{
   "status" : {
      "createdTime" : "2022-10-24T19:43:51.610Z",
      "dataSource" : "wikipedia",
      "duration" : 100795,
      "errorMsg" : null,
      "groupId" : "query-3681dd02-d136-477e-b2d7-ada7765d7e7c",
      "id" : "query-3681dd02-d136-477e-b2d7-ada7765d7e7c",
      "location" : {
         "host" : "localhost",
         "port" : 8100,
         "tlsPort" : -1
      },
      "queueInsertionTime" : "1970-01-01T00:00:00.000Z",
      "runnerStatusCode" : "WAITING",
      "status" : "SUCCESS",
      "statusCode" : "SUCCESS",
      "type" : "query_controller"
   },
   "task" : "query-3681dd02-d136-477e-b2d7-ada7765d7e7c"
}
```

### Verify your datasources

You can verify that the data is ingested using `GET /coordinator/v1/datasources.` 

## Querying data



## Managing data