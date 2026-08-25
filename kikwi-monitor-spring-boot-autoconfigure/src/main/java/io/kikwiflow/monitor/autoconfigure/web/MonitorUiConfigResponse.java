/*
 * Copyright 2026 Atoxfy and/or licensed to Atoxfy
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Atoxfy licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.kikwiflow.monitor.autoconfigure.web;

/**
 * Body returned by {@code GET /monitor-ui/config.json}. Field names are camelCase on purpose — they are read
 * as-is by the Monitor SPA's runtime config store (no custom Jackson naming strategy is configured anywhere in
 * this repo, so the default serializer already matches what the frontend expects).
 */
public record MonitorUiConfigResponse(
        String apiUrl,
        String oidcAuthority,
        String oidcClientId,
        String oidcRedirectUri,
        boolean requireAuth,
        boolean readOnly
) {
}
