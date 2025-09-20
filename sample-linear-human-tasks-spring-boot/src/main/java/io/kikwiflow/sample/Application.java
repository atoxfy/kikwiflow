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

package io.kikwiflow.sample;

import io.kikwiflow.KikwiflowEngine;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.InputStream;

@SpringBootApplication
public class Application {
    public static void main(String[] args){
        SpringApplication.run(Application.class, args);
    }

    @Bean
    CommandLineRunner deployProcess(KikwiflowEngine engine){
        return args -> {
            System.out.println("##### Deplpoying process definition...");
            InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("sequential-human-tasks.bpmn");
            engine.deployDefinition(bpmnStream);
            System.out.println("#### Process 'sequential-human-tasks' deployed successfully!");
        };
    }
}
