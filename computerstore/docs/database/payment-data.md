# What payment data is stored

The rule the payment code is built around: **a full card number is never
accepted, never stored, and never logged.** This page records how that is
achieved and how it was checked, because "we don't store card numbers" is the
kind of claim that deserves evidence.

## What is stored

| Column | Example | Why |
|---|---|---|
| `payments.card_brand` | `Visa` | Enough to render "Visa ending 4242". |
| `payments.card_last4` | `4242` | Enough for a customer to recognise their own card. |
| `payments.transaction_id` | `DEMO-4242-C0641D` | Simulation reference. Contains only the last four. |
| `payments.status` | `PAID` / `FAILED` | The outcome. |
| `payments.message` | `Approved` | The customer-facing reason. |
| `orders.payment_method` | `VISA_CARD` | Which method was used. |
| `orders.payment_transaction` | the same reference | Denormalised onto the order. |

Simulation references are `DEMO-<last4>-<hex>`, so even the reference cannot
carry more than four digits of the number.

## Why it is impossible rather than merely avoided

**The validator is a whitelist.** `CardValidator.TEST_CARDS` holds six published
sandbox numbers. Anything that is not one of them is refused, with a message
naming the ones that work. A real card number therefore cannot be accepted even
if every other check passes and even if Luhn is satisfied.

**The return type has nowhere to put it.** Validation returns:

```java
public record CardDetails(String brand, String last4, String outcome, String message)
```

No field can hold a PAN. The number is a local variable in `CheckoutServlet`,
passed to `validate()`, and then it is out of scope. There is no field, no
constructor parameter, and no setter through which a number could travel
downstream. This is the important property — not that the code is careful, but
that the shape of the data makes care unnecessary.

## The database backstop

`payments.card_last4` is `VARCHAR(4)`. Inserting a full card number fails:

```
ERROR 1406 (22001): Data too long for column 'card_last4' at row 1
```

There is also `chk_payments_card_last4`:

```sql
CHECK ((card_last4 IS NULL) OR (CHAR_LENGTH(card_last4) <= 4))
```

**The column type is the enforcing mechanism. The `CHECK` is a backstop.** It
cannot fire while the column is `VARCHAR(4)` — the value never gets that far. It
earns its place by surviving a future widening of the column, at which point it
becomes the thing that stops a regression.

Stating this accurately matters because the first verification of that
constraint was wrong. A probe inserted a card number for a non-existent order
`999999`, so it failed on `fk_payments_order` before reaching the constraint, and
the failure was read as the constraint working. Redone correctly — inside
`START TRANSACTION … ROLLBACK` against a real order — the rejection is error
1406 from the column, and the `CHECK` is never reached.

`CardSchemaSafetyTest` asserts both halves: the column is still `VARCHAR(4)`, and
the constraint still exists.

## Log paths that were checked

| Vector | Status |
|---|---|
| `payments` table | No column can hold more than 4 digits of a number. |
| `audit_logs` | Verified empty of card numbers. |
| `catalina.out` | Verified empty of card numbers. |
| `audit_logs.details` | The request-audit filter records no request parameters. |
| Access log | Tomcat has no `accessLogPattern` configured, so none is written. |
| URLs | Card fields are POST body only. No card field ever appears in a path or query string. |

Bulk-parameter logging would be the obvious hole, so it was checked directly:
**nothing in `src/main/java` calls `getParameterMap` or `getParameterNames`.**
There is no "log all parameters" code path to leak through.

## What is deliberately not implemented

There is no acquirer integration. A store cannot call Visa directly; it needs
Stripe, Braintree, PayPal, or a local acquirer. Until one of those is
integrated:

- `PaymentConfig.isCardReady()` returns a hard-coded `false`.
- `cardBlockingReason()` says so, and `/admin/payments` displays it.
- With `payment.card.enabled=true` but `payment.card.simulate=false`, the card
  option is **withheld from checkout** rather than offered and then failing.

The last point is the design decision that matters: an option that is visible
and broken is worse than an option that is absent.

## When a real acquirer is added

Three properties must survive the change, and each has a test that will fail if
it does not:

1. **No PAN reaches this application's server.** In a real integration the card
   fields are hosted by the acquirer and this application receives only a token.
   That is the single most important change, and it is a view-layer change, not a
   service change.
2. **`CardSchemaSafetyTest` still passes.** `card_last4` stays `VARCHAR(4)`.
3. **`isCardReady()` starts returning something real**, and only then does
   `payment.card.simulate=false` become a meaningful setting.
