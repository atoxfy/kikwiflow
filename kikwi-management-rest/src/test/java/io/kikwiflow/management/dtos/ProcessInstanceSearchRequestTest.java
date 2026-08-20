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

package io.kikwiflow.management.dtos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code size} é documentado em docs/apis/process-instances/search/api-guide.md como tendo máximo 100, mas até
 * este teste existir nada no código impedia um cliente de pedir qualquer valor. Ver
 * docs/engine/21-revisao-observabilidade-e-performance-monitor.md §4.5.
 */
@DisplayName("Dado um ProcessInstanceSearchRequest")
class ProcessInstanceSearchRequestTest {

    @Test
    @DisplayName("Quando size não é informado, então usa o default de 20")
    void semSizeUsaDefault() {
        assertEquals(20, requestWithSize(null).getOrDefaultSize());
    }

    @Test
    @DisplayName("Quando size está dentro do limite documentado, então usa o valor informado")
    void sizeDentroDoLimiteUsaOValorInformado() {
        assertEquals(50, requestWithSize(50).getOrDefaultSize());
    }

    @Test
    @DisplayName("Quando size excede o máximo documentado (100), então é limitado a 100")
    void sizeAcimaDoMaximoEhClampado() {
        assertEquals(ProcessInstanceSearchRequest.MAX_SIZE, requestWithSize(1_000_000).getOrDefaultSize());
    }

    @Test
    @DisplayName("Quando size é zero ou negativo, então usa o default de 20")
    void sizeNaoPositivoUsaDefault() {
        assertEquals(20, requestWithSize(0).getOrDefaultSize());
        assertEquals(20, requestWithSize(-5).getOrDefaultSize());
    }

    private static ProcessInstanceSearchRequest requestWithSize(Integer size) {
        return new ProcessInstanceSearchRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, size);
    }
}
