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

package io.kikwiflow.model.definition.process.policies;

import java.util.List;

/**
 * Uma chave de correlação construída por encadeamento de segmentos (EVENT_CATCHER, providerType TEMPLATE).
 * Em {@code catchType: STANDALONE}, {@code EventCatcherDefinition.correlationTemplates()} tem exatamente 1
 * entrada; em {@code GROUP}, uma entrada por chave esperada — declarada fixa no {@code .kikwi}, sem depender
 * de nenhuma variável de lista em runtime.
 *
 * @param keySegments segmentos que, concatenados, formam a chave de correlação técnica.
 * @param displayNameSegments segmentos opcionais para o rótulo humano; {@code null}/vazio faz o rótulo cair
 *                             de volta para a própria chave resolvida.
 */
public record CorrelationTemplateDefinition(
        List<CorrelationTemplateSegment> keySegments,
        List<CorrelationTemplateSegment> displayNameSegments
) {}
