# Source linting

Parts of this application are not compiled, and a mistake in them is not a
build failure — it is a runtime failure in production, on a page a customer is
looking at, after a deploy that reported success.

There are three such places, and each has a test that reads the source and
asserts the property the compiler would have caught:

| Uncompiled thing | Fails only at | Lint |
| --- | --- | --- |
| JSP views | request time, by Jasper | `JspTagBalanceTest` |
| Repository SQL and columns | first query that touches them | `RepositoryColumnCoverageTest` |
| `web.xml` | container startup, on deploy | `AsyncSupportDescriptorTest` |

None of these is a proper substitute for the real thing — a lint does not
render the page, does not run the query, and does not start a container. What
it does is fail in `mvn test` instead of in production, and it fails *with the
reason attached*, which is the part that matters when a config error has taken
the site down.

The recurring lesson is the same for all three: **a bug that no test can see
is a bug that ships.** Each lint below is self-testing — it asserts that its own
detector fails on the real bug it was written for — so a lint cannot quietly
become a no-op.

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

Hand-written JDBC means a renamed column is a runtime `SQLException`, not a
compile error. The test asserts that the column names the repositories declare
still exist in the database. See
[../database/schema.md](../database/schema.md).

## `AsyncSupportDescriptorTest`

`web.xml` is read once, at container startup. Nothing in `mvn package` looks at
it, so a filter chain that forbids `startAsync()` deploys cleanly and 500s on
every SSE request.

`/realtime` did exactly that. `RealtimeStreamServlet` is declared
`@WebServlet(asyncSupported = true)`, which is necessary and not sufficient:
the spec lets `startAsync()` proceed only if **every filter in the chain is
async-capable**, and `/realtime` matches `/*`, so its chain is all ten filters
mapped to `/*`. Two of them — `SupportChannelFilter` and `ReviewCountFilter` —
had no `<async-supported>` declaration, and one omission fails the whole
request.

The two existing realtime tests drive the servlet object directly and never read
the descriptor, so both passed against a `web.xml` that made the deployed
endpoint return 500. Configuration is not compiled.

