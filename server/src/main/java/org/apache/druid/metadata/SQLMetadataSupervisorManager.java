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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.apache.druid.guice.ManageLifecycle;
import org.apache.druid.guice.annotations.Json;
import org.apache.druid.indexing.overlord.supervisor.NoopSupervisorSpec;
import org.apache.druid.indexing.overlord.supervisor.SupervisorSpec;
import org.apache.druid.indexing.overlord.supervisor.VersionedSupervisorSpec;
import org.apache.druid.java.util.common.DateTimes;
import org.apache.druid.java.util.common.Pair;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.java.util.common.lifecycle.LifecycleStart;
import org.apache.druid.java.util.common.logger.Logger;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.Folder3;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.PreparedBatch;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@ManageLifecycle
public class SQLMetadataSupervisorManager implements MetadataSupervisorManager
{
  private static final Logger log = new Logger(SQLMetadataSupervisorManager.class);

  private final ObjectMapper jsonMapper;
  private final SQLMetadataConnector connector;
  private final Supplier<MetadataStorageTablesConfig> dbTables;
  private final IDBI dbi;

  @Inject
  public SQLMetadataSupervisorManager(
      @Json ObjectMapper jsonMapper,
      SQLMetadataConnector connector,
      Supplier<MetadataStorageTablesConfig> dbTables
  )
  {
    this.jsonMapper = jsonMapper;
    this.connector = connector;
    this.dbTables = dbTables;
    this.dbi = connector.getDBI();
  }

  @Override
  @LifecycleStart
  public void start()
  {
    connector.createSupervisorsTable();
  }

  @Override
  public void insert(final String id, final SupervisorSpec spec)
  {
    dbi.withHandle(
        handle -> {
          handle.createStatement(
              StringUtils.format(
                  "INSERT INTO %s (spec_id, created_date, payload) VALUES (:spec_id, :created_date, :payload)",
                  getSupervisorsTable()
              )
          )
                .bind("spec_id", id)
                .bind("created_date", DateTimes.nowUtc().toString())
                .bind("payload", jsonMapper.writeValueAsBytes(spec))
                .execute();

          return null;
        }
    );
  }

  @Override
  public Map<String, List<VersionedSupervisorSpec>> getAll()
  {
    return ImmutableMap.copyOf(
        dbi.withHandle(
            (HandleCallback<Map<String, List<VersionedSupervisorSpec>>>) handle -> handle.createQuery(
                StringUtils.format(
                    "SELECT id, spec_id, created_date, payload FROM %1$s ORDER BY id DESC",
                    getSupervisorsTable()
                )
            ).map(
                (index, r, ctx) -> Pair.of(
                    r.getString("spec_id"),
                    createVersionSupervisorSpecFromResponse(r)
                )
            ).fold(
                new HashMap<>(),
                (Folder3<Map<String, List<VersionedSupervisorSpec>>, Pair<String, VersionedSupervisorSpec>>) (retVal, pair, foldController, statementContext) -> {
                  try {
                    String specId = pair.lhs;
                    retVal.computeIfAbsent(specId, sId -> new ArrayList<>()).add(pair.rhs);
                    return retVal;
                  }
                  catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }
            )
        )
    );
  }

  @Override
  public List<VersionedSupervisorSpec> getAllForId(String id)
  {
    return ImmutableList.copyOf(
        dbi.withHandle(
            (HandleCallback<List<VersionedSupervisorSpec>>) handle -> handle.createQuery(
                StringUtils.format(
                    "SELECT id, spec_id, created_date, payload FROM %1$s WHERE spec_id = :spec_id ORDER BY id DESC",
                    getSupervisorsTable()
                )
            )
            .bind("spec_id", id)
            .map((index, r, ctx) -> createVersionSupervisorSpecFromResponse(r))
            .list()
        )
    );
  }

