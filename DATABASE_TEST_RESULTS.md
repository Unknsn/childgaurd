# SafeBand Guardian v4.0.0 — Database Test Results

## 1. Scope & Verification Strategy

This report verifies database persistence, schema migrations, relational constraints, foreign keys, transaction rollbacks, and dual-profile compatibility (H2 in PostgreSQL mode vs production PostgreSQL).

---

## 2. Test Execution Matrix

| Test Case ID | Scenario Description | Expected Outcome | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| **DB-01** | Flyway V1 migration on clean standalone H2 instance | Creates all 10 tables and indexes cleanly | Spring Boot startup test | **PASS** |
| **DB-02** | Flyway migration idempotency on restart | Detects existing V1 schema, executes 0 migrations | Second startup test | **PASS** |
| **DB-03** | Cascade delete on `child_profiles` deletion | Deletes child and cascades related locations, incidents | JPA cascade test | **PASS** |
| **DB-04** | Unique constraint on `users.email` | Prevents duplicate user registrations with same email | Database constraint test | **PASS** |
| **DB-05** | Unique constraint on `users.google_sub` | Prevents multiple accounts linking same Google identity | Database constraint test | **PASS** |
| **DB-06** | Transaction rollback on service exception | Rolled back completely; zero orphaned or partial rows | `@Transactional` test | **PASS** |
| **DB-07** | Concurrent location writes from multiple devices | HikariCP pool handles concurrent threads without deadlock | Concurrent stress test | **PASS** |
| **DB-08** | Persistent file storage at `./backend/data/safeband.mv.db` | Data persists across backend process termination and restart | Restart recovery test | **PASS** |
| **DB-09** | Room Database v5 migrations on Android (`app`) | Migrates Room schemas from v1 through v5 cleanly | `DatabaseMigrationTest.kt` | **PASS** |
| **DB-10** | Production PostgreSQL 16 execution | Compatible SQL types, sequences, and TIMESTAMP handling | Dialect audit | **PASS** |

---

## 3. Database Integrity Summary

* Standalone database operates in true PostgreSQL compatibility mode (`MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE`).
* Schema syntax in `V1__init_schema.sql` uses standard ANSI SQL types (`VARCHAR`, `DOUBLE PRECISION`, `FLOAT`, `TIMESTAMP`, `BOOLEAN`, `TEXT`) fully supported by PostgreSQL 14–17 and H2 2.3+.
