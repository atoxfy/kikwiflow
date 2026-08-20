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

package io.kikwiflow.sample.onboarding.process;

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de integração **real** (não em memória) para {@code onboarding-ativacao-produtos-sequencial.kikwi} e
 * {@code onboarding-ativacao-produtos-paralelo.kikwi} — sobe o contexto Spring Boot inteiro
 * (auto-deploy dos `.kikwi`, {@link KikwiflowEngine} de verdade, MongoDB Atlas configurado em
 * {@code application.yml}, {@code TaskAcquirer} rodando em background) e dirige os dois processos de ponta a
 * ponta pela API Java, sem HTTP.
 *
 * <p>Complementa — não duplica — a suíte determinística e em memória de
 * {@code CallActivityCoordinatorTest$SequentialIteration} (`kikwi-core-tests`), que já exercita
 * exaustivamente a ordem estrita de disparo/branch-pull do modo {@code SEQUENTIAL} sem depender de tempo real.
 * O valor único deste teste é de **fiação de produção**: confirma que estes dois `.kikwi` específicos
 * (a) passam {@code DeployValidator} e sobrevivem ao round-trip real do MongoDB — o mapper de
 * {@code CallActivityDefinition} é recente, exatamente a lacuna que este teste teria pego se ainda existisse
 * —, (b) todos os beans de {@code TaskHandler} referenciados resolvem no contexto Spring real, e (c) o retry
 * autocurável de {@code CriarProdutoTaskHandler} funciona contra o agendamento assíncrono real (não um
 * avanço manual de relógio).
 *
 * <p><b>Importante sobre como "conclusão" é observada aqui</b>: uma {@code ProcessInstance} concluída é
 * **removida** da coleção ativa, não atualizada para {@code status: COMPLETED} e mantida consultável (ver o
 * próprio comentário de {@code AssertableKikwiEngine.assertThatProcessInstanceIsCompleted}, `kikwi-core-testing`:
 * "uma instância completa é removida da coleção ativa"). Por isso este teste trata
 * {@code findProcessInstanceById} retornar vazio como o sinal de conclusão — não um corpo com status
 * {@code COMPLETED} — e não tenta ler variáveis finais de uma instância já concluída (nem do pai, nem dos
 * filhos); a prova de que o autocura funcionou é a **ausência de incidentes**, não uma variável.
 *
 * <p>Por depender de tempo real e do acquirer (~1s de intervalo,
 * {@code kikwiflow.execution.task-acquisition-interval-millis}), é lento (~1-2min no total) — não recomendado
 * para rodar em loop apertado de edição.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProductActivationCallActivityTest {

    private static final List<String> PRODUTOS = List.of("conta-corrente", "cartao-credito", "seguro-vida");
    private static final Duration POLL_INTERVAL = Duration.ofMillis(400);

    @Autowired
    private KikwiflowEngine engine;

    @Autowired
    private QueryRepository queryRepository;

    @Test
    @DisplayName("onboarding-ativacao-produtos-sequencial: ativa os 3 produtos um de cada vez, incluindo autocura de retry, e completa sem incidentes")
    void sequentialActivationCompletesAllProductsIncludingSelfHealingRetry() {
        runScenarioAndAssertSuccess("onboarding-ativacao-produtos-sequencial", "IT-SEQ-", Duration.ofSeconds(90));
    }

    @Test
    @DisplayName("onboarding-ativacao-produtos-paralelo: ativa os 3 produtos de uma vez, incluindo autocura de retry, e completa sem incidentes")
    void parallelActivationCompletesAllProducts() {
        runScenarioAndAssertSuccess("onboarding-ativacao-produtos-paralelo", "IT-PAR-", Duration.ofSeconds(60));
    }

    private void runScenarioAndAssertSuccess(String processDefinitionKey, String businessKeyPrefix, Duration timeout) {
        String businessKey = businessKeyPrefix + UUID.randomUUID();
        List<String> childBusinessKeys = List.of(
                businessKey + "#0", businessKey + "#1", businessKey + "#2");

        ProcessInstance parent = engine.startProcess()
                .byKey(processDefinitionKey)
                .withBusinessKey(businessKey)
                .onTenant("integration-test")
                .withVariables(Map.of("produtos", new ProcessVariable("produtos", PRODUTOS)))
                .execute();

        Set<String> observedChildIds = new HashSet<>();
        waitForParentCompletion(parent.id(), childBusinessKeys, observedChildIds, timeout);

        assertEquals(3, observedChildIds.size(),
                "Deveria ter observado exatamente 3 instâncias filhas (uma por produto) ao longo da execução — "
                        + "encontradas: " + observedChildIds);

        assertTrue(queryRepository.findIncidentsByProcessInstanceId(parent.id()).isEmpty(),
                "O pai não deveria ter nenhum incidente ao final de uma execução bem-sucedida.");
        observedChildIds.forEach(childId ->
                assertTrue(queryRepository.findIncidentsByProcessInstanceId(childId).isEmpty(),
                        "Filho " + childId + " não deveria ter incidentes — o retry autocurável de 'seguro-vida' "
                                + "(LINEAR, PT5S, maxRetries=2) deveria ter sucedido antes de esgotar o orçamento."));
    }

    /**
     * Faz varredura contínua por {@code childBusinessKeys} (acumulando em {@code observedChildIds} — os filhos
     * somem da coleção ativa assim que concluem, então só uma varredura contínua garante ver os 3) até que o
     * **pai** deixe de ser encontrável (o sinal de que ele mesmo concluiu, ver Javadoc da classe).
     */
    private void waitForParentCompletion(String parentId, List<String> childBusinessKeys,
                                          Set<String> observedChildIds, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            collectObservedChildren(childBusinessKeys, observedChildIds);

            if (queryRepository.findProcessInstanceById(parentId).isEmpty()) {
                collectObservedChildren(childBusinessKeys, observedChildIds); // última varredura, por segurança
                return;
            }
            sleep(POLL_INTERVAL);
        }
        throw new AssertionError("Timeout (" + timeout + ") esperando " + parentId
                + " concluir — filhos observados até agora: " + observedChildIds);
    }

    private void collectObservedChildren(List<String> childBusinessKeys, Set<String> observedChildIds) {
        queryRepository.createProcessInstanceQuery()
                .businessKeyIn(childBusinessKeys)
                .size(10)
                .listSummary()
                .content()
                .forEach(child -> observedChildIds.add(child.id()));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