  private VersionedSupervisorSpec createVersionSupervisorSpecFromResponse(ResultSet r) throws SQLException
  {
    SupervisorSpec payload;
    try {
      payload = jsonMapper.readValue(r.getBytes("payload"), SupervisorSpec.class);
    }
    catch (JsonParseException | JsonMappingException e) {
      log.warn("Failed to deserialize payload for spec_id[%s]", r.getString("spec_id"));
      payload = null;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return new VersionedSupervisorSpec(payload, r.getString("created_date"));
  }

  @Override
  public Map<String, SupervisorSpec> getLatest()
  {
    return ImmutableMap.copyOf(
        dbi.withHandle(
            (HandleCallback<Map<String, SupervisorSpec>>) handle -> handle.createQuery(
                StringUtils.format(
                    "SELECT r.spec_id, r.payload "
                    + "FROM %1$s r "
                    + "INNER JOIN(SELECT spec_id, max(id) as id FROM %1$s GROUP BY spec_id) latest "
                    + "ON r.id = latest.id",
                    getSupervisorsTable()
                )
            ).map(
                new ResultSetMapper<Pair<String, SupervisorSpec>>()
                {
                  @Nullable
                  @Override
                  public Pair<String, SupervisorSpec> map(int index, ResultSet r, StatementContext ctx)
                      throws SQLException
                  {
                    try {
                      return Pair.of(
                          r.getString("spec_id"),
                          jsonMapper.readValue(r.getBytes("payload"), SupervisorSpec.class)
                      );
                    }
                    catch (IOException e) {
                      String exceptionMessage = StringUtils.format(
                          "Could not map json payload to a SupervisorSpec for spec_id: [%s]."
                          + " Delete the supervisor from the table[%s] in the database and re-submit it to the overlord.",
                          r.getString("spec_id"),
                          getSupervisorsTable()
                      );
                      log.error(e, exceptionMessage);
                      return null;
                    }
                  }
                }
            ).fold(
                new HashMap<>(),
                (Folder3<Map<String, SupervisorSpec>, Pair<String, SupervisorSpec>>) (retVal, stringObjectMap, foldController, statementContext) -> {
                  try {
                    if (null != stringObjectMap) {
                      retVal.put(stringObjectMap.lhs, stringObjectMap.rhs);
                    }
                    return retVal;
                  }
                  catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }
            )
        )
    );
  }

  @Override
  public Map<String, SupervisorSpec> getLatestActiveOnly()
  {
    final Map<String, SupervisorSpec> supervisors = getLatest();
    final Map<String, SupervisorSpec> activeSupervisors = new HashMap<>();
    for (Map.Entry<String, SupervisorSpec> entry : supervisors.entrySet()) {
      // Terminated supervisor will have its latest supervisorSpec as NoopSupervisorSpec
      // (NoopSupervisorSpec is used as a tombstone marker)
      if (!(entry.getValue() instanceof NoopSupervisorSpec)) {
        activeSupervisors.put(entry.getKey(), entry.getValue());
      }
    }
    return ImmutableMap.copyOf(activeSupervisors);
  }

  @Override
  public Map<String, SupervisorSpec> getLatestTerminatedOnly()
  {
    final Map<String, SupervisorSpec> supervisors = getLatest();
    final Map<String, SupervisorSpec> terminatedSupervisors = new HashMap<>();
    for (Map.Entry<String, SupervisorSpec> entry : supervisors.entrySet()) {
      // Terminated supervisor will have its latest supervisorSpec as NoopSupervisorSpec
      // (NoopSupervisorSpec is used as a tombstone marker)
      if (entry.getValue() instanceof NoopSupervisorSpec) {
        terminatedSupervisors.put(entry.getKey(), entry.getValue());
      }
    }
    return ImmutableMap.copyOf(terminatedSupervisors);
  }

  @Override
  public int removeTerminatedSupervisorsOlderThan(long timestamp)
  {
    DateTime dateTime = DateTimes.utc(timestamp);
    Map<String, SupervisorSpec> terminatedSupervisors = getLatestTerminatedOnly();
    return dbi.withHandle(
        handle -> {
          final PreparedBatch batch = handle.prepareBatch(
              StringUtils.format(
                  "DELETE FROM %1$s WHERE spec_id = :spec_id AND created_date < '%2$s'",
                  getSupervisorsTable(),
                  dateTime.toString()
              )
          );
          for (Map.Entry<String, SupervisorSpec> supervisor : terminatedSupervisors.entrySet()) {
            batch.bind("spec_id", supervisor.getKey()).add();
          }
          int[] result = batch.execute();
          return IntStream.of(result).sum();
        }
    );
  }

  private String getSupervisorsTable()
  {
    return dbTables.get().getSupervisorTable();
  }
}
