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

import io.kikwiflow.monitor.autoconfigure.KikwiMonitorWebMvcConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Handles the bare {@code /monitor-ui} and {@code /monitor-ui/} paths — Spring's
 * {@code ResourceHttpRequestHandler} rejects an empty sub-path (the request path left over after
 * stripping the {@code /monitor-ui/**} mapping) before ever consulting a resource resolver, so
 * {@link SpaFallbackResourceResolver}'s fallback-to-index-html heuristic never gets a chance to run
 * for these two exact paths. This controller plugs that specific gap with a plain forward.
 */
@Controller
public class MonitorUiRootController {

    @GetMapping({KikwiMonitorWebMvcConfiguration.MONITOR_UI_PATH, KikwiMonitorWebMvcConfiguration.MONITOR_UI_PATH + "/"})
    public String index() {
        return "forward:" + KikwiMonitorWebMvcConfiguration.MONITOR_UI_PATH + "/index.html";
    }
}
