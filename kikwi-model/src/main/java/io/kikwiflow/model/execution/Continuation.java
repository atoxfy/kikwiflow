package io.kikwiflow.model.execution;

import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;

import java.util.List;

public class Continuation {

    private List<FlowNodeDefinition> nextNodes;
    private boolean isAsynchronous;
    public Continuation(List<FlowNodeDefinition> n, boolean a) { this.nextNodes = n; this.isAsynchronous = a; }
    public boolean isAsynchronous() { return isAsynchronous; }
    public List<FlowNodeDefinition> getNextNodes() { return nextNodes; }
}
