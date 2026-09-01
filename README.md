# J XA Tester

[![Build and Test](https://github.com/rrobetti/j-xa-tester/actions/workflows/main.yml/badge.svg)](https://github.com/rrobetti/j-xa-tester/actions/workflows/main.yml)

J XA Tester is a Java 17 testkit for deterministic, phase-aware XA fault
injection. It decorates an existing `javax.transaction.xa.XAResource` (JDBC,
JMS, or any other XA participant), records every interaction as an immutable
event, and can inject a synthetic `XAException`, delay, network-level fault,
or arbitrary callback at a precisely selected point (`BEFORE` / `AFTER_SUCCESS`
/ `AFTER_FAILURE` of a specific operation on a specific resource).

It is intended for tests, not production use. It never records connection
credentials, SQL, or message contents — only an application-chosen
`resourceId` alias and structural metadata about the XA call.

## Module structure

The project is a Maven reactor of six focused modules, each independently
consumable:

| Module | Artifact | Depends on | Purpose |
| --- | --- | --- | --- |
| [`core`](core) | `xa-tester-core` | *(JDK only)* | `FaultInjectingXAResource`, the event model, the rule/action engine, and the blocking `XaGate`. Zero non-JDK dependencies at compile time. |
| [`jdbc`](jdbc) | `xa-tester-jdbc` | `core` | Wraps a JDBC `XADataSource`/`XAConnection` so every connection it hands out (normal or recovery) is instrumented automatically. |
| [`jms`](jms) | `xa-tester-jms` | `core` | Wraps a Jakarta Messaging `XAConnectionFactory`/`XAConnection`/`XASession` using JDK dynamic proxies, so the adapter never compiles against `jakarta.jms` itself. |
| [`toxiproxy`](toxiproxy) | `xa-tester-toxiproxy` | `core` | A minimal [Toxiproxy](https://github.com/Shopify/toxiproxy) REST client (hand-rolled JSON + `java.net.http.HttpClient`) plus `XaAction` bridges, for combining synthetic XA faults with real network faults. |
| [`timeline`](timeline) | `xa-tester-timeline` | `core` | Human-readable timeline rendering, blocking `TimelineProbe.awaitEvent`, and `TimelineAssertions` helpers for verifying what actually happened. |
| [`junit5`](junit5) | `xa-tester-junit5` | `core`, `timeline` | A JUnit Jupiter extension (`@XaTest`, `@XaFault`/`@XaFaults`) that provisions a fresh engine per test, injects it as a parameter, and fails the test if a required fault never fired. |

### Dependency policy: test artifacts only

Only the JDK and other modules of this project are compile-scope dependencies
of any module's main code, with one deliberate, documented exception:
`xa-tester-junit5` compiles against `junit-jupiter-api`, because its entire
purpose is integrating with JUnit 5. Every other external library used
anywhere in the build — H2, `jakarta.jms-api`, Toxiproxy's wire format,
`junit-jupiter`, `junit-platform-testkit`, `junit-platform-launcher` — is a
`test`-scoped dependency, used only to exercise the adapters against a real
(if lightweight) implementation of the protocol they wrap. Run
`mvn dependency:tree -pl <module>` in any module to verify this for yourself.

## Build and test

```bash
git clone https://github.com/rrobetti/j-xa-tester.git
cd j-xa-tester
mvn test
```

This builds and tests all six modules in dependency order (57 tests as of
this writing: 18 in `core`, 3 in `jdbc`, 4 in `jms`, 15 in `toxiproxy`, 13 in
`timeline`, 4 in `junit5`).

For local use, install the snapshot, then add the module(s) you need to a
test project:

```bash
mvn install
```

```xml
<dependency>
  <groupId>io.github.rrobetti</groupId>
  <artifactId>xa-tester-jdbc</artifactId>
  <version>0.1.0-alpha</version>
  <scope>test</scope>
</dependency>
```

For a Spring Boot test setup that coordinates JDBC and MQ in one XA
transaction, see the [Spring Boot XA integration example](docs/spring-boot-integration.md).

## Continuous integration and releases

Two GitHub Actions workflows drive the project:

| Workflow | Trigger | Notes |
| --- | --- | --- |
| [`Build and Test`](.github/workflows/main.yml) | Push to the default branch, and pull requests targeting it | Runs `mvn clean verify` for all modules on JDK 17 and 21. Pull-request runs first wait on the `pr-approval` environment, so a maintainer must approve the run before any build starts; pushes to the default branch skip that gate. |
| [`Release to Maven Central`](.github/workflows/release.yml) | Manual (`workflow_dispatch`) | Bumps the version, builds and tests, publishes every module to Sonatype Maven Central (`-Prelease`), tags the release, bumps to the next `-alpha`, and creates a GitHub Release. Gated by the `release` environment. Supports a dry run. |

One-time repository setup:

1. **Environments** (Settings → Environments → *New environment*). These must
   be created by hand — GitHub auto-creates an environment on first use with
   no protection rules, and an unprotected environment approves nothing:
   - `pr-approval` — tick **Required reviewers** and add the maintainers, so
     pull-request builds stay pending until one of them approves. A pending
     build then shows a **Review deployments** button in the PR checks box.
   - `release` — tick **Required reviewers** and add `rrobetti`, so no release
     can be published without their approval.
2. **Secrets** needed by the release workflow: `SONATYPE_USERNAME`,
   `SONATYPE_PASSWORD` (Sonatype Central *user token*, not account
   credentials), `GPG_PRIVATE_KEY` (ASCII-armored), `GPG_PASSPHRASE`, and
   optionally `RELEASE_TOKEN` (a PAT with `repo` scope used to push the
   release commits and tag).

Note that a pull request opened by a bot or an outside collaborator is also
subject to the repository-level gate under Settings → Actions → General →
*Fork pull request workflows from outside collaborators*. That gate runs
before any job exists: the run sits in the `action_required` state and is
released by the **Approve and run workflows** button on the PR's checks (or on
the run page under the Actions tab), not by a **Review deployments** button.

## Core: intercepting an `XAResource`

Construct one `XaScenarioEngine` per isolated test, then decorate the
provider's resource before handing it to the transaction manager:

```java
import io.github.rrobetti.xafault.FaultInjectingXAResource;
import io.github.rrobetti.xafault.ResourceKind;
import io.github.rrobetti.xafault.XaScenarioEngine;
import javax.transaction.xa.XAResource;

XAResource vendorResource = obtainProviderXaResource();
XaScenarioEngine engine = new XaScenarioEngine();

XAResource instrumented =
    new FaultInjectingXAResource(vendorResource, engine, "orders-db", ResourceKind.JDBC);

transactionManager.enlistResource(instrumented);
```

`resourceId` is an application-chosen safe alias used in events; never put a
connection URL, username, or other secret in it.

Rules combine a `Predicate<XaEvent>` matcher with an `XaAction` and fire at
most once each, even under concurrent access:

```java
import static javax.transaction.xa.XAException.XAER_RMFAIL;

import io.github.rrobetti.xafault.XaAction;
import io.github.rrobetti.xafault.XaRule;
import io.github.rrobetti.xafault.XaRules;
import io.github.rrobetti.xafault.XaOperation;

XaRule rule = new XaRule(
    XaRules.before("orders-db", XaOperation.COMMIT),
    XaAction.throwException(XAER_RMFAIL));
engine.addRule(rule);

// ... exercise the code under test, then:
assertTrue(rule.fired(), "the intended XA fault was not injected");
```

`XaAction` also supports `.delay(Duration)`, `.gate(XaGate, Duration)` (block
until another thread releases the gate, with a timeout), `.callback(...)`,
and `.compose(...)` to chain several actions.

## JDBC adapter

`FaultInjectingJdbc.wrap` uses [J API Proxy](https://github.com/rrobetti/j-api-proxy)
to wrap a provider `XADataSource`:

```java
import io.github.rrobetti.xafault.jdbc.FaultInjectingJdbc;

XADataSource wrapped = FaultInjectingJdbc.wrap(vendorDataSource, engine, "orders-db");
XAConnection connection = wrapped.getXAConnection();
XAResource resource = connection.getXAResource(); // fault-injecting proxy
```

Tested against a real embedded H2 XA data source (test-scoped only).

## Jakarta JMS adapter

`FaultInjectingJms.wrap` uses [J API Proxy](https://github.com/rrobetti/j-api-proxy)
to instrument the XA object graph a Jakarta Messaging provider hands back:

```java
import io.github.rrobetti.xafault.jms.FaultInjectingJms;

XAConnectionFactory wrapped = FaultInjectingJms.wrap(providerFactory, engine, "orders-mq");

XAConnection connection = wrapped.createXAConnection();
XASession session = connection.createXASession();
XAResource resource = session.getXAResource(); // fault-injecting proxy
```

J API Proxy recursively wraps the factory, connection, session, and XA
resource. Ordinary message production and consumption calls pass straight
through untouched.

## Toxiproxy controller

`ToxiproxyClient` is a small REST client for a running
[Toxiproxy](https://github.com/Shopify/toxiproxy) server, letting a test
combine real network-level faults with the synthetic XA faults above:

```java
import io.github.rrobetti.xafault.toxiproxy.*;

ToxiproxyClient client = new ToxiproxyClient("http://localhost:8474");
ToxiproxyProxy proxy = client.createProxy("orders-db", "localhost:15432", "localhost:5432");

proxy.addToxic(Toxic.latency("db-lag", Toxic.Stream.DOWNSTREAM, 500, 100));
```

`ToxiproxyXaActions` bridges proxy control into the same `XaAction` pipeline
used for synthetic faults, so a rule can, e.g., disable a proxy the moment a
`PREPARE` call is observed:

```java
engine.addRule(new XaRule(
    XaRules.before("orders-db", XaOperation.PREPARE),
    ToxiproxyXaActions.disable(proxy)));
```

## Timeline and probes

`TimelineReport.render(events)` prints a fixed-width, human-readable timeline
of everything an engine recorded — useful for debugging a failing scenario or
attaching to a test failure report. `TimelineProbe` lets a test block a
background thread until a specific event is observed (already-happened or
still-to-come), and `TimelineAssertions` provides common structural checks:

```java
import io.github.rrobetti.xafault.timeline.*;

TimelineProbe probe = new TimelineProbe(engine);
XaEvent event = probe.awaitEvent(
    XaRules.afterFailure("orders-db", XaOperation.COMMIT), Duration.ofSeconds(5));

TimelineAssertions.assertOperationOrder(engine.journal().events(), "orders-db",
    XaOperation.START, XaOperation.END, XaOperation.PREPARE, XaOperation.COMMIT);
TimelineAssertions.assertNoFailures(engine.journal().events());
System.out.println(TimelineReport.render(engine.journal().events()));
```

## JUnit 5 extension

`@XaTest` provisions a fresh `XaScenarioEngine` per test method (resolvable as
a parameter) and `@XaFault` declares a synthetic fault as data instead of
imperative setup code. If a fault marked `requireFired = true` (the default)
never triggers, the extension fails the test during teardown — turning a
silently-skipped fault into a hard failure instead of a misleadingly green
test — and dumps the recorded timeline to `stderr` for any test that fails:

```java
import io.github.rrobetti.xafault.junit5.XaFault;
import io.github.rrobetti.xafault.junit5.XaTest;
import io.github.rrobetti.xafault.XaScenarioEngine;

@XaTest
class OrderServiceTest {

    @Test
    @XaFault(resourceId = "orders-db", operation = XaOperation.COMMIT, errorCode = XAException.XAER_RMFAIL)
    void retriesWhenCommitFailsOnce(XaScenarioEngine engine) {
        XAResource resource =
            new FaultInjectingXAResource(realResource, engine, "orders-db", ResourceKind.JDBC);
        // exercise the code under test; the first matching COMMIT call throws XAER_RMFAIL.
    }
}
```

Use `@XaFaults` (or simply repeat `@XaFault`) to declare more than one fault
on the same test method.

## `isSameRM` and recovery

Pass instrumented resources consistently to the transaction manager. When
`isSameRM` receives another `FaultInjectingXAResource`, the wrapper passes its
underlying provider resource to the delegate, preserving resource-manager
identity behavior. Recovery connections should also be wrapped, using the
same `resourceId`, before their resources reach the transaction manager, so
one engine observes `recover` and recovery-time `commit` calls too.

Use a separate engine per test; do not share one engine across unrelated
concurrent transactions.

## Limitations

This is a testkit, not a transaction manager or a message broker. It does not
select which XID belongs to which test, orchestrate cleanup, or manage a
Toxiproxy server's lifecycle (start/stop the server yourself — the client
only talks to an already-running instance). The JMS adapter is proxy-based
and reflects on return types, so a provider whose XA interfaces deviate
significantly in naming from the standard `jakarta.jms`/`javax.jms` contracts
may not be recognized correctly.
