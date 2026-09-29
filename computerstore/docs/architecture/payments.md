# The payment subsystem

Two providers, one checkout. This is the most intricate part of the codebase
because it has to satisfy two opposing requirements at once:

1. **Be usable with no credentials.** A student project has no ABA merchant
   account and no acquirer relationship. A payment option that cannot be
   demonstrated is not a payment option.
2. **Be safe to point at real money.** A card form that quietly accepted a real
   card number, or a simulator that fell through to a live call, would be worse
   than no card payment at all.

Most of the design decisions below exist to keep those two requirements from
fighting each other. The rule that resolves it: **the demo path is explicit,
narrow, and refuses anything it was not given in advance.**

## Configuration surface

Everything is in the `app_settings` table, so an administrator changes it at
`/admin/payments` with no rebuild and no restart. There is a short cache TTL, so
a change takes effect within that window.

| Key | Meaning |
|---|---|
| `payment.aba.enabled` | Offer ABA Payway at checkout. |
| `payment.aba.simulate` | Answer locally instead of calling the gateway. |
| `payment.aba.api_url` | Gateway base URL. |
| `payment.aba.merchant_id` | Merchant identifier. |
| `payment.aba.username` | Gateway username. |
| `payment.aba.shop_name` | Name shown to the customer. |
| `payment.aba.currency` | Transaction currency. `USD`. |
| `payment.card.enabled` | Offer card payment at checkout. |
| `payment.card.simulate` | Authorise locally instead of calling an acquirer. |

`payment.aba.secret` is resolved but **never** rendered, never echoed, and never
written back through the admin form. `AdminPaymentsServlet` only reports whether
a secret is *set* (`secretSet`).

### Availability is `enabled && chargeable`

`PaymentConfig` exposes two different questions and the distinction matters:

```java
isCardAvailable()  ==  isCardEnabled() && (isCardSimulated() || isCardReady())
```

A provider that is switched on but cannot actually charge anything is
**withheld from checkout entirely**, rather than offered and then failing. An
option that is visible and broken is worse than an option that is absent.

`isCardReady()` returns a hard-coded `false` with an explicit comment. That is
deliberate, not a stub: a store cannot call Visa directly. It needs an acquirer
— Stripe, Braintree, PayPal — and until one of those is integrated, the card
path has no live mode. `cardBlockingReason()` is the human-readable version and
is shown on the admin page.

For ABA, `isReady()` is likewise false until `merchant_id`, `username`, and
`secret` are all present, which is why simulation is the only mode that has been
exercised.

## The two flows are deliberately different

| | ABA Payway | Card |
|---|---|---|
| Shape | Redirect out, redirect back | Synchronous, during the checkout POST |
| Endpoint | `/payment/aba/*` | `/payment/card/*` |
| Places the order first | Yes | No — the card is validated *before* the order is created |
| Retry on failure | Re-enter the order and pay | Back to `/checkout` |
| Stored | QR payload, `aba_phone` | `card_brand`, `card_last4` |

ABA is a redirect flow because that is how the bank works. The card flow is
synchronous because a card authorisation returns in the same request. The
consequence worth internalising: **for cards, a bad card number must be
rejected before an order row exists**, otherwise a customer who mistypes their
card leaves an orphan order behind on every attempt.

## Card validation is a whitelist, not a check

`util/validation/CardValidator.java` does Luhn, brand detection, expiry, CVV,
and holder-name checks — and then the decisive part:

```java
private static final Map<String, String[]> TEST_CARDS = ...
    "4242424242424242" -> APPROVED
    "4000000000000002" -> DECLINED, "Your card was declined by the issuer."
    "4000000000009995" -> DECLINED, "Your card has insufficient funds."
    "4000000000000069" -> DECLINED, "That card has expired."
    "4000000000000127" -> DECLINED, "Incorrect security code."
    "4000000000000119" -> DECLINED, "The card could not be processed. Try another card."
```

A number that passes Luhn but is not in this map is **refused**, with a message
naming the test numbers. "A real card can never be accepted" is therefore a
property of the code, not a promise in a comment. The checkout form shows the
same list in a `<details>` block, so the customer is never guessing.

