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

package org.apache.druid.server.http.security;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.sun.jersey.spi.container.ContainerRequest;
import org.apache.druid.server.security.AuthorizationResult;
import org.apache.druid.server.security.AuthorizationUtils;
import org.apache.druid.server.security.AuthorizerMapper;
import org.apache.druid.server.security.ForbiddenException;
import org.apache.druid.server.security.Resource;
import org.apache.druid.server.security.ResourceAction;
import org.apache.druid.server.security.ResourceType;

import javax.ws.rs.core.PathSegment;
import java.util.List;

/**
 * Use this resource filter for API endpoints that contain {@link #DATASOURCES_PATH_SEGMENT} in their request path.
 */
public class DatasourceResourceFilter extends AbstractResourceFilter
{
  private static final String DATASOURCES_PATH_SEGMENT = "datasources";

  @Inject
  public DatasourceResourceFilter(
      AuthorizerMapper authorizerMapper
  )
  {
    super(authorizerMapper);
  }

  @Override
  public ContainerRequest filter(ContainerRequest request)
  {
    final ResourceAction resourceAction = new ResourceAction(
        new Resource(getRequestDatasourceName(request), ResourceType.DATASOURCE),
        getAction(request)
    );

    final AuthorizationResult authResult = AuthorizationUtils.authorizeResourceAction(
        getReq(),
        resourceAction,
        getAuthorizerMapper()
    );

    if (!authResult.allowAccessWithNoRestriction()) {
      throw new ForbiddenException(authResult.getErrorMessage());
    }

    return request;
  }

  private String getRequestDatasourceName(ContainerRequest request)
  {
    final List<PathSegment> pathSegments = request.getPathSegments();
    final String dataSourceName = pathSegments.get(
        Iterables.indexOf(pathSegments, input -> DATASOURCES_PATH_SEGMENT.equals(input.getPath())) + 1
    ).getPath();

    Preconditions.checkNotNull(dataSourceName);
    return dataSourceName;
  }
}
