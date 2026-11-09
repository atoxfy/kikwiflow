/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
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
package io.kikwiflow.persistence.mongodb.mapper;

import org.bson.Document;

/**
 * Leitura numérica tolerante ao tipo BSON gravado. O motor grava Int64/Double, mas documentos regravados por
 * ferramentas externas (driver Node, mongosh, Compass) costumam voltar como Int32 — e {@code Document.getLong}/
 * {@code getDouble} são casts, não conversões: um Int32 em {@code executions} derrubava a aquisição de tasks.
 */
public final class NumberMapper {

    private NumberMapper() {}

    public static Long getLong(Document doc, String key) {
        Number value = doc.get(key, Number.class);
        return value != null ? value.longValue() : null;
    }

    public static Integer getInteger(Document doc, String key) {
        Number value = doc.get(key, Number.class);
        return value != null ? value.intValue() : null;
    }

    public static Double getDouble(Document doc, String key) {
        Number value = doc.get(key, Number.class);
        return value != null ? value.doubleValue() : null;
    }
}
