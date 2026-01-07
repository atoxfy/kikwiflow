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

package io.kikwiflow.rest.autoconfigure;

import io.kikwiflow.management.controller.externaltask.ExternalTaskCommandController;
import io.kikwiflow.management.controller.externaltask.ExternalTaskQueryController;
import io.kikwiflow.management.controller.processdefinition.ProcessDefinitionCommandController;
import io.kikwiflow.management.controller.processdefinition.ProcessDefinitionQueryController;
import io.kikwiflow.management.controller.processinstance.ProcessInstanceCommandController;
import io.kikwiflow.management.controller.processinstance.ProcessInstanceQueryController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnClass(ProcessDefinitionQueryController.class)
@Import({
        ProcessDefinitionQueryController.class,
        ProcessDefinitionCommandController.class,

        ProcessInstanceQueryController.class,
        ProcessInstanceCommandController.class,

        ExternalTaskCommandController.class,
        ExternalTaskQueryController.class
})
public class KikwiRestAutoConfiguration {

}
