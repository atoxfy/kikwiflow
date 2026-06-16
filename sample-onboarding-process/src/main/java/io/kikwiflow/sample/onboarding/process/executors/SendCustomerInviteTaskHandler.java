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

package io.kikwiflow.sample.onboarding.process.executors;

import io.kikwiflow.execution.api.ExecutionContext;
import io.kikwiflow.execution.api.TaskHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component("sendCustomerInvite")
public class SendCustomerInviteTaskHandler implements TaskHandler {
    private static Logger logger = LogManager.getLogger(SendCustomerInviteTaskHandler.class);

    @Override
    public void handle(ExecutionContext execution) {
        String threadName = Thread.currentThread().getName();
        logger.info("[{}] SendCustomerInviteTaskHandler - Iniciando handle para instância: {}", threadName, execution.getProcessInstanceId());

    }
}
