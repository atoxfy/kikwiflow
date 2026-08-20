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
import io.kikwiflow.sample.onboarding.directory.CustomerDirectory;
import io.kikwiflow.sample.onboarding.process.VariableScope;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Simula a consulta a um bureau de crédito externo dentro do fluxo de scatter-gather
 * ({@code onboarding-scatter-gather.kikwi}) — uma das duas ramificações do PARALLEL_GATEWAY, ao lado de
 * UI_UPLOAD. Dois taxIds acionam os dois caminhos de falha do motor, contrastando incidente técnico com
 * incidente de negócio (nenhum {@code BOUNDARY_ERROR_HANDLER} é usado aqui de propósito: uma ramificação
 * paralela só libera o JOIN_GATEWAY seguinte reexecutando o mesmo nó com sucesso — ver TUTORIAL.md, seção
 * "Um desvio de erro dentro de um branch paralelo nunca libera o join sozinho"):
 * <ul>
 *     <li>taxId "7" simula uma instabilidade técnica do bureau (timeout) — lança uma {@link RuntimeException}
 *     comum. Sem handler de borda para capturá-la, segue o caminho padrão de retry da {@code RetryPolicy} do
 *     nó (EXPONENTIAL_BACKOFF) e, se esgotar, abre um incidente {@code FAILED_JOB}.</li>
 *     <li>taxId "9" simula o bureau fora do ar de forma definitiva (erro de negócio) — lança
 *     {@link ProcessErrorException} com o errorCode "BUREAU_UNAVAILABLE". Como não há nenhum
 *     {@code BOUNDARY_ERROR_HANDLER} anexado a este nó, o erro não é capturado: vira um incidente
 *     {@code UNHANDLED_BUSINESS_ERROR} imediatamente, sem consumir o orçamento de retries — ver
 *     {@code CriarContaCoreBankingTaskHandler} para um exemplo de {@code BOUNDARY_ERROR_HANDLER} capturando de
 *     fato um erro de negócio (fora de qualquer branch paralelo).</li>
 * </ul>
 * Em ambos os casos, corrigir a causa (ex.: trocar o taxId via {@code PUT .../variables}) e chamar
 * {@code PUT /incidents/{id}/retry} reexecuta API_BUREAU normalmente, e a ramificação segue para o JOIN_SYNC.
 */
@Component("bureauCheckHandler")
public class BureauCheckTaskHandler implements TaskHandler {

    private static final Logger logger = LogManager.getLogger(BureauCheckTaskHandler.class);
    public static final String BUREAU_TIMEOUT_TAX_ID = "7";
    public static final String BUREAU_UNAVAILABLE_TAX_ID = "9";

    @Override
    public void handle(ExecutionContext execution) {
        String threadName = Thread.currentThread().getName();
        logger.info("[{}] BureauCheckTaskHandler - Iniciando handle para instância: {}", threadName, execution.getProcessInstanceId());

        VariableScope variableScope = VariableScope.ofContext(execution);
        String taxId = variableScope.getTaxId();

        if (BUREAU_TIMEOUT_TAX_ID.equals(taxId)) {
            throw new RuntimeException("Timeout ao consultar bureau de crédito");
        }
        if (BUREAU_UNAVAILABLE_TAX_ID.equals(taxId)) {
            throw new ProcessErrorException("BUREAU_UNAVAILABLE", "Bureau de crédito indisponível para consulta");
        }

        double bureauScore;
        if (CustomerDirectory.APPROVED_CUSTOMER_TAX_ID.equals(taxId)) {
            bureauScore = 90.0;
        } else if (CustomerDirectory.FRAUD_CUSTOMER_TAX_ID.equals(taxId)) {
            bureauScore = 15.0;
        } else {
            bureauScore = 55.0;
        }

        execution.setVariable("bureauScore", new ProcessVariable("bureauScore", bureauScore));
    }
}
