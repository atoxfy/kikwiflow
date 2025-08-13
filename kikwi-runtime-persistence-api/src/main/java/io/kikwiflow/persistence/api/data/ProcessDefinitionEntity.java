package io.kikwiflow.persistence.api.data;

import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;
import io.kikwiflow.persistence.api.data.bpmn.FlowNodeDefinitionEntity;

import java.util.Map;

public class ProcessDefinitionEntity {

    private String id;
    private Integer version;
    private String key;
    private String name;
    private Map<String, FlowNodeDefinitionEntity> flowNodes;
    private FlowNodeDefinitionEntity defaultStartPoint;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, FlowNodeDefinitionEntity> getFlowNodes() {
        return flowNodes;
    }

    public void setFlowNodes(Map<String, FlowNodeDefinitionEntity> flowNodes) {
        this.flowNodes = flowNodes;
    }

    public FlowNodeDefinitionEntity getDefaultStartPoint() {
        return defaultStartPoint;
    }

    public void setDefaultStartPoint(FlowNodeDefinitionEntity defaultStartPoint) {
        this.defaultStartPoint = defaultStartPoint;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private Integer version;
        private String key;
        private String name;
        private Map<String, FlowNodeDefinitionEntity> flowNodes;
        private FlowNodeDefinitionEntity defaultStartPoint;

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder version(Integer version) {
            this.version = version;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder flowNodes(Map<String, FlowNodeDefinitionEntity> flowNodes) {
            this.flowNodes = flowNodes;
            return this;
        }

        public Builder defaultStartPoint(FlowNodeDefinitionEntity defaultStartPoint) {
            this.defaultStartPoint = defaultStartPoint;
            return this;
        }

        public ProcessDefinitionEntity build() {
            ProcessDefinitionEntity entity = new ProcessDefinitionEntity();
            entity.setId(this.id);
            entity.setVersion(this.version);
            entity.setKey(this.key);
            entity.setName(this.name);
            entity.setFlowNodes(this.flowNodes);
            entity.setDefaultStartPoint(this.defaultStartPoint);
            return entity;
        }
    }
}
