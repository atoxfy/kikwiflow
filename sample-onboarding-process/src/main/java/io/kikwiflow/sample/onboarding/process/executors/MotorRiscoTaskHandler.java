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

/**
 * Combina o score do bureau ({@code API_BUREAU}) com os documentos recebidos ({@code UI_UPLOAD}) — as duas
 * ramificações paralelas de {@code onboarding-scatter-gather.kikwi} — para decidir a classificação de risco.
 * O resultado ("APPROVED"/"HIGH_RISK") é lido diretamente pelo {@code onboardingRiskAnswerProvider} no
 * EXCLUSIVE_GATEWAY seguinte.
 */
@Component("motorRiscoHandler")
public class MotorRiscoTaskHandler implements TaskHandler {

    private static final Logger logger = LogManager.getLogger(MotorRiscoTaskHandler.class);
    private static final double APPROVAL_THRESHOLD = 50.0;

    @Override
    public void handle(ExecutionContext execution) {
        String threadName = Thread.currentThread().getName();
        logger.info("[{}] MotorRiscoTaskHandler - Iniciando handle para instância: {}", threadName, execution.getProcessInstanceId());

        double bureauScore = execution.hasVariable("bureauScore")
                ? ((Number) execution.getVariable("bureauScore").value()).doubleValue()
                : 0.0;

        String riskLevel = bureauScore >= APPROVAL_THRESHOLD ? "APPROVED" : "HIGH_RISK";
        execution.setVariable("riskLevel", new ProcessVariable("riskLevel", riskLevel));
        logger.info("[{}] MotorRiscoTaskHandler - bureauScore={} -> riskLevel={}", threadName, bureauScore, riskLevel);
    }
}
