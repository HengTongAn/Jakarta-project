# Audit logging

`util/web/AuditLogger` writes two things: a formatted line to the application
log, and — when database auditing is on — a row in `audit_logs`. It is also the
place that the payment and admin flows call to record what happened.

## Categories

`action_type` is an enum, and the four public methods map one to one:

| Method | Category | Records |
|---|---|---|
| `logAuthEvent` | `AUTH` | `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`, and invalid-credential attempts. Failed attempts are treated as security-relevant and logged more loudly. |
| `logAdminAction` | `ADMIN` | A product created, a user promoted, a setting changed. |
| `logDataModification` | `DATA` | A record changed, with resource type and id. |
| `logSecurityEvent` | `SECURITY` | Rate-limit trips, access denials, and similar. |
| — | `SYSTEM` | Available in the enum. No caller yet. |

## Correlation is the point

`audit_logs` carries `request_id`, `session_id`, and `ip_address`. Those are
seeded once per request by `RequestAuditContextFilter` — which runs **before**
the security filters, deliberately, so that a *blocked* request is still
correlatable. An authentication failure is exactly the event you most want to
tie to a session, and it is rejected by a filter.

Given a `session_id` you can reconstruct everything that session did. Given a
`request_id` you can see one request end to end. Neither is possible from a log
file of formatted lines alone.

## The table

```
audit_id       int PK
action_type    ENUM('AUTH','ADMIN','DATA','SECURITY','SYSTEM')
action_name    varchar(100)
actor          varchar(50)
resource_type  varchar(50)
resource_id    varchar(50)
details        varchar(500)
ip_address     varchar(45)
request_id     varchar(64)
session_id     varchar(64)
created_at     timestamp
```

Indexed on `audit_id`, `action_type`, `actor`, `request_id`, `created_at`.

`audit_logs_archive` has the same shape plus `archive_id`, `original_audit_id`,
and `archived_at`, written by Admin → History → Archive. It is for moving cold
rows out of the hot table, not for deletion — nothing is thrown away.

## `details` is where a leak would happen

`details` is `VARCHAR(500)` and is written by the callsite, so it is bounded by
the code that fills it rather than by the schema. Two rules:

- **Never put a password, a token, or a card field in `details`.** The column
  will accept it.
- **Never put a whole request body in `details`.** A naive "log the submitted
  form" would put a card number in the audit table, which is a table an admin
  can read.

The request-audit filter records **no request parameters**, which is the property
that makes this safe by default. It was checked directly: nothing in
`src/main/java` calls `getParameterMap` or `getParameterNames`, so there is no
bulk-parameter logging path.

`audit_logs` was verified to contain no card numbers.

## Database auditing is opt-in

`-Dcomputerstore.audit.database.enabled`. Without it, `AuditLogger` writes only
to the application log and `audit_logs` stays empty. That is a reasonable
default for local work — the table grows fast and holds a row for every request
that touches anything auditable.

## Retention

`migration_add_audit_correlation` exists for the correlation and
auto-retention columns, and the retention behaviour is controlled from Admin →
History. **Check that it is doing what you expect before assuming a bound on
table growth**; an auto-retention feature that silently does nothing is the usual
failure.

## The honest limitation

**The audit log records what code chose to record.** The categories are
enforced by the enum, but the *coverage* is per-callsite. A new sensitive action
is not audited because nobody added a call — and nothing fails, because there is
no test asserting "this action must be logged".

That is the gap worth closing. A test that lists the actions which must be
audited, and fails when one of them stops calling `AuditLogger`, would turn a
convention into a guarantee. It is not written yet.

`AuditLogRepositoryTest` covers the repository. It does not cover coverage.
