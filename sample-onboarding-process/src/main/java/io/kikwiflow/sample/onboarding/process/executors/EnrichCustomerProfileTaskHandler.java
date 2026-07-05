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

import io.kikwiflow.exception.ProcessErrorException;
import io.kikwiflow.execution.api.ExecutionContext;
import io.kikwiflow.execution.api.TaskHandler;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.sample.onboarding.directory.CustomerDirectory;
import io.kikwiflow.sample.onboarding.process.VariableScope;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class EnrichCustomerProfileTaskHandler implements TaskHandler {

    private static Logger logger = LogManager.getLogger(EnrichCustomerProfileTaskHandler.class);

    private final CustomerDirectory customerDirectory;

    public EnrichCustomerProfileTaskHandler(CustomerDirectory customerDirectory) {
        this.customerDirectory = customerDirectory;
    }

    @Override
    public void handle(ExecutionContext execution) {
        String threadName = Thread.currentThread().getName();
        logger.info("[{}] EnrichCustomerProfileTaskHandler - Iniciando handle para instância: {}", threadName, execution.getProcessInstanceId());
        VariableScope variableScope = VariableScope.ofContext(execution);
        String taxId = variableScope.getTaxId();
        if(taxId.equals("19")){
            throw new ProcessErrorException("CLIENTE_NAO_ENCONTRADO");
        }else if(taxId.equals("20")){
            throw new RuntimeException("Falha grave!");
        }

        customerDirectory.findByTaxId(taxId)
                        .ifPresent(customer -> {
                            variableScope.setName(customer.name());
                            variableScope.setBirthDate(customer.birthDate());
                        });

    }
}
