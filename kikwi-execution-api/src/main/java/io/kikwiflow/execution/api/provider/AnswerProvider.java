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

package io.kikwiflow.execution.api.provider;

import io.kikwiflow.execution.api.context.EvaluationContext;

/**
 * Contrato principal para provedores de decisão.
 */
@FunctionalInterface
public interface AnswerProvider {

    /**
     * Avalia o contexto atual e retorna uma String representando a hipótese de resposta.
     *
     * @param context Contexto imutável da execução.
     * @return A resposta (hipótese) gerada. Retornar nulo é permitido, mas deve ser tratado
     *         explicitamente no modelo do processo.
     */
    String resolve(EvaluationContext context);
}
