# XA Fault Testkit

This repository contains the deterministic XA core for an XA fault-injection testkit.
It decorates `XAResource` instances, records an ordered, defensive event timeline,
and applies once-only rules before or after XA operations.  JDBC, JMS, Toxiproxy,
and transaction-manager adapters remain separate follow-on modules.