The test reads the descriptor and asserts three things: every `<filter>`
declares it (Tomcat's location), every `<filter-mapping>` declares it (the
specification's location), and no filter on the chain of any
`asyncSupported = true` servlet is missing it. The third is the one that states
the reason, so a failure explains the outage instead of just naming a tag.

### The two elements are not interchangeable

This is the part that was got wrong the first time, in the documentation as
well as in the descriptor:

- The Servlet specification puts `<async-supported>` on the
  **`<filter-mapping>`**.
- **Tomcat ignores it there.** Tomcat builds a `FilterDef` from the
  **`<filter>`** element, and `ApplicationFilterChain.findNonAsyncFilters()`
  checks `FilterDef.getAsyncSupportedBoolean()`. Established by disassembling
  `ApplicationFilterChain` from Tomcat 11's `catalina.jar`, not inferred from
  the spec. Tomcat's own `conf/web.xml` template shows the element on
  `<filter>` too.

So the descriptor declares it on both. Applying the specification's location
alone reproduces the 500 — that was tried before the real cause was found.

### Use a parser, not a regex

This test reads `web.xml` with `DocumentBuilder`, and that is not incidental.
The page documenting the rule contains the literal text `<filter>` and
`<filter-mapping>` in prose, inside an XML comment. A regex over the raw file
counts those as real elements and mis-pairs every block after the first.

This is not hypothetical. A hand-written regex check of this descriptor reported
**18 filters and 17 mappings** against an actual DOM count of **15 and 15**.
`DocumentBuilder` skips comments; a regex does not.

## `CssGutterInvariantTest`

`theme.css` had a "responsive comfort pass" that narrowed `.container`'s
`--bs-gutter-x` on small screens to win back content width on a phone. It
changed the container alone.

That is not a safe thing to change on its own. A Bootstrap row pulls itself
outside its container by half its own gutter:

```css
.row { --bs-gutter-x: 1.5rem; margin-inline: calc(var(--bs-gutter-x) / -2); }
```

and the container's horizontal padding is the only thing absorbing that pull. The
two are locked together:

```text
container padding-x  ==  container --bs-gutter-x / 2  >=  row gutter / 2
```

Bootstrap's defaults satisfy it exactly — a 1.5rem container gutter gives
0.75rem of padding, which absorbs `g-4`'s 1.5rem row. The comfort pass broke the
lock: at ≤767.98px the container gutter dropped to `1rem` (8px of padding) while
`g-3`/`g-4` rows kept their own gutter, so rows hung outside the page by 4px.

Measured in Chrome at a 390px viewport, on every page: `scrollWidth` **394**
against a 390px viewport, from the home page to `/account/orders`. The fix is
three rules that clamp the row gutter to the container gutter at each breakpoint,
scoped as `.container .row` so the specificity (0,2,0) outranks `.g-3`/`.g-4`
(0,1,0).

The test reads the app stylesheets and asserts that **every media block which
narrows a container gutter also declares a row gutter no larger than it, in the
same block**. That is exactly the edit that fixes the overflow, which is what
makes the rule specific enough not to fire on unrelated CSS.

It has three parts, and the other two are the point:

- **The real stylesheet is checked**, not a fixture. Removing the three clamp
  lines from `theme.css` fails the build and names the offending values
  (`narrows .container to 1.35rem but declares no row gutter clamp`), which is
  the defect re-created against the real file rather than a toy.
- **The detector self-tests on the exact broken shape** — a missing clamp, and a
  clamp that is present but still too small, which is the same bug wearing a
  disguise.
- **Unsupported values fail loudly.** If a gutter is authored in `px` or
  `calc()`, the comparison would skip it and the test would pass without having
  looked at anything. That case is reported rather than skipped, so the check
  cannot succeed by not understanding the file.

Two things this lint is honest about. It compares in `rem`, which is only valid
because the app is `16px` root and both sides are authored in `rem` — hence the
explicit failure on other units. And a static source check is a proxy: the real
failure is a computed style, and the browser is the only thing that can see it.
See [Responsive audit](#responsive-audit) below for the measured run.

### Classify `.container .row` as a row, not a container

Worth recording, because the first version of this test was wrong and passed for
the wrong reason. `.container .row` matches both the "is a container" and the
"is a row" predicate, so each clamp rule was counted as a *container*
declaration too. "Widest container gutter" then included the clamp itself, the
comparison was the clamp against itself, and a clamp that was present but too
small to absorb the rows passed. The self-test caught it; the real stylesheet did
not, because there the two values are equal. A predicate must be mutually
exclusive before a `max()` over it means anything.

## `AdminNavVisibilityTest`

A second CSS lint, and a different kind of trap from the gutter one: the
complementary half of an overflow. The gutter test stops the page scrolling
sideways; this one stops the overflowing region becoming *invisible*.

The admin nav is thirteen links in a `flex-row flex-nowrap` row inside an
`overflow-x: auto` container, and it overflows at **every** width — measured at
1453px of links against an 823px port on a 1280px laptop. Two things had to be
true for the nav to work, and both were false:

1. **The active link has to be scrolled into view.** The `active` class was
   applied correctly on all thirteen pages, so this looked fine in the HTML —
   and it was, which is what made it a slow bug. What was wrong was that on 6 of
   the 13 pages the active link began past the right edge of the port, so the
   highlight was never on screen. Dashboard is link 1, so it is the one link
   always visible, which made *every* page look like Dashboard was selected.
2. **The scrollbar must stay visible.** It was `scrollbar-width: none`, so 630px
   of navigation was not merely off screen, it was off screen with no hint that
   it existed. A user reported the nav as "always showing Dashboard".

The lint checks the source-level half of each: the fragment must load
`admin-nav.js` (which sets `scrollLeft` on load), and no rule may hide the admin
nav's scrollbar. It also requires the scroller class name to appear in the CSS
*and* the JS, because renaming one without the other would silently stop the
reveal.

`scrollbar-width: none` is rejected; `scrollbar-width: auto` is not. The
invariant is "not hidden", not "must be thin" — leaving the property alone falls
back to a visible bar, which satisfies the requirement.

Measured after the fix, real browser, all thirteen admin pages:

| viewport | port | content | hidden | active link off-screen |
|---|---|---|---|---|
| 1024px | 775px | 1453px | 678px | 0 / 13 pages |
| 1280px | 823px | 1453px | 630px | 6 → **0** / 13 pages |
| 1440px | 1003px | 1453px | 450px | 4 → **0** / 13 pages |

### A fixed browser port makes you "verify" a stale copy

A harness bug worth writing down, because it reported a working fix as broken
and would otherwise have had me debugging correct code.

Every probe script launched Chrome with a fixed `--remote-debugging-port=9222`.
A Chrome from a *previous* run was still alive holding that port, so the new
launch failed to bind, the script silently connected to the old instance, and
that instance served `admin-nav.js` and `layout.css` **from its warm HTTP
cache** — pre-fix copies. The symptoms were precisely misleading: `EDGE_SLACK`
present 0 times in the JS the page had actually loaded, and `scrollbar-width`
still computing to `none`, while `curl` on the same URLs returned the new code
three times over. A separate bug in the same script compared
`location.pathname` against a full origin+path URL, which fails on every page.

Two rules came out of it:

- **Randomise the CDP port per run**, or a stray browser silently becomes your
  test fixture.
- **Never `pkill -f remote-debugging-port=...`.** The pattern matches the command
  line of the very shell running it, so `pkill` SIGTERMs its own caller and the
  script dies with no output at all. Match on the binary and kill by PID.

The lesson underneath both: when a browser measurement disagrees with `curl`
about the same URL, suspect the measurement before editing the source.

## `CacheStatsHitRateTest`

Not a source lint — this one runs the real class — but it guards the same kind
of mistake, so it is listed here.

Reading `/admin/performance` in a real browser turned up two rows in the cache
table reading `0 hits, 0 misses, 100.0%`. They were the `details` and `orders`
caches, which have genuinely never been asked anything. A 100% hit rate for an
unused cache is not a small inaccuracy: it is wrong in the one direction that
stops anyone looking, and it is visually identical to a cache working perfectly.

The cause is upstream. Caffeine's `CacheStats.hitRate()` returns exactly `1.0`
when `requestCount()` is `0`, and `CacheManager.addStats` passed it straight
through. `CacheManager` now reports no rate at all until there is at least one
request, and both the JSP and `performance-live.js` render an em dash.

Three things this test is careful about:

- **`rate()` in the live script had the same bug, in mirror image.**
  `Number(null)` is `0` and `Number('')` is `0`, so a null rate would have
  rendered `0.0%` on every 15-second refresh while the first server-rendered
  paint showed an em dash — the same row changing meaning under the reader. The
  guard has to come *before* the numeric coercion.
- **The upstream behaviour is pinned**, not assumed. One test builds a real
  Caffeine cache, never touches it, and asserts `hitRate() == 1.0`. If a
  Caffeine upgrade ever changes that to `0.0`, the test fails and the guard can
  be reconsidered instead of lingering to mask behaviour that no longer exists.
- **The test was seen red.** Removing the one-line guard from `addStats` makes
  it fail by name, naming the cache and the offending value. A lint that has
  never been seen failing is not known to bite. `CacheManager.java` was restored
  byte-identical afterwards (verified by `md5sum`).
- **Fixtures call `recordStats()`, and it is not optional.** Caffeine 3.1.8 leaves
  statistics recording *off* unless `recordStats()` is asked for, so a fixture
  built with a bare `Caffeine.newBuilder().build()` reports `0` hits and `0`
  misses for ever and tests nothing. Confirmed in `jshell` against 3.1.8:
  `get(k, fn)` and `getIfPresent(k)` both record nothing without it, and
  `hitCount=2, missCount=1` with it. All nine caches in `CacheManager` do call it,
  so the live panel is unaffected — but the fixtures had to as well, or the test
  that pins Caffeine's `1.0` default would have been asserting that an *unrecorded*
  cache reads `1.0`, which is a different and much weaker claim.
- **The test does not read the shared static caches.** `CacheManager`'s nine
  caches are `static` and surefire reuses one JVM for all test classes, so an
  earlier version of this test asserted "at least one cache has 0 requests" and
  passed only because nothing else touched them. Any future test that merely read
  a cached product would have turned that into a spurious failure in an unrelated
  class. It now drives `addStats` with caches the test owns, which also covers the
  direction the original did not: that a *used* cache still reports a real rate,
  so the guard cannot be satisfied by never reporting one at all.

## `AssetsVersionCoverageTest`

`header.jspf` computes `assetsVersion` as the newest last-modified time across a
hand-maintained array of paths, and every asset reference appends it as
`?v=${assetsVersion}`. The value is cached in application scope, so it is
computed once per deploy.

That only works if the array covers everything referenced with the token, and it
did not. `admin-nav.js` and `pwa.js` were added in this change, referenced with
`?v=`, and absent from the array; eight further assets carried no token at all.
Both failures are silent and both are permanent. The browser and the service
worker key on the full URL including the query string, so a file missing from the
array keeps the same `?v=` across a deploy that changes only that file, and the
stale bytes are served until a hard reload. The file on disk is correct and the
page is wrong, which is close to the hardest kind of bug to read.

The test checks both directions, so neither the array nor a reference can drift
alone: every `.js`/`.css` reference in a `src` or `href` attribute must carry the
token, must appear in the array, and the array must not list a path that does not
exist. Only tag attributes are matched, so the array's own string literals in
`header.jspf` are not mistaken for references — the first draft did match them and
failed on the array itself.

Two things this test is careful about:

- **Attribute-scoped, not bare-path matching.** An earlier version matched any
  `/assets/...` string, which caught the array entries. Scoping to `(?:src|href)=`
  is both correct and closer to the thing that actually ships.
- **The context-path EL is stripped before comparing.** References read
  `${pageContext.request.contextPath}/assets/js/app.js`, so the value is
  normalised to the path from `/assets/` before it is looked up in the array.

Both directions were seen red: deleting `"/assets/js/pwa.js",` from the array, and
stripping `?v=` from a `dashboard.jsp` script tag. Both files were restored
byte-identical afterwards (verified by `md5sum`).

## Responsive audit

The fix above was found by measurement, not by reading CSS. Headless Chrome
cannot be resized to a phone width with `--window-size`: `--headless` and
`--headless=new` both clamp to 500px, and
`--force-device-scale-factor=2 --window-size=780,1688` yields a 780px layout
viewport at dpr 2. Only CDP's `Emulation.setDeviceMetricsOverride` produces a
true 390px viewport, so the audit drives Chrome over the DevTools protocol
(`Page`, `Runtime`, `Network`) and evaluates the measurement in the page.

Per page it records `scrollWidth` against the viewport, every element whose
right edge exceeds the viewport, and interactive elements below the
[WCAG 2.2 §2.5.8](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum)
24×24px minimum. Current state at 390px: no horizontal overflow on any of the
fifteen storefront, account, checkout and admin pages.

The target-size metric needed a correction worth repeating. The first version
used a 40px height threshold and reported 92 undersized targets on the home page
and **383** on `/admin/products` — numbers that looked alarming and were mostly
false positives: `label.visually-hidden` at 1×1 (not perceivable, so not a
target) and `th.sortable` at 37px tall but 109px wide. Height alone ignores area.
The shipped rule excludes visually-hidden elements, applies the §2.5.8 exemption
for targets inside a sentence or block of text, and requires **both** dimensions
to fall short. That reduced the same pages to 16 and 6, and the remainder are
genuine but marginal — inline spec-text links 19px tall, and 25×23 `btn-link`s on
`/admin/inventory`. There is no systemic tap-target defect to fix here, and
padding every one of those out to 44px would have been a large change to the
design in pursuit of a number the pages were never failing.

## Adding a lint

1. Put it where the thing it reads lives: `web.view` for JSP,
   `core.repository` for SQL, `web.config` for the descriptor and the app CSS.
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
