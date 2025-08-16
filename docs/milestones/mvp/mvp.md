
-   **Core & Execution:**

    -   Provide a deployment interface not tied to BPMN, allowing future extension for other notations/parsers.

    -   Parse BPMN into a directed graph.

    -   Interpret and execute automated tasks (ServiceTasks) using `JavaDelegate`.

    -   Interpret, create, and complete tasks external to the engine (HumanTasks).

    -   Interpret exclusive gateways.

    -   Implement process navigation logic.

    -   Enable the valuation/pricing of process instances.

-   **Architecture & Modules:**

    -   Provide the library containing common domains (`kikwi-model`).

    -   Define the strategy to segregate in-flight data from historical data.

    -   Define the methodology to ensure data persistence (events/history) can be done without harming the engine's performance.

    -   Provide the ability to be embedded in a Spring Boot application (`kikwi-spring-boot-starter`).

-   **Persistence:**

    -   Provide a mechanism for 100% in-memory execution (`kikwi-in-memory-addons`).

    -   Provide the runtime data persistence API (`kikwi-runtime-persistence-api`).

    -   Provide the persistence API implementation for MongoDB (`kikwi-persistence-mongodb`).

-   **Management & Observability:**

    -   Provide an addon for native observability (metrics).

    -   Provide the APIs for engine management (`kikwi-management-api`).

    -   Launch `kikwi-pulse` v1.0.0 with a basic management dashboard (list and complete tasks).

-   **Testing & Quality:**

    -   Provide addons for unit, integration, and E2E testing (`kikwi-testing`).

    -   Conduct the first version of performance tests.

    -   Implement E2E tests for the main process flows.