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
package io.kikwiflow.starter.autoconfigure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "kikwiflow")
public class KikwiflowProperties {

    @Value("${spring.application.name:kikwiflow-engine}")
    private String instanceName;

    private final Stats stats = new Stats();
    private final Outbox outbox = new Outbox();
    private final AutoDeploy autoDeploy = new AutoDeploy();
    private final Execution execution = new Execution();
    private final Retry retry = new Retry();
    private final Security security = new Security();

    public Execution getExecution() {
        return execution;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public Stats getStats() {
        return stats;
    }

    public Retry getRetry() {
        return retry;
    }

    public Security getSecurity() {
        return security;
    }

    public AutoDeploy getAutoDeploy() { return autoDeploy; }

    public Outbox getOutbox() {
        return outbox;
    }


    public static class Security {
        private boolean isDeployEnabled;

        public boolean isDeployEnabled() {
            return isDeployEnabled;
        }

        public void setDeployEnabled(boolean deployEnabled) {
            isDeployEnabled = deployEnabled;
        }
    }

    public static class Stats {
        private boolean enabled = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Outbox {
        private boolean eventsEnabled = false;

        public boolean isEventsEnabled() { return eventsEnabled; }
        public void setEventsEnabled(boolean eventsEnabled) { this.eventsEnabled = eventsEnabled; }
    }

    public static class AutoDeploy {
        private boolean enabled = true;
        private String path = "classpath*:processes/**/*.kikwi";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }

    public static class Retry {
        private String defaultRetryInterval;

        private List<String> fatalExceptions = new ArrayList<>();

        public List<String> getFatalExceptions() {
            return fatalExceptions;
        }

        public void setFatalExceptions(List<String> fatalExceptions) {
            this.fatalExceptions = fatalExceptions;
        }

        public String getDefaultRetryInterval() {
            return defaultRetryInterval;
        }

        public void setDefaultRetryInterval(String defaultRetryInterval) {
            this.defaultRetryInterval = defaultRetryInterval;
        }
    }

    public static class Execution {
        private long taskAcquisitionIntervalMillis = 5000L;
        private int taskAcquisitionMaxTasks = 10;
        private int maxConcurrentTasks = 200;
        private int shutdownGracePeriodSeconds = 20;
        private long lockTimeoutMillis = 12;

        public void setTaskAcquisitionIntervalMillis(long taskAcquisitionIntervalMillis) {
            this.taskAcquisitionIntervalMillis = taskAcquisitionIntervalMillis;
        }

        public void setTaskAcquisitionMaxTasks(int taskAcquisitionMaxTasks) {
            this.taskAcquisitionMaxTasks = taskAcquisitionMaxTasks;
        }

        public void setMaxConcurrentTasks(int maxConcurrentTasks) {
            this.maxConcurrentTasks = maxConcurrentTasks;
        }

        public void setShutdownGracePeriodSeconds(int shutdownGracePeriodSeconds) {
            this.shutdownGracePeriodSeconds = shutdownGracePeriodSeconds;
        }

        public void setLockTimeoutMillis(long lockTimeoutMillis) {
            this.lockTimeoutMillis = lockTimeoutMillis;
        }

        public long getTaskAcquisitionIntervalMillis() {
            return taskAcquisitionIntervalMillis;
        }

        public int getTaskAcquisitionMaxTasks() {
            return taskAcquisitionMaxTasks;
        }

        public long getLockTimeoutMillis() {
            return lockTimeoutMillis;
        }

        public int getMaxConcurrentTasks() {
            return maxConcurrentTasks;
        }

        public int getShutdownGracePeriodSeconds() {
            return shutdownGracePeriodSeconds;
        }
    }
}