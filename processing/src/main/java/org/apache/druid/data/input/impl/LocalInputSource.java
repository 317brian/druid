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

package org.apache.druid.data.input.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.AndFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.druid.data.input.AbstractInputSource;
import org.apache.druid.data.input.InputEntity;
import org.apache.druid.data.input.InputFileAttribute;
import org.apache.druid.data.input.InputFormat;
import org.apache.druid.data.input.InputRowSchema;
import org.apache.druid.data.input.InputSourceReader;
import org.apache.druid.data.input.InputSplit;
import org.apache.druid.data.input.SplitHintSpec;
import org.apache.druid.data.input.impl.systemfield.SystemField;
import org.apache.druid.data.input.impl.systemfield.SystemFieldDecoratorFactory;
import org.apache.druid.data.input.impl.systemfield.SystemFieldInputSource;
import org.apache.druid.data.input.impl.systemfield.SystemFields;
import org.apache.druid.java.util.common.CloseableIterators;
import org.apache.druid.java.util.common.IAE;
import org.apache.druid.java.util.common.logger.Logger;
import org.apache.druid.utils.CollectionUtils;
import org.apache.druid.utils.Streams;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LocalInputSource
    extends AbstractInputSource
    implements SplittableInputSource<List<File>>, SystemFieldInputSource
{
  private static final Logger log = new Logger(LocalInputSource.class);
  public static final String TYPE_KEY = "local";

  @Nullable
  private final File baseDir;
  @Nullable
  private final String filter;
  private final List<File> files;
  private final SystemFields systemFields;

  @JsonCreator
  public LocalInputSource(
      @JsonProperty("baseDir") @Nullable File baseDir,
      @JsonProperty("filter") @Nullable String filter,
      @JsonProperty("files") @Nullable List<File> files,
      @JsonProperty(SYSTEM_FIELDS_PROPERTY) @Nullable SystemFields systemFields
  )
  {
    this.baseDir = baseDir;
    this.filter = baseDir != null ? Preconditions.checkNotNull(filter, "filter") : filter;
    this.files = files == null ? Collections.emptyList() : files;
    this.systemFields = systemFields == null ? SystemFields.none() : systemFields;

    if (baseDir == null && CollectionUtils.isNullOrEmpty(files)) {
      throw new IAE("At least one of baseDir or files should be specified");
    }
  }

  @JsonIgnore
  @Nonnull
  @Override
  public Set<String> getTypes()
  {
    return Collections.singleton(TYPE_KEY);
  }

  public LocalInputSource(File baseDir, String filter)
  {
    this(baseDir, filter, null, SystemFields.none());
  }

  @Nullable
  public File getBaseDir()
  {
    return baseDir;
  }

  /**
   * Returns the base directory for serialization. This is better than returning {@link File} directly, because
   * Jackson serializes {@link File} using {@link File#getAbsolutePath()}, and we'd prefer to not force relative
   * path resolution as part of serialization.
   */
  @Nullable
  @JsonProperty("baseDir")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String getBaseDirForSerialization()
  {
    if (baseDir == null) {
      return null;
    } else {
      return baseDir.getPath();
    }
  }

  @Nullable
  @JsonProperty
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getFilter()
  {
    return filter;
  }

  @Override
  public Set<SystemField> getConfiguredSystemFields()
  {
    return systemFields.getFields();
  }

  public SystemFields getSystemFields()
  {
    return systemFields;
  }

  public List<File> getFiles()
  {
    return files;
  }

  /**
   * Returns the list of file paths for serialization. This is better than returning {@link File} directly, because
   * Jackson serializes {@link File} using {@link File#getAbsolutePath()}, and we'd prefer to not force relative
   * path resolution as part of serialization.
   */
  @JsonProperty("files")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private List<String> getFilesForSerialization()
  {
    return getFiles().stream().map(File::getPath).collect(Collectors.toList());
  }

  @Override
  public Stream<InputSplit<List<File>>> createSplits(InputFormat inputFormat, @Nullable SplitHintSpec splitHintSpec)
  {
    return Streams.sequentialStreamFrom(getSplitFileIterator(inputFormat, getSplitHintSpecOrDefault(splitHintSpec)))
                  .map(InputSplit::new);
  }

  @Override
  public int estimateNumSplits(InputFormat inputFormat, @Nullable SplitHintSpec splitHintSpec)
  {
    return Iterators.size(getSplitFileIterator(inputFormat, getSplitHintSpecOrDefault(splitHintSpec)));
  }

  private Iterator<List<File>> getSplitFileIterator(final InputFormat inputFormat, SplitHintSpec splitHintSpec)
  {
    final Iterator<File> fileIterator = getFileIterator();
    return splitHintSpec.split(
        fileIterator,
        file -> new InputFileAttribute(
            file.length(),
            inputFormat != null
            ? inputFormat.getWeightedSize(file.getName(), file.length())
            : file.length()
        )
    );
  }

  @VisibleForTesting
  Iterator<File> getFileIterator()
  {
    return
        Iterators.filter(
            Iterators.concat(
                getDirectoryListingIterator(),
                getFilesListIterator()
            ),
            file -> file.length() > 0
        );
  }

  private Iterator<File> getDirectoryListingIterator()
  {
    if (baseDir == null) {
      return Collections.emptyIterator();
    } else {
      final IOFileFilter fileFilter;
      if (files == null) {
        fileFilter = new WildcardFileFilter(filter);
      } else {
        fileFilter = new AndFileFilter(
            new WildcardFileFilter(filter),
            new NotFileFilter(
                new NameFileFilter(files.stream().map(File::getName).collect(Collectors.toList()), IOCase.SENSITIVE)
            )
        );
      }
      Iterator<File> fileIterator = FileUtils.iterateFiles(
          baseDir.getAbsoluteFile(),
          fileFilter,
          TrueFileFilter.INSTANCE
      );
      if (!fileIterator.hasNext()) {
        // base dir & filter are guaranteed to be non-null here
        // (by construction and non-null check of baseDir a few lines above):
        log.info("Local inputSource filter [%s] for base dir [%s] did not match any files", filter, baseDir);
      }
      return fileIterator;
    }
  }

  private Iterator<File> getFilesListIterator()
  {
    if (files == null) {
      return Collections.emptyIterator();
    } else {
      return files.iterator();
    }
  }

  @Override
  public SplittableInputSource<List<File>> withSplit(InputSplit<List<File>> split)
  {
    return new LocalInputSource(null, null, split.get(), systemFields);
  }

  @Override
  public Object getSystemFieldValue(InputEntity entity, SystemField field)
  {
    final FileEntity fileEntity = (FileEntity) entity;

    switch (field) {
      case URI:
        return fileEntity.getUri().toString();
      case PATH:
        return fileEntity.getFile().getPath();
      default:
        return null;
    }
  }

  @Override
  public boolean needsFormat()
  {
    return true;
  }

  @Override
  protected InputSourceReader formattableReader(
      InputRowSchema inputRowSchema,
      InputFormat inputFormat,
      @Nullable File temporaryDirectory
  )
  {
    //noinspection ConstantConditions
    return new InputEntityIteratingReader(
        inputRowSchema,
        inputFormat,
        CloseableIterators.withEmptyBaggage(Iterators.transform(getFileIterator(), FileEntity::new)),
        SystemFieldDecoratorFactory.fromInputSource(this),
        temporaryDirectory
    );
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LocalInputSource that = (LocalInputSource) o;
    return Objects.equals(baseDir, that.baseDir)
           && Objects.equals(filter, that.filter)
           && Objects.equals(files, that.files)
           && Objects.equals(systemFields, that.systemFields);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(baseDir, filter, files, systemFields);
  }

  @Override
  public String toString()
  {
    return "LocalInputSource{" +
           "baseDir=\"" + baseDir +
           "\", filter=" + filter +
           ", files=" + files +
           (systemFields.getFields().isEmpty() ? "" : ", systemFields=" + systemFields) +
           "}";
  }
}
