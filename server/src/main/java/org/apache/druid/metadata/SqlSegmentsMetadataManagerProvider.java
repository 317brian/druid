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

package org.apache.druid.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Supplier;
import com.google.inject.Inject;
import org.apache.druid.java.util.common.lifecycle.Lifecycle;
import org.apache.druid.java.util.emitter.service.ServiceEmitter;
import org.apache.druid.metadata.segment.SqlSegmentsMetadataManagerV2;
import org.apache.druid.metadata.segment.cache.SegmentMetadataCache;
import org.apache.druid.segment.metadata.CentralizedDatasourceSchemaConfig;
import org.apache.druid.segment.metadata.SegmentSchemaCache;

public class SqlSegmentsMetadataManagerProvider implements SegmentsMetadataManagerProvider
{
  private final ObjectMapper jsonMapper;
  private final Supplier<SegmentsMetadataManagerConfig> config;
  private final Supplier<MetadataStorageTablesConfig> storageConfig;
  private final SQLMetadataConnector connector;
  private final Lifecycle lifecycle;
  private final ServiceEmitter serviceEmitter;
  private final SegmentSchemaCache segmentSchemaCache;
  private final SegmentMetadataCache segmentMetadataCache;
  private final CentralizedDatasourceSchemaConfig centralizedDatasourceSchemaConfig;

  @Inject
  public SqlSegmentsMetadataManagerProvider(
      SegmentMetadataCache segmentMetadataCache,
      ObjectMapper jsonMapper,
      Supplier<SegmentsMetadataManagerConfig> config,
      Supplier<MetadataStorageTablesConfig> storageConfig,
      SQLMetadataConnector connector,
      Lifecycle lifecycle,
      SegmentSchemaCache segmentSchemaCache,
      CentralizedDatasourceSchemaConfig centralizedDatasourceSchemaConfig,
      ServiceEmitter serviceEmitter
  )
  {
    this.jsonMapper = jsonMapper;
    this.config = config;
    this.storageConfig = storageConfig;
    this.connector = connector;
    this.lifecycle = lifecycle;
    this.serviceEmitter = serviceEmitter;
    this.segmentSchemaCache = segmentSchemaCache;
    this.segmentMetadataCache = segmentMetadataCache;
    this.centralizedDatasourceSchemaConfig = centralizedDatasourceSchemaConfig;
  }

  @Override
  public SegmentsMetadataManager get()
  {
    lifecycle.addHandler(
        new Lifecycle.Handler()
        {
          @Override
          public void start()
          {
            connector.createSegmentSchemasTable();
            connector.createSegmentTable();
            connector.createUpgradeSegmentsTable();
          }

          @Override
          public void stop()
          {

          }
        }
    );

    return new SqlSegmentsMetadataManagerV2(
        segmentMetadataCache,
        segmentSchemaCache,
        connector,
        config,
        storageConfig,
        centralizedDatasourceSchemaConfig,
        serviceEmitter,
        jsonMapper
    );
  }
}