The validation is a whitelist precisely because the alternative — Luhn plus a
"this is a demo" warning — is one checkbox away from accepting a live card.

### The PAN is unreachable downstream

`CardValidator` returns a `CardDetails` record:

```java
public record CardDetails(String brand, String last4, String outcome, String message)
```

There is no field that can hold a full card number. The number is read in
exactly one place — a local variable in `CheckoutServlet` — passed to
`validate`, and then it is gone. Nothing downstream can leak it because nothing
downstream can reference it.

Supporting properties, all verified rather than assumed:

- Nothing in `src/main/java` calls `getParameterMap` or `getParameterNames`, so
  there is no bulk parameter logging anywhere.
- The request-audit filter records no parameters.
- Tomcat has no `accessLogPattern`, so card fields never reach an access log.
- Card data travels only in the POST body, never in a URL.
- Confirmed absent from `payments`, `audit_logs`, and `catalina.out`.

## What is stored

| Column | Type | Content |
|---|---|---|
| `payments.card_brand` | `VARCHAR(20)` | `Visa` |
| `payments.card_last4` | `VARCHAR(4)` | `4242` |
| `payments.transaction_id` | `VARCHAR(64)` | `DEMO-4242-C0641D` in simulation |

`card_last4` being `VARCHAR(4)` is the **primary** limit, not a stylistic
choice: inserting a full PAN fails with MySQL error 1406, *Data too long for
column*. There is also a `CHECK` constraint, `chk_payments_card_last4`, which is
a **backstop only** — it cannot fire while the column is `VARCHAR(4)`, and earns
its place only by surviving a future widening of that column. An earlier claim
that the constraint was the enforcing mechanism was wrong; the verification had
been invalidated by a foreign-key failure on a non-existent order. The check was
re-run inside `START TRANSACTION … ROLLBACK` against a real order and the
rejection is the column length.

`CardSchemaSafetyTest` guards both facts: the column type and the existence of
the constraint.

## Order state on a decline

A declined card leaves the order **`PENDING`**, never `CANCELLED` or `FAILED`.
The customer can retry. The failed attempt *is* recorded — as a `payments` row
with status `FAILED` — so declines are visible to an administrator.

The attempt is inserted as `PENDING` first and promoted to `PAID` by
`markPaid` within the same transaction. Inserting it as `PAID` up front would
leave a `PAID` payment attached to an unpaid order if settlement failed
afterwards.

`markPaid` takes three arguments — `(orderId, transactionId, note)` — and the
ABA call site passes `"ABA Payway (" + transactionId + ")"` as the note.

## The status pages are read-only

`CardPaymentServlet` and `AbaPaymentServlet` only *display* an outcome. A POST to
either redirects; neither mutates state. `CardPaymentServlet` resolves
`/payment/card/<orderId>` and checks the order belongs to the session user
before rendering — an order id belonging to another customer returns 302 rather
than rendering someone else's order.

## Method normalisation

`PaymentService.normaliseMethod` maps submitted values to the three canonical
methods and accepts `visa`, `card`, and `VISA_CARD` as aliases for `visa`. It is
the single gate: the checkout servlet does not compare raw form strings
anywhere, and a tampered `paymentMethod` that is not one of the three is
rejected before an order is created.

## Adding a provider

The design intends a fourth provider to be cheap, and three things make that
true:

1. Checkout panels are found by `data-payment-panel="<value>"`, not by id, so
   adding one is a markup change rather than a JavaScript change.
2. The options grid is `repeat(auto-fit, minmax(210px, 1fr))`, so the layout
   adapts to however many options configuration allows.
3. Availability is a per-provider predicate, so the option set is configuration,
   not a hard-coded list.

One thing is **not** free: see
[../development/source-linting.md](../development/source-linting.md#the-required-in-a-hidden-panel-trap)
for the trap that a statically `required` field inside a hidden panel puts the
next provider's author in, and `PaymentPanelRequiredTest`, which fails the build
if they fall into it.
