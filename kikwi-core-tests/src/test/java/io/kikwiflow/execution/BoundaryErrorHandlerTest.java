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

package io.kikwiflow.execution;

import io.kikwiflow.exception.ProcessErrorException;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta {@code BOUNDARY_ERROR_HANDLER}: ao contrário dos boundary events interruptivos (timer/catch
 * event), ele é permitido em cima de um {@code EXECUTABLE_TASK} porque é um try/catch síncrono na mesma call
 * stack (ver comentário em {@code DeployValidator}), não uma interrupção assíncrona vinda de fora. Ele só
 * intercepta {@link ProcessErrorException} — uma {@code RuntimeException} qualquer nunca é vista por ele e
 * segue direto para o caminho padrão de retry/incidente ({@code FailureHandler}).
 * <p>
 * Cobre também um comportamento pouco óbvio do {@code FailureHandler}: uma {@link ProcessErrorException} que
 * NÃO casa com nenhum {@code BOUNDARY_ERROR_HANDLER} anexado (ou para a qual não existe handler algum) vira um
 * incidente {@code UNHANDLED_BUSINESS_ERROR} imediatamente — sem passar pelo orçamento de retries da
 * {@code RetryPolicy} do nó, diferente de uma falha técnica comum.
 */
@DisplayName("Dado um EXECUTABLE_TASK com BOUNDARY_ERROR_HANDLER(s) anexado(s)")
class BoundaryErrorHandlerTest {

    private ExecutableTask fetchPendingTask(TestEngine engine, String processInstanceId) {
        List<ExecutableTask> tasks = engine.repository().findExecutableTasksByProcessInstanceId(processInstanceId);
        assertEquals(1, tasks.size(), "Deveria haver exatamente uma ExecutableTask pendente.");
        return tasks.get(0);
    }

    @Nested
    @DisplayName("Quando o nó tem um handler específico (errorCode=SALDO_INSUFICIENTE) e um catch-all (sem errorCode)")
    class WithSpecificAndCatchAllHandlers {

        private TestEngine buildEngine(AtomicInteger notificarErroCalls) {
            return SingletonsFactory.engine()
                    .withTaskHandler("processarPagamentoTaskHandler", ctx -> {
                        throw new ProcessErrorException((String) ctx.getVariable("errorCodeToThrow").value());
                    })
                    .withTaskHandler("notificarErroTaskHandler", ctx -> notificarErroCalls.incrementAndGet())
                    .build();
        }

        @Test
        @DisplayName("ProcessErrorException com código que casa com o handler específico desvia sincronamente para a saída dele, sem incidente")
        void matchingSpecificErrorCodeRoutesSynchronouslyToItsOutgoing() {
            AtomicInteger notificarErroCalls = new AtomicInteger(0);
            TestEngine engine = buildEngine(notificarErroCalls);
            ProcessDefinition definition = engine.deploy("/processes/boundary-error-handler-executable-task.json");

            ProcessInstance instance = engine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-BOUNDARY-ERR-1")
                    .withVariables(Map.of("errorCodeToThrow", new ProcessVariable("errorCodeToThrow", "SALDO_INSUFICIENTE")))
                    .execute();

            ExecutableTask pagamento = fetchPendingTask(engine, instance.id());
            ProcessInstance afterExecution = engine.engine().executeFromTask(pagamento);

            assertEquals(ProcessInstanceStatus.COMPLETED, afterExecution.status());
            assertTrue(engine.repository().findIncidentsByProcessInstanceId(instance.id()).isEmpty(),
                    "Um erro capturado pelo boundary handler não deveria abrir incidente.");
            assertEquals(0, notificarErroCalls.get(),
                    "A saída do catch-all (NOTIFICAR_ERRO) não deveria ter rodado — o handler específico venceu.");
        }

