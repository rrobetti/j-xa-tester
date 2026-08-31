# XA Fault Testkit

XA Fault Testkit is a Java 17 library for deterministic, phase-aware XA testing.
Its current `0.1.0-SNAPSHOT` core decorates an existing
`javax.transaction.xa.XAResource`, records each XA interaction, and can inject a
synthetic `XAException` at a selected point.

It is intended for tests, not production use. It never records connection
credentials, SQL, or message contents.

## Current scope

The core supports:

- Every `XAResource` method: `start`, `end`, `prepare`, `commit`, `rollback`,
  `recover`, `forget`, `isSameRM`, and transaction-timeout methods.
- `BEFORE`, `AFTER_SUCCESS`, and `AFTER_FAILURE` events.
- Globally ordered and per-resource operation ordinals.
- Immutable snapshots of XID bytes for safe journaling.
- Once-only predicate rules and synthetic `XAException` actions.
- Correct peer unwrapping for `isSameRM`.

JDBC/JMS factory wrappers, Toxiproxy controls, JUnit extensions, recovery
controllers, gates, delays, callbacks, reporting, and a scenario DSL are not
yet included. Wrap the `XAResource` returned by your provider manually while
using this core.

## Build and test

The project uses Maven and Java 17:

```bash
git clone https://github.com/rrobetti/j-xa-tester.git
cd j-xa-tester
mvn test
```

For local use, first install the snapshot:

```bash
mvn install
```

Then add it to a test project's Maven dependencies:

```xml
<dependency>
  <groupId>io.github.rrobetti</groupId>
  <artifactId>xa-fault-testkit</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

## Basic interception

Construct one `XaScenarioEngine` per isolated test. Decorate the provider's
resource before giving it to the transaction manager:

```java
import io.github.rrobetti.xafault.FaultInjectingXAResource;
import io.github.rrobetti.xafault.ResourceKind;
import io.github.rrobetti.xafault.XaScenarioEngine;
import javax.transaction.xa.XAResource;

XAResource vendorResource = obtainProviderXaResource();
XaScenarioEngine engine = new XaScenarioEngine();

XAResource instrumented = new FaultInjectingXAResource(
    vendorResource,
    engine,
    "orders-db",
    ResourceKind.JDBC);

transactionManager.enlistResource(instrumented);
```

`resourceId` is an application-chosen safe alias used in events. Do not put a
connection URL, username, or other secret in it. Use `ResourceKind.JMS` for a
messaging participant and `ResourceKind.OTHER` for another XA provider.

The wrapper delegates the original call unless a `BEFORE` rule throws. It emits
an `AFTER_SUCCESS` event with the integer return code for `prepare`,
`getTransactionTimeout`, or other integer-returning methods. If the delegate
or a rule throws an `XAException`, it emits `AFTER_FAILURE` and preserves that
exception's `errorCode`.

## Injecting a deterministic commit failure

Rules combine a `Predicate<XaEvent>` matcher with an `XaAction`. Each rule fires
at most once, including when multiple threads observe matching events. This
example prevents the delegate's first matching `commit` call and makes the
transaction manager observe `XAER_RMFAIL`:

```java
import static javax.transaction.xa.XAException.XAER_RMFAIL;

import io.github.rrobetti.xafault.EventPosition;
import io.github.rrobetti.xafault.XaAction;
import io.github.rrobetti.xafault.XaOperation;
import io.github.rrobetti.xafault.XaRule;

engine.addRule(new XaRule(
    event -> event.resourceId().equals("orders-db")
        && event.operation() == XaOperation.COMMIT
        && event.position() == EventPosition.BEFORE
        && Boolean.FALSE.equals(event.onePhase()),
    XaAction.throwException(XAER_RMFAIL)));
```

Because the rule action runs while recording the `BEFORE` event, the vendor
resource's `commit` method is not called. The journal still contains the
`BEFORE` event followed by an `AFTER_FAILURE` event. After the test, assert
`rule.fired()` so a mismatched transaction-manager flow cannot produce a false
pass:

```java
assertTrue(rule.fired(), "the intended XA fault was not injected");
```

Retain the rule when adding it:

```java
XaRule rule = new XaRule(
    event -> event.operation() == XaOperation.PREPARE
        && event.position() == EventPosition.BEFORE,
    XaAction.throwException(XAER_RMFAIL));
engine.addRule(rule);
```

## Inspecting the event timeline

`engine.journal().events()` returns an immutable snapshot in observed sequence
order. `sequence` is globally monotonic; `operationOrdinal` identifies the XA
operation across resources; `resourceOperationOrdinal` identifies it for the
particular wrapper instance.

```java
import io.github.rrobetti.xafault.XaEvent;

for (XaEvent event : engine.journal().events()) {
    System.out.printf("%03d %s %s %s%n",
        event.sequence(),
        event.resourceId(),
        event.operation(),
        event.position());
}
```

Relevant event fields include:

| Field | Meaning |
| --- | --- |
| `xid` | Defensive `XidSnapshot`; its byte-array accessors also return copies. |
| `flags` | XA flags for `start`, `end`, `recover`, or the timeout seconds for `setTransactionTimeout`. |
| `onePhase` | `commit`'s one-phase argument; otherwise `null`. |
| `returnCode` | Integer result where applicable, such as `XA_OK` from `prepare`. |
| `error` | The XA error code and exception class on `AFTER_FAILURE`; otherwise `null`. |
| `resourceInstanceId` | A unique wrapper identifier; several wrapped resources can represent one resource manager. |

## `isSameRM` and recovery

Pass instrumented resources consistently to the transaction manager. When
`isSameRM` receives another `FaultInjectingXAResource`, the wrapper passes its
underlying provider resource to the delegate, preserving resource-manager
identity behavior.

Recovery connections should also be wrapped before their resources are handed
to the transaction manager. This lets the same engine observe `recover` and
recovery-time `commit` calls:

```java
XAResource recoveryResource = new FaultInjectingXAResource(
    obtainRecoveryProviderXaResource(), engine, "orders-db", ResourceKind.JDBC);
```

Use a separate engine for each test and do not share one engine across
unrelated concurrent transactions. The current core does not select a single
test XID or provide cleanup/recovery orchestration; those responsibilities
remain with the test and transaction manager until later modules are added.

## Next steps

The planned adapters will wrap JDBC `XADataSource` and Jakarta JMS
`XAConnectionFactory` objects so normal and recovery connections are
intercepted automatically. Toxiproxy and JUnit modules will add real network
faults, cleanup, waits, reports, and outcome assertions on top of this core.
