# Payment data handling

The short version: **a full card number cannot be accepted, stored, or logged by
this application.** The full argument, with the evidence, is in
[../database/payment-data.md](../database/payment-data.md). This page is the
security summary and the rules for anyone changing the payment code.

## Why it is a property of the code and not a promise

Three layers, each sufficient on its own.

**1. The validator is a whitelist.** `CardValidator.TEST_CARDS` holds six
published sandbox numbers. Anything else is refused, with a message naming the
ones that work:

```java
"4242424242424242" -> APPROVED
"4000000000000002" -> DECLINED, issuer declined
"4000000000009995" -> DECLINED, insufficient funds
"4000000000000069" -> DECLINED, expired
"4000000000000127" -> DECLINED, incorrect security code
"4000000000000119" -> DECLINED, could not be processed
```

Luhn, brand detection, expiry, CVV, and holder-name checks all run first and all
pass for a real card. The whitelist is what stops it. A real card number
therefore fails even when every other check succeeds.

**2. The return type has nowhere to put it.**

```java
public record CardDetails(String brand, String last4, String outcome, String message)
```

No PAN field, no PAN parameter, no PAN setter. The number is a local variable in
`CheckoutServlet`, passed to `validate()`, and out of scope. Nothing downstream
can leak it because nothing downstream can name it.

**3. The column will not accept it.** `payments.card_last4` is `VARCHAR(4)`, so
inserting a full number fails with MySQL error **1406**. `chk_payments_card_last4`
is a backstop that cannot fire while the column is that narrow — it earns its
place only by surviving a future column widening.

`CardSchemaSafetyTest` asserts the column type and the constraint still exist.

## Log paths, checked rather than assumed

| Vector | Status |
|---|---|
| `payments` | No column can hold a PAN. |
| `audit_logs` | Verified clean. The request-audit filter records no parameters. |
| `catalina.out` | Verified clean. |
| Access log | Tomcat has no `accessLogPattern`, so none exists. |
| URLs | Card fields are POST body only. |
| Bulk parameter logging | **Nothing in `src/main/java` calls `getParameterMap` or `getParameterNames`.** |

That last row is the one that matters most: there is no "log everything" code
path, so there is no path for card data to leak through.

## Availability is not a formality

```java
isCardAvailable() == isCardEnabled() && (isCardSimulated() || isCardReady())
```

`isCardReady()` is a hard-coded `false` with an explicit comment, because a store
cannot call Visa directly — it needs an acquirer. So `payment.card.enabled=true`
with `payment.card.simulate=false` **withholds** the card option from checkout
entirely.

That is the design decision. An option that is visible and then fails is worse
than an option that is absent, because the customer's trust is spent on a
failure that the configuration caused.

`cardBlockingReason()` produces the explanation, and `/admin/payments` displays
it next to the switch so the operator is not left guessing.

## Rules for changing this code

1. **Do not add a field to `CardDetails` that could hold a PAN.** The safety
   property is the shape of the type, not the discipline of its readers.
2. **Do not log a submitted field to diagnose a problem.** Not at debug level,
   not temporarily, not in a comment. The whitelist means a real number never
   gets past `validate` — but the field is still in the POST body, in the
   container's memory, and possibly in a form-dump you have not thought of.
3. **Do not put a card field in `audit_logs.details`.** The column will accept
   it.
4. **Do not relax the whitelist to "any Luhn-valid number".** That one change
   converts the card form into a place where a real card can be entered.
5. **If you add an error path, check that the message does not echo the number
   back.** The form reports the reason; it must not render what was typed. This
   is verified — a real-looking PAN is refused with no order created and the
   number is not echoed into the HTML.
6. **Do not switch `payment.card.simulate=false` expecting real payments.** There
   is nothing behind it.

## Before shipping an acquirer integration

Three properties must survive, each with a test that will fail if it does not:

1. **No PAN reaches this application's server.** With a real processor the card
   fields are hosted by the acquirer and this application receives a token. That
   is a **view-layer change** — the form and its fields — not a service change.
2. **`CardSchemaSafetyTest` still passes.** `card_last4` stays `VARCHAR(4)`, and
   what is stored is a token reference plus a brand and last four obtained from
   the acquirer, never from the client.
3. **`isCardReady()` starts returning something real**, and only then does
   `payment.card.simulate=false` become a meaningful setting.

Until all three hold, `payment.card.simulate=true` is the only honest
configuration, and the admin page already says so.
