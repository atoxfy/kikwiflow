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
package io.kikwiflow.management.exception;

import io.kikwiflow.exception.InvalidProcessDefinitionException;
import io.kikwiflow.exception.VariableValidationException;
import io.kikwiflow.variable.VariableValidationError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KikwiflowExceptionHandlerTest {

    private final KikwiflowExceptionHandler handler = new KikwiflowExceptionHandler();

    @Test
    void variableValidationBecomes422WithFieldErrors() {
        List<VariableValidationError> errors = List.of(
                new VariableValidationError("amount", VariableValidationError.REQUIRED, "Variable 'Valor' is required."));

        ResponseEntity<KikwiflowExceptionHandler.VariableValidationErrorResponse> response =
                handler.handleVariableValidation(new VariableValidationException("REVIEW", errors));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("VARIABLE_VALIDATION_FAILED", response.getBody().code());
        assertEquals("REVIEW", response.getBody().nodeId());
        assertEquals(errors, response.getBody().errors());
    }

    @Test
    void invalidProcessDefinitionBecomes400() {
        ResponseEntity<KikwiflowExceptionHandler.ErrorResponse> response =
                handler.handleInvalidProcessDefinition(new InvalidProcessDefinitionException("bad"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_PROCESS_DEFINITION", response.getBody().code());
    }
}
