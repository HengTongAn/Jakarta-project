# View linting

JSP is compiled by Tomcat at request time, not at build time. So **every JSP
mistake is a runtime error in production**, on a page a customer is looking at,
after a deploy that reported success. There is no compiler in `mvn package` to
catch it.

Three tests in `src/test/java/com/hengtongan/computerstore/web/view/` exist to
close that gap. They read the source and assert properties, which is not a
proper substitute for rendering the page — but it is a substitute that runs in
`mvn test` instead of in production.

## `JspTagBalanceTest`

**What it catches:** an unclosed or mismatched `c:` tag, which makes Jasper
throw at compile time.

```xml
<div data-payment-panel="visa" class="d-none">
    <c:if test="${cardAvailable}">
        ...
    </div>          <!-- <c:if> never closed -->
```

On this Tomcat the diagnostic is actively misleading. A missing `</c:otherwise>`
reports `end tag is unbalanced`, pointing at the wrong construct. The failure
names a tag that is fine.

**Why it is worth the effort:** this shipped. The `checkout.jsp` card panel had
a `</c:forEach}>` where a `</c:if>` belonged, and it was only discovered by
reading the file. `JspTagBalanceTest` is self-testing — it asserts that the
detector fails on both real bug variants — so it cannot silently become a
no-op.

## `PaymentPanelRequiredTest`

**What it catches:** a `required` control inside a payment panel that gets
hidden when another method is selected.

This is the one worth reading in full, because the bug is invisible in a way
that is worth understanding.

### The `required`-in-a-hidden-panel trap

Every payment method shares one `<form>`. Each method's extra inputs live in a
`data-payment-panel` div that the page hides with Bootstrap's `d-none`, i.e.
`display: none !important`, when the customer picks a different method. The
JavaScript toggles that one class and nothing else.

So the card panel looked like this:

```html
<div data-payment-panel="visa" class="d-none">
  <input id="cardName"   name="cardName"   required>
  <input id="cardNumber" name="cardNumber" required>
  <input id="cardExpiry" name="cardExpiry" required>
  <input id="cardCvv"    name="cardCvv"    required>
</div>
```

and the consequence is that enabling card payment **silently disabled ABA Payway
and cash on delivery**. The customer selected one of them, pressed Place order,
and nothing happened. No error, no message, no server log entry.

### Why

HTML5 constraint validation does not exempt a control for being
`display: none`. Being rendered is not part of the definition of a "candidate for
constraint validation" — only `disabled`, `type="hidden"`, and a handful of
other conditions remove a control from it. So a `required` input inside a hidden
panel is still validated, the browser refuses to submit the form containing it,
and it **cannot show an error** because the offending control cannot be focused
to display a message on.

A browser is not a place where a silent no-op is acceptable. This is the worst
failure mode available: the customer believes checkout is broken and has no way
to learn why.

### Measured in Chrome, on the real deployed page

Before the fix, with card payment enabled and all three methods offered:

```
cardNumber.required.onLoad = true
CASH_selected.formValid = false    submitFires = false
ABA_selected.formValid  = false    submitFires = false
```

Note the second line. Selecting **cash on delivery explicitly** also failed. The
customer actively choosing the method that should work did not help, because
the required fields belonged to a panel they had switched away from.

### The fix

The markup no longer ships `required`. It opts in:

```html
<input id="cardNumber" name="cardNumber" data-active-required="true" data-luhn-target="true">
```

and `initCheckoutPayment` applies `required` only to the panel that is showing:

```js
function syncRequired(allPanels, activeValue) {
    allPanels.forEach(function (panel) {
        var active = panel.dataset.paymentPanel === activeValue;
        panel.querySelectorAll("[data-active-required]").forEach(function (field) {
            field.required = active;
        });
    });
}
```

The opt-in marker matters. A blanket "strip `required` from hidden panels" rule
would silently break a field in a panel that genuinely must always be required.
Only fields that ask for it are touched.

Server-side validation remains the actual gate in every case. This is a usability
affordance, not a security control.

### Verified after the fix, same method, real page

```
methods_offered                = aba,visa,cash
ABA_selected.formValid         = true    submitFires = true
CASH_selected.formValid        = true    submitFires = true
VISA_selected_empty.formValid  = false   submitFires = false
VISA_selected_filled.formValid = true    submitFires = true
backToCash.cardNumber.required = false
CASH_after_card_typed.formValid = true   submitFires = true
```

Read the fourth line: an empty card is still correctly blocked, and its error
*is* displayable because the panel is visible. The last two lines: switching back
to cash releases `required` and the form submits, even with a card number still
in the field.

### The lesson, which is the real content of this page

Twenty-eight server-side behavioural tests passed while this was broken. Every
one of them posted the form directly. **`curl` does not implement HTML5
constraint validation** — it posts the form regardless — so the server received
exactly the request it would have received from a working browser. The server
contract was verified exhaustively and the browser contract not at all.

If a change touches a form, load the page in a real browser and press the
button. `curl` cannot tell you that a customer cannot.

## `RepositoryColumnCoverageTest`

Not a view test, but the same idea. Hand-written JDBC means a renamed column is
a runtime `SQLException`, not a compile error. The test asserts that the column
names the repositories declare still exist in the database. See
[../database/schema.md](../database/schema.md).

## Adding a lint

1. Put it in `com.hengtongan.computerstore.web.view` (or
   `core.repository` for SQL).
2. **Self-test it.** At least one test feeding a known bad input and asserting
   detection, and one feeding the fixed input and asserting silence. A lint that
   cannot fail is worse than no lint, because it appears in the build output.
3. **Make the failure message say what to do.** The current one names the
   offending element, explains why it breaks, and gives the fix:

   > These controls are required but sit in a payment panel that gets hidden
   > when another method is selected. The browser will refuse to submit the form
   > and cannot show an error on an invisible control, so choosing another
   > payment method silently does nothing. Use `data-active-required="true"`
   > instead; `initCheckoutPayment` applies required only while the panel is
   > active.
4. **Write the class comment as an explanation of the defect**, not a restatement
   of the class name. These comments are the actual documentation; a future
   maintainer reads the comment and not the test.
5. **Verify it against the real file**, not just a fixture. Reintroduce the bug
   with `sed`, confirm the test fails, restore. See
   [building-and-testing.md](building-and-testing.md#a-lint-is-only-useful-if-it-fails-on-the-real-bug).

## JSP gotchas on this Tomcat

Accumulated the hard way. All of these cost real debugging time.

| Symptom | Cause |
|---|---|
| "end tag is unbalanced" naming a correct tag | A different `c:` tag is actually unclosed. |
| Markup inside `${...}` in template text never renders | It is parsed as a real tag, not evaluated. Build the value first. |
| `<c:choose>` inside a tag attribute value | Not allowed. Compute before the tag. |
| `${order.paid}` empty for a record or an `isX()` accessor | `isX()` is not resolvable as property `x`. Use `<c:set>` with string coercion, or add a `getX()`. |
| A page returns 500 with no stack trace in `error.log` | JSP compile error. The trace is in `/opt/tomcat/logs/catalina.out`. |
| A hidden field still validated | See `PaymentPanelRequiredTest` above. |