        @Test
        @DisplayName("ProcessErrorException com código que só casa com o catch-all é interceptada, e commitAfter=true no handler mantém a saída PENDING até um ciclo separado")
        void unmatchedSpecificCodeFallsThroughToTheCatchAllAndRespectsItsOwnCommitAfter() {
            AtomicInteger notificarErroCalls = new AtomicInteger(0);
            TestEngine engine = buildEngine(notificarErroCalls);
            ProcessDefinition definition = engine.deploy("/processes/boundary-error-handler-executable-task.json");

            ProcessInstance instance = engine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-BOUNDARY-ERR-2")
                    .withVariables(Map.of("errorCodeToThrow", new ProcessVariable("errorCodeToThrow", "TIMEOUT_GATEWAY_PAGAMENTO")))
                    .execute();

            ExecutableTask pagamento = fetchPendingTask(engine, instance.id());
            ProcessInstance afterExecution = engine.engine().executeFromTask(pagamento);

            assertEquals(ProcessInstanceStatus.ACTIVE, afterExecution.status(),
                    "O processo não deveria concluir ainda — commitAfter=true no catch-all deixa NOTIFICAR_ERRO PENDING em vez de rodar inline.");
            assertEquals(0, notificarErroCalls.get(), "NOTIFICAR_ERRO não deveria ter rodado inline.");
            assertTrue(engine.repository().findIncidentsByProcessInstanceId(instance.id()).isEmpty(),
                    "Um erro capturado pelo catch-all também não deveria abrir incidente.");

            ExecutableTask notificarErro = fetchPendingTask(engine, instance.id());
            assertEquals("NOTIFICAR_ERRO", notificarErro.taskDefinitionId());

            ProcessInstance afterNotification = engine.engine().executeFromTask(notificarErro);

            assertEquals(1, notificarErroCalls.get(), "Agora sim, num ciclo separado.");
            assertEquals(ProcessInstanceStatus.COMPLETED, afterNotification.status());
        }

        @Test
        @DisplayName("Uma RuntimeException comum (não ProcessErrorException) nunca é vista pelos boundary handlers, nem mesmo pelo catch-all")
        void aPlainRuntimeExceptionBypassesEveryBoundaryHandlerIncludingTheCatchAll() {
            TestEngine engine = SingletonsFactory.engine()
                    .withTaskHandler("processarPagamentoTaskHandler", ctx -> {
                        throw new RuntimeException("Gateway de pagamento indisponível");
                    })
                    .withTaskHandler("notificarErroTaskHandler", ctx -> {})
                    .build();
            ProcessDefinition definition = engine.deploy("/processes/boundary-error-handler-executable-task.json");

            ProcessInstance instance = engine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-BOUNDARY-ERR-3")
                    .execute();

            ExecutableTask pagamento = fetchPendingTask(engine, instance.id());
            engine.engine().executeFromTask(pagamento);

            ExecutableTask afterFailure = fetchPendingTask(engine, instance.id());
            assertEquals("PAGAMENTO", afterFailure.taskDefinitionId(),
                    "Deveria continuar sendo a própria tarefa PAGAMENTO reagendada — nenhum boundary handler a interceptou.");
            assertEquals(ExecutableTaskStatus.PENDING, afterFailure.status());
            assertEquals(1L, afterFailure.retries(), "Deveria ter consumido um retry do orçamento normal (maxRetries=2), como qualquer falha técnica.");
            assertTrue(engine.repository().findIncidentsByProcessInstanceId(instance.id()).isEmpty());
        }
    }

    @Nested
    @DisplayName("Quando o único handler anexado é específico e a exceção lançada tem um errorCode diferente")
    class WithOnlyAMismatchedSpecificHandler {

        @Test
        @DisplayName("Vira incidente UNHANDLED_BUSINESS_ERROR imediatamente, sem consumir o orçamento de retries da RetryPolicy do nó")
        void skipsTheRetryBudgetEntirelyAndOpensAnIncidentRightAway() {
            TestEngine engine = SingletonsFactory.engine()
                    .withTaskHandler("pagamentoSemCatchAllTaskHandler", ctx -> {
                        throw new ProcessErrorException("CARTAO_EXPIRADO");
                    })
                    .build();
            ProcessDefinition definition = engine.deploy("/processes/boundary-error-handler-unmatched-becomes-incident.json");

            ProcessInstance instance = engine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-BOUNDARY-ERR-4")
                    .execute();

            ExecutableTask pagamento = fetchPendingTask(engine, instance.id());
            ProcessInstance afterExecution = engine.engine().executeFromTask(pagamento);

            assertEquals(ProcessInstanceStatus.ACTIVE, afterExecution.status());

            ExecutableTask failedTask = fetchPendingTask(engine, instance.id());
            assertEquals(ExecutableTaskStatus.ERROR, failedTask.status());
            assertEquals(0L, failedTask.retries(),
                    "Retries deveria ter ido direto a zero — uma ProcessErrorException não tratada não é reagendada como uma falha técnica comum.");

            List<Incident> incidents = engine.repository().findIncidentsByProcessInstanceId(instance.id());
            assertEquals(1, incidents.size());
            Incident incident = incidents.get(0);
            assertEquals(IncidentStatus.OPEN, incident.status());
            assertEquals("UNHANDLED_BUSINESS_ERROR", incident.type(),
                    "O tipo do incidente deveria distinguir um erro de negócio não tratado de uma falha técnica (FAILED_JOB).");
        }
    }
}
