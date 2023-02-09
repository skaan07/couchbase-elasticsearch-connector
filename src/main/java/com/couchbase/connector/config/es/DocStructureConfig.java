/*
 * Copyright 2018 Couchbase, Inc.
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

package com.couchbase.connector.config.es;

import com.couchbase.connector.config.toml.ConfigTable;
import com.google.common.base.Strings;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
public interface DocStructureConfig {

  @Nullable
  String metadataFieldName();

  boolean documentContentAtTopLevel();

  boolean wrapCounters();

  @Nullable
  String filterFields();

  @Nullable
  String denormalizeFields();

  @Nullable
  String excludeFields();

  static ImmutableDocStructureConfig from(ConfigTable config) {
    config.expectOnly("metadataFieldName", "documentContentAtTopLevel", "wrapCounters", "filterFields", "denormalizeFields", "excludeFields");

    return ImmutableDocStructureConfig.builder()
        .metadataFieldName(Strings.emptyToNull(config.getString("metadataFieldName").orElse(null)))
        .documentContentAtTopLevel(config.getBoolean("documentContentAtTopLevel").orElse(false))
        .wrapCounters(config.getBoolean("wrapCounters").orElse(false))
        .filterFields(Strings.emptyToNull(config.getString("filterFields").orElse(null)))
        .denormalizeFields(Strings.emptyToNull(config.getString("denormalizeFields").orElse(null)))
        .excludeFields(Strings.emptyToNull(config.getString("excludeFields").orElse(null)))
        .build();
  }
}
