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

import io.kikwiflow.execution.api.context.ExecutionContext;
import io.kikwiflow.execution.api.handler.TaskHandler;
import io.kikwiflow.model.execution.ProcessVariable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Roda depois do {@code CALL_ACTIVITY_COORDINATOR} liberar — em ambos
 * {@code onboarding-ativacao-produtos-sequencial.kikwi} e {@code onboarding-ativacao-produtos-paralelo.kikwi}
 * (mesmo handler, compartilhado). Nesse ponto todos os produtos da lista já foram ativados com sucesso — o
 * coordenador só segue seu {@code outgoing} depois que {@code pendingBranchIds}/{@code pendingLoopElements}
 * esvaziam por completo (ver docs/engine/20-subprocessos-call-activity-especificacao.md).
 */
@Component("afterProductActivationTaskHandler")
public class AfterProductActivationTaskHandler implements TaskHandler {

    private static final Logger logger = LogManager.getLogger(AfterProductActivationTaskHandler.class);

    @Override
    public void handle(ExecutionContext execution) {
        String threadName = Thread.currentThread().getName();
        logger.info("[{}] AfterProductActivationTaskHandler - Todos os produtos ativados para instância: {}",
                threadName, execution.getProcessInstanceId());

        execution.setVariable("ativacaoConcluidaEm", new ProcessVariable("ativacaoConcluidaEm", Instant.now().toString()));
    }
}
