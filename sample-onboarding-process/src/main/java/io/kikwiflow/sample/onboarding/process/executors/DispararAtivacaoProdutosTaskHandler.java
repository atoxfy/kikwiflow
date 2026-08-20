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
 * Simula o disparo assíncrono de ativação de produtos (ex.: Kafka/API) após a conta ser criada, no fluxo
 * {@code onboarding-scatter-gather.kikwi}. A tarefa seguinte (AGUARDAR_PRODUTOS) é um EXTERNAL_TASK completado
 * manualmente via REST, simulando a confirmação assíncrona vinda de outro sistema.
 */
@Component("dispararAtivacaoProdutosHandler")
public class DispararAtivacaoProdutosTaskHandler implements TaskHandler {

    private static final Logger logger = LogManager.getLogger(DispararAtivacaoProdutosTaskHandler.class);

    @Override
    public void handle(ExecutionContext execution) {
        String threadName = Thread.currentThread().getName();
        logger.info("[{}] DispararAtivacaoProdutosTaskHandler - Iniciando handle para instância: {}", threadName, execution.getProcessInstanceId());

        execution.setVariable("produtosDisparadosEm", new ProcessVariable("produtosDisparadosEm", Instant.now().toString()));
    }
}
