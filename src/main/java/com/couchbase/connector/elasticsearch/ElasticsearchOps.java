/*
 * Copyright 2022 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase.connector.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.couchbase.client.dcp.util.Version;
import org.elasticsearch.client.RestClient;

import java.io.Closeable;
import java.io.IOException;

public interface ElasticsearchOps extends Closeable {
  Version version() throws IOException;

  ElasticsearchClient modernClient();

  RestClient lowLevelClient();
}
