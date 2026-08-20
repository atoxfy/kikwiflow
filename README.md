<div align="center">
  <img src="assets/logo.svg" width="120" height="120" alt="Kikwiflow logo"/>

  <br/>
  <h1>Kikwiflow</h1>
  <h3>Orchestration Ecossystem</h3>
  <p>Stateful workflows, living documentation, and AI-ready JSON topologies powered by pure Java.</p>

  <p>
    <a href="https://github.com/atoxfy/kikwiflow/actions"><img src="https://img.shields.io/badge/build-passing-brightgreen" alt="Build Status"/></a>
    <a href="https://jdk.java.net/21/"><img src="https://img.shields.io/badge/Java-21-blue.svg" alt="Java 21"/></a>
    <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg" alt="Spring Boot"/></a>
    <a href="https://discord.gg/kikwiflow"><img src="https://img.shields.io/badge/Discord-Join%20Community-7289da.svg" alt="Discord"/></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"/></a>
  </p>
</div>

---

## What is Kikwiflow?

**Kikwiflow** is a stateful process orchestration engine designed to help engineering teams build, observe, and scale complex distributed systems.

Orchestrating microservices, handling asynchronous callbacks, and managing SLAs shouldn't require compromising your application's architecture. Kikwiflow embraces the modern Java ecosystem: workflows are defined as clean JSON graphs, while execution logic remains entirely in strictly-typed, testable Java code.

### Why Kikwiflow?

* **Exceptional Developer Experience (DX):** Implement your business tasks, decision strategies, and dynamic timers as standard Spring Beans with full IDE support, dependency injection, and instant unit testing.
* **AI-Ready & Living Documentation:** Decoupling visual topology (`.kikwi` JSON schemas) from Java code makes Kikwiflow uniquely suited for the LLM era. AI agents can generate, inspect, and refactor process blueprints seamlessly.
* **Built for High Concurrency:** Powered by **Java 21 Virtual Threads**, handling thousands of concurrent I/O-bound wait states with minimal memory overhead.
* **Transactional Reliability:** Native multi-document ACID transactions with MongoDB or in-memory repositories via the `UnitOfWork` pattern.

---

## ⚡ Quick Start: From Zero to Code in Seconds

Kikwiflow revolutionizes workflow bootstrap with the **Kikwiflow Craft Initializr**.

```
  ┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
  │ 1. Model Process│ ───►  │ 2. Export via   │ ───►  │ 3. Implement    │ ───►  │ 4. Run & Monitor│
  │   (Craft UI)    │       │    Initializr   │       │    Spring Beans │       │  (Live Engine)  │
  └─────────────────┘       └─────────────────┘       └─────────────────┘       └─────────────────┘
```

### Step 1: Model & Export
1. Open **Kikwiflow Craft** (the visual modeler).
2. Drag and drop your workflow nodes (Executable Tasks, External Tasks, Gateways, Timers).
3. Configure your addons and click **Export -> Initializr**.
4. Download the generated Spring Boot project boilerplate—complete with pre-configured Maven dependencies, embedded `.kikwi` workflow definitions, and stubbed `@Component` Java classes!

### Step 2: Implement Your Logic
Unzip the project and fill in the generated `@Component` Spring Beans:

```java
import io.kikwiflow.execution.api.context.ExecutionContext;
import io.kikwiflow.execution.api.handler.TaskHandler;
import io.kikwiflow.model.execution.ProcessVariable;
import org.springframework.stereotype.Component;

@Component("enrichCustomerHandler")
public class EnrichCustomerHandler implements TaskHandler {

    @Override
    public void handle(ExecutionContext execution) {
        String customerId = execution.getVariable("customerId").value().toString();
        
        // Write standard, testable Java code
        System.out.println("Enriching profile for customer: " + customerId);
        
        execution.setVariable(new ProcessVariable("status", "VERIFIED"));
    }
}
```

### Step 3: Run
Start your Spring Boot application:

```bash
mvn spring-boot:run
```

*(There is no Maven wrapper in this project — `mvn` must be on your `PATH`.)*

The auto-deployer automatically detects and registers all `.kikwi` workflow files from `classpath*:processes/**/*.kikwi` on application startup!

