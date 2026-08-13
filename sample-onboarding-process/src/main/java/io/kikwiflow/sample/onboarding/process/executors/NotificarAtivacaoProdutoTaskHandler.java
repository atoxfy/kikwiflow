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
import java.util.Optional;

/**
 * Segundo (e último) nó de {@code product-activation-process.kikwi} — roda inline logo após
 * {@code CRIAR_PRODUTO} suceder (não é {@code commitBefore}), na mesma execução que resumiu a tarefa anterior.
 */
@Component("notificarAtivacaoProdutoTaskHandler")
public class NotificarAtivacaoProdutoTaskHandler implements TaskHandler {

    private static final Logger logger = LogManager.getLogger(NotificarAtivacaoProdutoTaskHandler.class);

    @Override
    public void handle(ExecutionContext execution) {
        String threadName = Thread.currentThread().getName();
        String produto = Optional.ofNullable(execution.getVariable("produto"))
                .map(ProcessVariable::value)
                .map(Object::toString)
                .orElse("desconhecido");

        logger.info("[{}] NotificarAtivacaoProdutoTaskHandler - Notificando cliente sobre ativação de '{}' (instância {})",
                threadName, produto, execution.getProcessInstanceId());

        execution.setVariable("notificadoEm", new ProcessVariable("notificadoEm", Instant.now().toString()));
    }
}
