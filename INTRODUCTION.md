# Meet J XA Tester

Distributed transactions are reassuring when everything works and revealing when they do not. An order may be stored in a database while its matching message is on the way to a broker, and XA helps the two systems agree on whether the work should succeed or be undone. The difficult part is gaining confidence in the failure paths. A real database or network problem can be hard to reproduce at exactly the point a test needs it. J XA Tester is a Java 17 testkit that makes those moments predictable, so teams can explore what their transaction-handling code does before production has to teach the lesson.

The testkit sits between an application and an existing XA resource. It watches calls such as `start`, `prepare`, `commit`, and recovery operations without needing to know the database query or message body. A test gives the resource a safe, meaningful name, then asks J XA Tester to react when a chosen operation reaches a chosen phase. The reaction can be a synthetic XA exception, a pause, a callback, or a network fault through Toxiproxy. That means a test can reliably say, “make the order database fail just before commit,” instead of hoping a temporary outage arrives at the right millisecond.

```mermaid
flowchart LR
    App[Application test] --> TM[Transaction manager]
    TM --> Tester[J XA Tester]
    Tester --> DB[Database XA resource]
    Tester --> MQ[Messaging XA resource]
    Tester --> Timeline[Recorded timeline]
```

J XA Tester works with the XA participant an application already uses. Its core wrapper can decorate any `XAResource`, while dedicated adapters make JDBC XA data sources and Jakarta Messaging XA connection factories easy to instrument. The JUnit 5 extension can create a fresh scenario engine for every test and check that a required fault really happened. This keeps a test honest: if the code never reached the failure point, the test does not quietly pass with an untested recovery path.

After a scenario runs, the recorded timeline provides the story behind it. Rather than guessing whether a resource prepared before a commit failed, a developer can inspect the immutable XA events and see the order directly. Timeline helpers can also wait for an event or verify the sequence from within a test. This is particularly useful when retries, background work, or recovery are involved, because it turns a confusing transaction trace into something the test can describe and verify.

The project is intended for testing, not for running transactions in production. It deliberately records structural XA information and an application-selected resource alias, rather than connection credentials, SQL, or message contents. Start with one isolated scenario engine per test, wrap the resources passed to the transaction manager, and add the fault that tells the story you want to understand. From there, J XA Tester makes XA failures less mysterious and gives transaction code a chance to prove that it can recover.
