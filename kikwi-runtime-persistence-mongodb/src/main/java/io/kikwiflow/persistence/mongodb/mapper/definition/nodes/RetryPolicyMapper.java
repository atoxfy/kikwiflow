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

package io.kikwiflow.persistence.mongodb.mapper.definition.nodes;

import io.kikwiflow.model.definition.process.policies.RetryPolicy;
import io.kikwiflow.model.execution.enumerated.RetryStrategy;
import org.bson.Document;

public class RetryPolicyMapper {

    public static RetryPolicy mapToDefinition(Document document){

        Document policyDoc = document.get("retryPolicy", Document.class);
        RetryPolicy retryPolicy = null;

        if (policyDoc != null) {
            retryPolicy = new RetryPolicy(
                    RetryStrategy.valueOf(policyDoc.getString("strategy")),
                    policyDoc.getInteger("maxRetries"),
                    policyDoc.getString("initialInterval"),
                    policyDoc.getDouble("multiplier"),
                    policyDoc.getString("maxInterval"),
                    policyDoc.getList("intervals", String.class)
            );
        }

        return  retryPolicy;
    }
}
