package io.kikwiflow.persistence;

import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.model.execution.CoverageSnapshot;
import io.kikwiflow.model.execution.CoveredElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class StatsManager {

    private Map<String, List<CoveredElement>> coveredElements = new HashMap<String, List<CoveredElement>>();
    private final KikwiflowConfig kikwiflowConfig;

    public StatsManager(KikwiflowConfig kikwiflowConfig) {
        this.kikwiflowConfig = kikwiflowConfig;
    }

    private CoveredElement getCoveredElement(CoverageSnapshot coverageSnapshot){
        CoveredElement coveredElement = new CoveredElement();
        coveredElement.setElementId(coverageSnapshot.flowNode().getId());
        coveredElement.setProcessDefinitionId(coverageSnapshot.processDefinition().getId());
        coveredElement.setProcessInstanceId(coverageSnapshot.processInstance().getId());
        coveredElement.setStartedAt(coverageSnapshot.startedAt());
        coveredElement.setFinishedAt(coverageSnapshot.finishedAt());
        coveredElement.setStatus(coverageSnapshot.status());
        return coveredElement;
    }


    public void registerCoverage(CoverageSnapshot coverageSnapshot){
        CoveredElement coveredElement = getCoveredElement(coverageSnapshot);
        String processInstanceId = coverageSnapshot.processInstance().getId();
        List<CoveredElement> processInstanceIdElements = coveredElements.get(processInstanceId);
        if(Objects.isNull(processInstanceIdElements)){
            processInstanceIdElements = new ArrayList<>();
        }

        processInstanceIdElements.add(coveredElement);

        coveredElements.put(processInstanceId, processInstanceIdElements);
    }
}
