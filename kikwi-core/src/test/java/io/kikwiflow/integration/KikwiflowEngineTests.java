package io.kikwiflow.integration;

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.persistence.KikwiflowEngineInMemoryRepository;
import io.kikwiflow.persistence.KikwiflowEngineRepository;

public class KikwiflowEngineTests {
    private final KikwiflowEngine kikwiflowEngine;
    private final KikwiflowEngineRepository kikwiflowEngineRepository;

    public KikwiflowEngineTests(){
        this.kikwiflowEngineRepository = new KikwiflowEngineInMemoryRepository();
        this.kikwiflowEngine = new KikwiflowEngine(kikwiflowEngineRepository, new KikwiflowConfig());
    }
}
