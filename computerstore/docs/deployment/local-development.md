# Local development

Target environment as actually configured on this machine: **Java 17**,
**MySQL 8.4.10**, **Apache Tomcat 11.0.26** at `/opt/tomcat`, context path
`/computerstore`.

## Prerequisites

- JDK 17 on `PATH` (`java -version`).
- Maven. All builds are offline (`mvn -o`) because the local repository already
  has every dependency.
- MySQL 8.4 running and reachable, with a database named `computer_store`.
- A user with DDL rights on that database, for migrations.

## Database

Create the database and a user, then let the migration runner create the schema
on first boot:

```sql
CREATE DATABASE computer_store CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'store_user'@'localhost' IDENTIFIED BY '<password>';
GRANT ALL PRIVILEGES ON computer_store.* TO 'store_user'@'localhost';
```

The runner needs DDL rights on first start. If the user has only DML rights the
app boots and then fails on the first query — `store_user` in this environment
cannot create a scratch database, which is why every test run uses the real one.

## Credentials

Credentials are **environment variables only**. `.env` is untracked and
git-ignored, and there is no committed fallback with real values.

```
DB_URL=jdbc:mysql://localhost:3306/computer_store?...
DB_USERNAME=...
DB_PASSWORD=...
DB_POOL_MAX=50
DB_POOL_MIN=10
```

`DB_URL` is the full JDBC URL, not a database name. To extract the database name
from it:

```bash
echo "$DB_URL" | sed -E 's#.*/([^/?]+).*#\1#'   # -> computer_store
```

`.env.example` is committed and documents the shape without values.

For a shell session, source it:

```bash
set -a; . ./.env; set +a
```

Tomcat does **not** read `.env`. See
[configuration.md](configuration.md#giving-tomcat-the-environment).

## Secrets you must supply yourself

These are never committed and have no default:

| Setting | Needed for | Without it |
|---|---|---|
| `db.password` in `config/db.properties`, or `DB_PASSWORD` | Everything | The app will not start. |
| `payment.aba.secret` in `app_settings` | Live ABA Payway | ABA is withheld from checkout; simulation still works. |
| `COMPUTERSTORE_2FA_ENCRYPTION_KEY` | Two-factor setup | 2FA provisioning refuses rather than storing a plaintext secret. |

`payment.card` has **no** secret, because there is no acquirer integration. See
[../architecture/payments.md](../architecture/payments.md).

## Build and run

```bash
mvn -o clean package
/opt/tomcat/bin/startup.sh
```

Then open <http://localhost:8080/computerstore/>.

Health check:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/computerstore/
```

## Seeding a usable catalogue

Migrations create the schema; they do not put anything in it. A fresh database
has an empty `products` table and the storefront shows an empty catalogue.

`src/main/resources/db/seed/seed-data.sql` is a complete bootstrap. It is **not**
applied automatically — it is a manual step, deliberately, so a deploy never
resurrects seeded rows over real data.

It contains:

| Table | What |
|---|---|
| `brands` | 15 |
| `categories` | 15 |
| `products` | **149** across two `INSERT` statements |
| `users` | 2 — an `admin` and one other, both with the hash that was current when the seed was written |

```bash
mysql -h localhost -u "$DB_USERNAME" -p "$DB" < src/main/resources/db/seed/seed-data.sql
```

**Change both seeded passwords before the instance is reachable by anything
else.** The hash in the file is a real, published BCrypt hash.

The statements name their columns explicitly but are **not** `INSERT IGNORE`, so
re-running the file against a populated database fails on duplicate keys. It is a
one-shot bootstrap, not something to re-apply after a partial failure — check
what landed and insert the remainder by hand.

### The seed is one product short of the live database

The live database has 150 products; the seed has 149. The row that exists only in
the database is `LAP-ACER-S233` (Acer Swift 5 Ultrabook), so a freshly seeded
install 404s on that product's detail page. It is worth reconciling — see
[../security/known-issues.md](../security/known-issues.md).

## Things that will waste your time if you do not know them

- **A proxy is configured for this machine.** `curl` needs `--noproxy '*'` to
  reach `localhost:8080`, and headless Chrome needs `--no-proxy-server`.
  Without it, an iframe to a same-origin page reports itself as cross-origin and
  fails confusingly.
- **Never `pkill -f catalina`.** Use `shutdown.sh` then `startup.sh`. A `kill -9`
  leaves the work directory half-written and the next deploy fails in a way that
  looks like a code problem.
- **Do not delete and recreate a database user while Tomcat is running.** The
  running container keeps serving the old `user_id`, so every cart insert fails
  on `fk_cart_user` even though login appears to work. Restart Tomcat, or reuse
  the user.
- **`mysql -B` still prints headers on this machine.** Use `--skip-column-names`
  for scripts.
- **JSP compilation errors land in `/opt/tomcat/logs/catalina.out`**, not
  `error.log`. A page that 500s with no stack trace in the usual place is a JSP
  problem until proven otherwise.
- **Deploy hygiene:** always `mvn -o clean package`, then
  `rm -rf /opt/tomcat/work/Catalina/localhost/computerstore` **before** copying
  the new WAR. Reversing the order silently deletes the new WAR and yields 404.

## Related

- [tomcat-deployment.md](tomcat-deployment.md) — shipping a build.
- [configuration.md](configuration.md) — every setting, in one table.
- [../development/building-and-testing.md](../development/building-and-testing.md) —
  running the tests.
