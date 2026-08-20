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

import io.kikwiflow.execution.api.context.ExecutionContext;
import io.kikwiflow.model.execution.ProcessVariable;

import java.time.LocalDate;
import java.util.Optional;

public class VariableScope {

    private final ExecutionContext executionContext;
    public static final String TAX_ID = "taxId";
    public static final String NAME = "name";
    public static final String BIRTH_DATE = "birthDate";
    public static final String RISK_SCORE = "riskScore";


    public VariableScope(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    public static VariableScope ofContext(ExecutionContext executionContext){
        return new VariableScope(executionContext);
    }

    public String getTaxId(){
        return Optional.ofNullable(executionContext.getVariable(TAX_ID))
                .map(ProcessVariable::value)
                .map(Object::toString)
                .orElse(null);
    }

    public void setName(String name){
        ProcessVariable pv = new ProcessVariable(NAME, name);
        executionContext.setVariable(NAME, pv);
    }

    public void setBirthDate(LocalDate birthDate){
        ProcessVariable pv = new ProcessVariable(BIRTH_DATE, birthDate);
        executionContext.setVariable(BIRTH_DATE, pv);
    }

    public void setRiskScore(Double riskScore){
        ProcessVariable pv = new ProcessVariable(RISK_SCORE, riskScore);
        executionContext.setVariable(RISK_SCORE, pv);
    }
}
