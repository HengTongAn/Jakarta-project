# Building and testing

## Build

```bash
mvn -o clean package
```

`-o` (offline) is the normal invocation — every dependency is already in the
local repository. `package` produces `target/computerstore.war`.

Skip tests for a deploy build only if you are deploying to a machine where you
will verify manually afterwards. Locally, always run them.

## Test

```bash
mvn -o test                          # all 194
mvn -o test -Dtest=CardValidatorTest # one class
mvn -o test -Dtest='PaymentPanelRequiredTest#noPaymentPanelContainsAStaticallyRequiredControl'
```

**194 tests, 34 test classes, zero failures.** Reports land in
`target/surefire-reports/`.

## What the tests cover

| Area | Classes |
|---|---|
| Filters | `RateLimitingFilterTest`, `UserRateLimitingFilterTest`, `AuthenticationFilterActivityTest`, `CompressionFilterTest`, `StaticResourceCacheFilterTest` |
| Security primitives | `PasswordUtilTest`, `CSRFUtilTest`, `TwoFactorAuthServiceTest` |
| Payment | `CardValidatorTest` (17), `PaymentServiceMethodTest`, `PaymentStatusTest`, `CardSchemaSafetyTest` |
| Schema safety | `RepositoryColumnCoverageTest`, `DatabaseMigrationRunnerTest`, `CardSchemaSafetyTest` |
| View lint | `JspTagBalanceTest`, `PaymentPanelRequiredTest` |
| Services | `CartServiceTest`, `OrderServiceCheckoutTest`, `ReviewValidationTest`, `SupportChannelServiceTest`, `AuthServiceTest` |
| Servlets | `ErrorServletTest`, `HealthCheckServletTest`, `ReviewServletsTest`, `RealtimeStreamServletTest`, `EventHubDisconnectTest` |
| Utilities | `ValidationUtilTest`, `MiniJsonTest`, `FileUploadUtilTest`, `AppConfigTest` |
| Domain | `ProductStatusTest`, `TimeBoundariesTest` |

Note there are two classes named `RateLimitingFilterTest` in different packages
(`com.hengtongan.computerstore` and
`com.hengtongan.computerstore.web.filter.security`). Use the fully qualified name
to disambiguate.

## Three kinds of test, and the difference matters

**Unit tests** exercise pure logic: `ValidationUtil`, `CardValidator`,
`PasswordUtil`, `MiniJson`, `AppConfig.resolve`. Fast, no container.

**Integration tests** use Mockito for the servlet and filter layer. They verify
that a filter sends the right redirect, that an exception maps to the right
status. They do **not** exercise a real container, a real JSP, or real HTML
rendering.

**Source lints** read the `.jsp` and `.sql` sources and assert a property. These
exist because the failure they catch is otherwise invisible until a customer
sees it. `JspTagBalanceTest` catches an unclosed `c:` tag — a JSP compile error
that is not a build error. `PaymentPanelRequiredTest` catches a `required`
control in a hidden panel — a checkout that silently accepts nothing.
`RepositoryColumnCoverageTest` catches SQL naming a column the database does not
have, which hand-written JDBC cannot catch at compile time.

The lints are described in [view-linting.md](view-linting.md).

## A lint is only useful if it fails on the real bug

Every lint here is self-testing: it has at least one test that feeds it a known
bad input and asserts it reports it, and one that feeds it the fixed input and
asserts it does not. When you add a check, add both.

The claim to distrust is "the test passes" — that is equally consistent with a
detector that does nothing. Prove it catches the real thing:

```bash
V=src/main/webapp/WEB-INF/views/customer/checkout.jsp
cp $V /tmp/co.bak
sed -i 's#data-active-required="true" data-luhn-target="true">#required data-luhn-target="true">#' $V
mvn -o test -Dtest='PaymentPanelRequiredTest#noPaymentPanelContainsAStaticallyRequiredControl'
#   -> FAIL: payment panel "visa" contains required on cardNumber
cp /tmp/co.bak $V
```

If that does not fail, the lint is decoration.

## Tests that need the database

`CardSchemaSafetyTest` and `RepositoryColumnCoverageTest` read the live schema,
so the database must be up and migrated before `mvn test`. They fail with a
connection error rather than a clear message if it is not.

`store_user` in this environment has no DDL rights, so tests cannot create or
drop a scratch database. Every test run uses the real one. That is a real
limitation: a test that writes data touches production-shaped data, and a test
that assumes an empty table will fail once there is a catalogue.

## Writing a test

- Name the class after the unit, `*Test`, in the same package as the unit.
- For a bug fix, write the test that fails **before** the fix. The count of tests
  going up is not evidence; a test that failed first is.
- If the bug was found in a way the test could not see, say so in the class
  comment and explain what the new test *does* cover. A test that passes both
  before and after the fix is worse than no test, because it reads like
  coverage.
- Use `assertTrue` with a message that says what to do, not just what failed.
  A failing assertion should tell the next person how to fix it.

## Before deploying

```bash
mvn -o clean package     # runs the tests
```

Then follow [../deployment/tomcat-deployment.md](../deployment/tomcat-deployment.md),
and verify with a real browser if the change touched a form.
