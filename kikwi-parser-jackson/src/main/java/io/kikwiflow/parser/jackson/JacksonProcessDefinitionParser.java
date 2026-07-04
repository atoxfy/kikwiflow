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

package io.kikwiflow.parser.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kikwiflow.execution.api.ProcessDefinitionParser;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.ProcessDefinitionDeployRequest;

import java.security.MessageDigest;
import java.util.HexFormat;

public class JacksonProcessDefinitionParser implements ProcessDefinitionParser {

    private final ObjectMapper objectMapper;

    public JacksonProcessDefinitionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ProcessDefinitionDeployRequest parse(byte[] processContent) throws Exception {
        return objectMapper.readValue(processContent, ProcessDefinitionDeployRequest.class);
    }

    @Override
    public String calculateChecksum (ProcessDefinitionDeployRequest processDefinitionDeployRequest) throws Exception {
        byte[] content = objectMapper.writeValueAsBytes(processDefinitionDeployRequest);
        String checksum = calculateChecksum(content);
        return checksum;
    }

    @Override
    public ProcessDefinition parse(ProcessDefinitionDeployRequest processContent)  {
        return objectMapper.convertValue(processContent, ProcessDefinition.class);
    }

    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new RuntimeException("Falha ao calcular checksum do processo", e);
        }
    }
}