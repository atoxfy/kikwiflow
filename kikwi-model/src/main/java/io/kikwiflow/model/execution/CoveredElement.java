package io.kikwiflow.model.execution;

import io.kikwiflow.model.execution.enumerated.CoveredElementStatus;

import java.time.Instant;

public class CoveredElement {
    private String elementId;
    private String processDefinitionId;
    private String processInstanceId;

    private Instant startedAt;

    private Instant finishedAt;

    private CoveredElementStatus status;


    public String getElementId() {
        return elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public CoveredElementStatus getStatus() {
        return status;
    }

    public void setStatus(CoveredElementStatus status) {
        this.status = status;
    }
}
