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

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Falls back to a pre-rendered page for client-side (Next.js App Router) paths that don't match a
 * real static asset — e.g. reloading {@code /monitor-ui/monitor?processId=...} with F5 must still
 * render the process detail screen, not silently land back on the overview.
 *
 * <p><b>Important:</b> unlike a classic single-shell SPA (CRA-style, one generic {@code index.html}
 * for every route), Next.js {@code output: 'export'} pre-renders a <em>separate</em> HTML file per
 * route — {@code /monitor} → {@code monitor.html}, {@code /monitor/advanced} →
 * {@code monitor/advanced.html}, {@code /} → {@code index.html}. Always falling back to
 * {@code index.html} would silently serve the wrong page's initial render for every other route (a
 * real bug caught by manual browser verification, not the curl-only smoke test — F5 on
 * {@code /monitor-ui/monitor?processId=...} rendered the process list instead of the process
 * detail screen). So the fallback order is: (1) the exact static asset, (2) the pre-rendered page
 * matching the request path, (3) Next's own generated not-found page, (4) the app shell
 * ({@code index.html}) as a last-resort baseline.
 *
 * <p>Participates in the same {@link org.springframework.web.servlet.resource.ResourceHttpRequestHandler}
 * chain used for real assets, so it inherits correct content-type/cache/ETag handling for free — preferred
 * over a catch-all {@code @Controller} forward.
 *
 * <p>Step (2)/(3) only trigger when the last path segment has no {@code .} — a plain heuristic to
 * distinguish an SPA route from a genuinely missing/broken asset (e.g. a 404'd {@code .js} chunk), which would
 * otherwise be masked as a false-positive 200.
 */
public class SpaFallbackResourceResolver extends PathResourceResolver {

    private static final String INDEX_HTML = "index.html";
    private static final String NOT_FOUND_HTML = "_not-found.html";

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
        Resource resource = super.getResource(resourcePath, location);
        if (resource != null) {
            return resource;
        }
        if (!looksLikeSpaRoute(resourcePath)) {
            return null;
        }

        Resource pageResource = readableOrNull(location, trimTrailingSlash(resourcePath) + ".html");
        if (pageResource != null) {
            return pageResource;
        }

        Resource notFoundResource = readableOrNull(location, NOT_FOUND_HTML);
        if (notFoundResource != null) {
            return notFoundResource;
        }

        return location.createRelative(INDEX_HTML);
    }

    private Resource readableOrNull(Resource location, String relativePath) throws IOException {
        Resource candidate = location.createRelative(relativePath);
        return (candidate.exists() && candidate.isReadable()) ? candidate : null;
    }

    private String trimTrailingSlash(String resourcePath) {
        return resourcePath.endsWith("/") ? resourcePath.substring(0, resourcePath.length() - 1) : resourcePath;
    }

    private boolean looksLikeSpaRoute(String resourcePath) {
        String lastSegment = resourcePath.contains("/")
                ? resourcePath.substring(resourcePath.lastIndexOf('/') + 1)
                : resourcePath;
        return !lastSegment.contains(".");
    }
}
