# Spring Boot XA integration example

This example shows how to use J XA Tester in a Spring Boot test that coordinates
one XA database resource and one XA message-broker resource in the same
two-phase commit (2PC).

The important integration rule is to wrap the provider XA objects before the
transaction manager sees them:

- wrap the application's `XADataSource` with `FaultInjectingJdbc.wrap(...)`
- wrap the broker's `XAConnectionFactory` with `FaultInjectingJms.wrap(...)`
- use stable, non-secret `resourceId` values such as `orders-db` and `orders-mq`
- create an isolated `XaScenarioEngine` per test context

The snippets below are test-focused. Keep the J XA Tester dependencies in
`test` scope and adapt bean names, provider classes, and transaction-manager
registration to your application.

## Test dependencies

```xml
<dependency>
  <groupId>io.github.rrobetti</groupId>
  <artifactId>xa-tester-jdbc</artifactId>
  <version>0.1.0-alpha</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>io.github.rrobetti</groupId>
  <artifactId>xa-tester-jms</artifactId>
  <version>0.1.0-alpha</version>
  <scope>test</scope>
</dependency>
```

Your Spring Boot test application also needs its normal XA-capable transaction
manager and provider dependencies, for example Narayana or another JTA
transaction manager, an XA JDBC driver, and a Jakarta Messaging provider.

## Example application code under test

The service only needs normal Spring transaction boundaries. The transaction
manager decides whether the JDBC and JMS resources participate in the same 2PC.

```java
@Service
class OrderService {
    private final JdbcTemplate jdbcTemplate;
    private final JmsTemplate jmsTemplate;

    OrderService(JdbcTemplate jdbcTemplate, JmsTemplate jmsTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jmsTemplate = jmsTemplate;
    }

    @Transactional
    void placeOrder(long orderId) {
        jdbcTemplate.update(
                "insert into orders(id, status) values (?, ?)",
                orderId,
                "CREATED");
        jmsTemplate.convertAndSend("orders.created", orderId);
    }
}
```

## Test wiring

Expose wrapped XA resources in the test context. If your transaction manager is
configured through resource-specific factory beans instead of raw
`XADataSource`/`XAConnectionFactory` beans, apply the same wrapping at the point
where those factories receive the provider XA object.

```java
@TestConfiguration
class XaFaultTestConfig {

    @Bean
    XaScenarioEngine xaScenarioEngine() {
        return new XaScenarioEngine();
    }

    @Bean
    @Primary
    XADataSource faultInjectingDataSource(
            @Qualifier("vendorXaDataSource") XADataSource vendorDataSource,
            XaScenarioEngine engine) {
        return FaultInjectingJdbc.wrap(vendorDataSource, engine, "orders-db");
    }

    @Bean
    @Primary
    XAConnectionFactory faultInjectingConnectionFactory(
            @Qualifier("vendorXaConnectionFactory") XAConnectionFactory vendorConnectionFactory,
            XaScenarioEngine engine) {
        return FaultInjectingJms.wrap(vendorConnectionFactory, engine, "orders-mq");
    }
}
```

Typical imports for the J XA Tester types used in the tests are:

```java
import io.github.rrobetti.xafault.XaAction;
import io.github.rrobetti.xafault.XaOperation;
import io.github.rrobetti.xafault.XaRule;
import io.github.rrobetti.xafault.XaRules;
import io.github.rrobetti.xafault.XaScenarioEngine;
import io.github.rrobetti.xafault.jdbc.FaultInjectingJdbc;
import io.github.rrobetti.xafault.jms.FaultInjectingJms;
import jakarta.jms.XAConnectionFactory;
import javax.sql.XADataSource;
import javax.transaction.xa.XAException;
```

## Test: break 2PC before committing to the database

This rule fires immediately before the transaction manager calls `commit` on
the database XA resource. The database commit fails, so the test can assert the
application's failure behavior and verify that the fault was actually injected.

```java
@SpringBootTest
@Import(XaFaultTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderServiceXaFailureTest {
    @Autowired OrderService orderService;
    @Autowired XaScenarioEngine engine;

    @Test
    void breaksBeforeDatabaseCommit() {
        XaRule dbCommitFailure = new XaRule(
                XaRules.before("orders-db", XaOperation.COMMIT),
                XaAction.throwException(XAException.XAER_RMFAIL));
        engine.addRule(dbCommitFailure);

        assertThatThrownBy(() -> orderService.placeOrder(1001L))
                .hasRootCauseInstanceOf(XAException.class);

        assertThat(dbCommitFailure.fired()).isTrue();
        assertThat(engine.journal().events())
                .anyMatch(event -> event.resourceId().equals("orders-db")
                        && event.operation() == XaOperation.COMMIT);
    }
}
```

## Test: break 2PC before committing to MQ

This rule uses the same transaction path but targets the message-broker XA
resource. It fires before the transaction manager commits MQ, while the database
resource remains wrapped and observable under its own `resourceId`.

```java
@SpringBootTest
@Import(XaFaultTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderServiceMqXaFailureTest {
    @Autowired OrderService orderService;
    @Autowired XaScenarioEngine engine;

    @Test
    void breaksBeforeMessageBrokerCommit() {
        XaRule mqCommitFailure = new XaRule(
                XaRules.before("orders-mq", XaOperation.COMMIT),
                XaAction.throwException(XAException.XAER_RMFAIL));
        engine.addRule(mqCommitFailure);

        assertThatThrownBy(() -> orderService.placeOrder(1002L))
                .hasRootCauseInstanceOf(XAException.class);

        assertThat(mqCommitFailure.fired()).isTrue();
        assertThat(engine.journal().events())
                .anyMatch(event -> event.resourceId().equals("orders-mq")
                        && event.operation() == XaOperation.COMMIT);
    }
}
```

## Notes for reliable tests

- Use a fresh Spring test context, a fresh `XaScenarioEngine`, or another
  isolation mechanism for each scenario so rules and journal events do not leak
  between tests.
- Keep `resourceId` values stable and safe to log; do not include JDBC URLs,
  usernames, passwords, broker URLs, or tenant identifiers.
- If the transaction manager performs recovery with separate XA resources, wrap
  those recovery resources with the same `resourceId` so recovery-time XA calls
  are visible too.
- A 2PC participant commit order is transaction-manager specific. Target the
  resource by `resourceId`, not by assuming database or MQ commits first.