### Step 4: Monitor
If you included the management REST starter and web-monitor open **Kikwiflow Monitor** to watch process tokens flow in real time, inspect context variables, and interact with pending tasks.

---

## Deep Dive: Controlling the Engine Programmatically

When you need custom triggers, webhook callbacks, or variable updates, `KikwiflowEngine` provides a clean, fluent Java API.

### 1. Starting a Process Instance
Start instances fluently using a business key and execution context:

```java
@Service
public class OrderService {

    private final KikwiflowEngine kikwiflowEngine;

    public OrderService(KikwiflowEngine kikwiflowEngine) {
        this.kikwiflowEngine = kikwiflowEngine;
    }

    public void createOrder(String orderId, BigDecimal amount) {
        ProcessInstance instance = kikwiflowEngine.startProcess()
            .byKey("order-fulfillment")
            .withBusinessKey(orderId)
            .withBusinessValue(amount)
            .withVariables(Map.of(
                "orderId", new ProcessVariable("orderId", orderId),
                "amount", new ProcessVariable("amount", amount)
            ))
            .execute();
            
        System.out.println("Started process instance ID: " + instance.id());
    }
}
```

### 2. Completing External Tasks (Callbacks & Webhooks)
When an external system or human completes a wait state (`EXTERNAL_TASK`), inform the engine to advance the token:

```java
@PostMapping("/webhooks/payment")
public ResponseEntity<Void> onPaymentWebhook(@RequestParam String externalTaskId, @RequestBody PaymentResult result) {
    
    IdentityContext identity = IdentityContext.system(); // Or current tenant/user identity
    
    Map<String, ProcessVariable> outputVars = Map.of(
        "paymentApproved", new ProcessVariable("paymentApproved", result.isSuccess()),
        "transactionId", new ProcessVariable("transactionId", result.getTxId())
    );

    // Completes the wait state and synchronously continues workflow execution
    kikwiflowEngine.completeExternalTask(externalTaskId, outputVars, identity);

    return ResponseEntity.ok().build();
}
```

### 3. Setting & Updating Variables
Update runtime variables on active process instances at any time:

```java
public void updateCustomerRiskScore(String processInstanceId, double riskScore) {
    IdentityContext identity = IdentityContext.system();
    
    kikwiflowEngine.setVariables(
        processInstanceId,
        Map.of("riskScore", new ProcessVariable("riskScore", riskScore)),
        identity
    );
}
```

---

## 🛠️ The Kikwiflow Ecosystem

* 🎨 **[Kikwiflow Craft](https://kikwiflow.io/craft):** Drag-and-drop web modeler for `.kikwi` process definitions with real-time validation.
* 📊 **[Kikwiflow Monitor](https://kikwiflow.io/monitor):** Operational dashboard for real-time visualization, SSE streaming, incident management, and manual task completion.
* 🔌 **[REST Management Starter](https://kikwiflow.io/docs/rest-api):** Ready-to-use endpoints for searching instances, managing tasks, and retrying incidents.

---

## 📖 Documentation & Guides

Explore the full documentation at **[kikwiflow.io/docs](https://kikwiflow.io/docs)**:

* [Anatomy of a Process](https://kikwiflow.io/docs/anatomy)
* [Executable Tasks & Handlers](https://kikwiflow.io/docs/executable-tasks)
* [External Tasks & Workers](https://kikwiflow.io/docs/external-tasks)
* [Decisions & Gateways](https://kikwiflow.io/docs/gateways)
* [Timers & SLAs](https://kikwiflow.io/docs/timers)
* [Error Handling & Incidents](https://kikwiflow.io/docs/error-handling)
* [Testing Handlers with InMemory Engine](https://kikwiflow.io/docs/testing)

---

## 🤝 Community & Support

* 💬 **Discord:** Join our [Discord Community](https://discord.gg/kikwiflow) to chat with maintainers.
* 🐛 **Issues:** Report bugs or suggest features on [GitHub Issues](https://github.com/atoxfy/kikwiflow/issues).

---

## ⚖️ License

Kikwiflow Community Edition is licensed under the [Apache 2.0 License](LICENSE).
