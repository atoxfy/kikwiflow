package io.kikwiflow.model.execution;

import io.kikwiflow.model.bpmn.elements.FlowNode;

import java.util.List;

public class Continuation {

    private List<FlowNode> nextNodes;
    private boolean isAsynchronous;
    public Continuation(List<FlowNode> n, boolean a) { this.nextNodes = n; this.isAsynchronous = a; }
    public boolean isAsynchronous() { return isAsynchronous; }
    public List<FlowNode> getNextNodes() { return nextNodes; }
}
