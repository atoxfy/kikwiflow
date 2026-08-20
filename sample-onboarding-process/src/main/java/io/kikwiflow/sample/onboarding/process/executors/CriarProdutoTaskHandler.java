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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Primeiro nó de {@code product-activation-process.kikwi} — o processo filho chamado por
 * {@code onboarding-ativacao-produtos-sequencial.kikwi}/{@code onboarding-ativacao-produtos-paralelo.kikwi}, um
 * por elemento da lista {@code produtos} (via {@code CALL_ACTIVITY_COORDINATOR}, {@code elementVariable:
 * produto}).
 *
 * <p>O produto {@code "seguro-vida"} simula uma instabilidade transiente do core banking: falha na primeira
 * execução e sucede sozinho na retentativa (a {@code RetryPolicy} LINEAR do nó, {@code PT5S}, entra em ação —
 * mesmo padrão de {@code CriarContaCoreBankingTaskHandler}). Como o nó é {@code commitBefore: true}, cada
 * tentativa é uma execução própria da {@code ExecutableTask}, não uma chamada inline. A tentativa já feita é
 * rastreada em memória por {@code processInstanceId} — aqui, crucialmente, é o {@code processInstanceId} do
 * **filho** (cada produto tem sua própria instância, isolada das demais), não o do pai: em modo
 * {@code SEQUENTIAL} isso também demonstra que uma falha travando um elemento não "vaza" nem afeta os
 * elementos vizinhos — só atrasa quando o próximo é iniciado (a iteração sequencial só avança depois que este
 * filho conclui).
 */
@Component("criarProdutoTaskHandler")
public class CriarProdutoTaskHandler implements TaskHandler {

    private static final Logger logger = LogManager.getLogger(CriarProdutoTaskHandler.class);
    public static final String TRANSIENT_FAILURE_PRODUCT = "seguro-vida";

    private final Set<String> instancesThatAlreadyFailedOnce = ConcurrentHashMap.newKeySet();

    @Override
    public void handle(ExecutionContext execution) {
        String threadName = Thread.currentThread().getName();
        String produto = Optional.ofNullable(execution.getVariable("produto"))
                .map(ProcessVariable::value)
                .map(Object::toString)
                .orElse("desconhecido");

        logger.info("[{}] CriarProdutoTaskHandler - Ativando produto '{}' para instância filha: {}",
                threadName, produto, execution.getProcessInstanceId());

        if (TRANSIENT_FAILURE_PRODUCT.equals(produto) && instancesThatAlreadyFailedOnce.add(execution.getProcessInstanceId())) {
            throw new RuntimeException("Instabilidade transitória no core banking ao criar produto '" + produto + "'");
        }

        execution.setVariable("produtoId", new ProcessVariable("produtoId", "PROD-" + UUID.randomUUID()));
    }
}
