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

package io.kikwiflow.persistence.mongodb.autoconfigure.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Liga a mesma chave {@code kikwiflow.outbox.events-enabled} já usada por {@code KikwiflowConfig} (módulo
 * {@code kikwi-core}) — este binding independente é necessário porque a camada de persistência MongoDB não
 * depende de {@code kikwi-core}, então não tem acesso a {@code KikwiflowConfig} diretamente.
 */
@ConfigurationProperties(prefix = "kikwiflow.outbox")
public record KikwiflowOutboxProperties(
        @DefaultValue("false") boolean eventsEnabled,
        String ttl
) {

    /**
     * Retenção de {@code outbox_events} em segundos, ou {@code 0} se {@code ttl} não foi declarado (retenção
     * indefinida, comportamento padrão). Aceita duração ISO-8601 (ex.: {@code "P30D"}, {@code "PT720H"}), no
     * mesmo formato já usado em {@code kikwiflow.retry.default-retry-interval} e nos timers do processo.
     */
    public long ttlSeconds() {
        return (ttl == null || ttl.isBlank()) ? 0L : Duration.parse(ttl).getSeconds();
    }
}
