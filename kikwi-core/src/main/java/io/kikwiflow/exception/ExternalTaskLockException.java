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

package io.kikwiflow.exception;

/**
 * O worker não tem (ou não tem mais) o lease da tarefa: outro worker a adquiriu, o lease venceu, ou a tarefa
 * é uma missão de agente sendo concluída sem {@code workerId} (docs/engine/31, E2). REST: 409.
 */
public class ExternalTaskLockException extends RuntimeException {

    public ExternalTaskLockException(String message) {
        super(message);
    }
}
