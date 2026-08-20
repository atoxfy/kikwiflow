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

import io.kikwiflow.exception.ProcessErrorException;
import io.kikwiflow.execution.api.context.ExecutionContext;
import io.kikwiflow.execution.api.handler.TaskHandler;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.sample.onboarding.process.VariableScope;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cria a conta no core banking ao final do fluxo de scatter-gather ({@code onboarding-scatter-gather.kikwi}).
 * Dois taxIds acionam os dois caminhos de falha do nó, agora fora de qualquer ramificação paralela (o nó só é
 * alcançado depois do JOIN_SYNC, então nenhum dos dois interage com o fan-out/fan-in):
 * <ul>
 *     <li>taxId "6" simula um documento cadastral inválido — erro de negócio, capturado sincronamente pelo
 *     {@code BOUNDARY_ERROR_HANDLER} ERROR_DOCUMENTO_INVALIDO anexado a CREATE_ACCOUNT, que desvia para
 *     END_EVENT_REJECTED sem consumir o orçamento de retries.</li>
 *     <li>taxId "8" simula uma instabilidade transiente: falha na primeira execução e sucede na segunda,
 *     demonstrando a {@code RetryPolicy} (LINEAR) declarada no nó sendo honrada de fato — o nó é
 *     {@code commitBefore: true}, então cada tentativa é uma execução própria da {@code ExecutableTask}, não
 *     uma chamada inline dentro da tarefa que resumiu o fluxo.</li>
 * </ul>
 * A tentativa já feita (para o caso do taxId "8") é rastreada em memória (por {@code processInstanceId}), não
 * em uma variável de processo: uma execução que termina em exceção nunca é persistida (o {@code FailureHandler}
 * só grava o reagendamento da tarefa e o incidente, não o {@code ExecutionContext} da tentativa que falhou) —
 * uma variável setada antes do {@code throw} seria perdida e a próxima tentativa falharia de novo, para sempre.
 */
@Component("criarContaCoreBanking")
public class CriarContaCoreBankingTaskHandler implements TaskHandler {

    private static final Logger logger = LogManager.getLogger(CriarContaCoreBankingTaskHandler.class);
    public static final String INVALID_DOCUMENT_TAX_ID = "6";
    public static final String TRANSIENT_FAILURE_TAX_ID = "8";

    private final Set<String> instancesThatAlreadyFailedOnce = ConcurrentHashMap.newKeySet();

    @Override
    public void handle(ExecutionContext execution) {
        String threadName = Thread.currentThread().getName();
        logger.info("[{}] CriarContaCoreBankingTaskHandler - Iniciando handle para instância: {}", threadName, execution.getProcessInstanceId());

        VariableScope variableScope = VariableScope.ofContext(execution);
        String taxId = variableScope.getTaxId();

        if (INVALID_DOCUMENT_TAX_ID.equals(taxId)) {
            throw new ProcessErrorException("DOCUMENTO_INVALIDO", "Documento cadastral do cliente é inválido");
        }
        if (TRANSIENT_FAILURE_TAX_ID.equals(taxId) && instancesThatAlreadyFailedOnce.add(execution.getProcessInstanceId())) {
            throw new RuntimeException("Instabilidade transitória no core banking");
        }

        execution.setVariable("accountId", new ProcessVariable("accountId", "ACC-" + UUID.randomUUID()));
    }
}
