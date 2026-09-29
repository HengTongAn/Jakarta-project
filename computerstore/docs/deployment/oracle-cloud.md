# Deploying to Oracle Cloud Always Free

The one free host that runs this stack **as it is**. It gives you a real Linux VM
with root, so Tomcat and MySQL go on it exactly as they do on your laptop. No
code changes, no containerisation, no database swap.

Verified stack: **Java 25 (LTS, project targets 17)**, **Apache Tomcat 11.0.26**,
**MySQL 8.4.10**, context path `/computerstore`.

---

## Read this before you create anything

**The A1 allowance has already been cut once.** Oracle halved the Always Free
Ampere A1 entitlement from 4 OCPU / 24 GB to **2 OCPU / 12 GB** in July 2026,
without announcement. The free tier is not a contract. Check the current
allowances in the console before you rely on any number here.

**"Out of capacity for shape" is the common failure.** Free-tier regions fill up,
and the A1 instance type is the first to be unavailable. If creation fails, it is
almost always this — not your account. Work down the region list until one
accepts the shape. Each attempt is free; a failed create costs nothing.

---

## Step 0 — a warning that only matters once you click "Create VM"

**The seed file ships working credentials.**

```sql
('admin',    '...', 'System Administrator', 'admin@computershop.test', 'SUPER_ADMIN')
('customer', '...', 'Jane Customer',        'jane@computershop.test',  'CUSTOMER')
```

Those hashes verify against the published plaintexts `admin123` and
`customer123`. Your **local** database has already rotated them — I confirmed
`admin/admin123` is rejected there. A **fresh VM seeded from `seed-data.sql`
will not have been rotated**, and it will be reachable from the internet.

`DefaultCredentialsChecker` detects this at startup and logs an `ERROR` naming
the account, but it **only logs** — startup continues and the login works. On a
laptop you read that line. On a public host it scrolls past in a journal nobody
is watching.

So: **change both passwords before you point a domain at the VM**, and do it from
the UI or by generating your own BCrypt hashes. Do not rely on the log line.

---

## Step 1 — pick the OS: Oracle Linux 9, not Ubuntu

This is the decision that saves you an afternoon, and the reason is MySQL.

The Always Free A1 shape is **ARM (aarch64)**. Your app is verified against
**MySQL 8.4**. On Ubuntu 24.04 aarch64, `dnf`/`apt install mysql-server` gives you
**8.0.39** — not 8.4 — and the MySQL APT repository's 8.4 arm64 packaging is not
reliable. The generic 8.4 ARM64 tarball exists but is a manual install.

**Oracle Linux 9 aarch64 ships a native MySQL 8.4 Community repository.** One
`dnf install` and you are on the version the app was tested against.

| OS | MySQL 8.4 on ARM | Verdict |
|---|---|---|
| **Oracle Linux 9 aarch64** | native repo | **Use this** |
| Ubuntu 24.04 aarch64 | 8.0.39 via apt, or manual 8.4 tarball | works, more work, unverified version |
| Ubuntu 24.04 **amd64** (E2.1.Micro) | apt gives 8.0.39 too | 1 GB RAM, cramped — see fallback |

### Fallback if no A1 capacity is available

Always Free also includes **2 × VM.Standard.E2.1.Micro** — x86, so nothing about
the stack changes, but each is **1 GB RAM**. Tomcat plus MySQL 8.4 in 1 GB
requires tuning (`innodb_buffer_pool_size=64M`, `-Xmx256m`). Workable for a demo,
not comfortable. Choose the AMD shape only if A1 is genuinely unavailable.

---

## Step 2 — the VM

- **Shape:** `VM.Standard.A1.Flex`, **2 OCPU / 12 GB** (Always Free total)
- **Image:** Oracle Linux 9, aarch64
- **Boot volume:** 47 GB (Always Free allowance)
- **Networking:** a VCN with a **public subnet** — you need SSH and HTTPS
- Add an **SSH key pair**; do not use a password
- **Reserve a public IP** and assign it, so the address survives a stop/start

---

## Step 3 — Java, Tomcat, MySQL

```bash
# --- Java (Temurin 21 LTS; the project targets 17, runs fine on newer) ---
sudo dnf install -y java-21-openjdk-headless
java -version

# --- Tomcat 11 ---
sudo useradd -r -m -d /opt/tomcat tomcat
sudo curl -fsSL https://archive.apache.org/dist/tomcat/tomcat-11/v11.0.26/bin/apache-tomcat-11.0.26.tar.gz \
  | sudo tar -xz -C /opt/tomcat --strip-components=1
sudo chown -R tomcat:tomcat /opt/tomcat
sudo chmod +x /opt/tomcat/bin/*.sh

# --- MySQL 8.4 (native on Oracle Linux 9 aarch64) ---
sudo dnf install -y mysql-server
mysqld --version          # MUST say 8.4 -- stop here if it says 8.0
```

If `mysqld --version` does not report 8.4, stop and fix it before continuing —
the migrations were written against 8.4.

### Database and least-privilege user

```bash
sudo systemctl enable --now mysqld
sudo mysql <<'SQL'
CREATE DATABASE computer_store CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- A dedicated app user, NOT root.
CREATE USER 'store_user'@'localhost' IDENTIFIED BY '<a real password>';
GRANT ALL PRIVILEGES ON computer_store.* TO 'store_user'@'localhost';
FLUSH PRIVILEGES;
SQL
```

`GRANT ALL` on the one schema, not global. The app runs its own migrations and
needs DDL, so schema-wide is the minimum that works.

---

## Step 4 — migrations and seed

**`computerstore.migration.autoRun` defaults to `false`** and the startup log
calls enabling it *"not recommended for production"*. A fresh database is
therefore **empty** until you apply them, and every page will fail.

```bash
# Migrations first, in order, from src/main/resources/db/migrations/
for f in /path/to/computerstore/src/main/resources/db/migrations/*.sql; do
  echo "  applying $(basename "$f")"
  sudo mysql computer_store < "$f" || { echo "FAILED at $f"; break; }
done

# Then the seed: brands, categories, 149 products, and the two default users.
sudo mysql computer_store < /path/to/computerstore/src/main/resources/db/seed/seed-data.sql

# Verify
sudo mysql -N computer_store -e "SELECT COUNT(*) FROM products;"
# expect 149 -- and remember the 150th (LAP-ACER-S233) exists only in your local DB
```

Note the seed gives you **149** products, not 150. `LAP-ACER-S233` (Acer Swift 5
Ultrabook) lives only in your local database and in no seed or migration — see
[../security/known-issues.md](../security/known-issues.md#6-a-fresh-install-has-149-products-not-150).

---

## Step 5 — deploy the WAR

Build locally, deploy from your machine:

```bash
mvn -o clean package          # target/computerstore.war

scp target/computerstore.war tomcat@<your-public-ip>:/tmp/
ssh tomcat@<your-public-ip> 'sudo systemctl stop tomcat'
ssh tomcat@<your-public-ip> 'sudo rm -rf /opt/tomcat/webapps/computerstore \
                                        /opt/tomcat/work/Catalina/localhost/computerstore'
ssh tomcat@<your-public-ip> 'sudo mv /tmp/computerstore.war /opt/tomcat/webapps/'
```

**Deleting `/opt/tomcat/work` is not optional.** It caches compiled JSPs and
survives the WAR being replaced; a stale JSP there will serve old markup
indefinitely. This is the single most common way to deploy a fix and then not see
it.

Keep the filename `computerstore.war` so the context path stays `/computerstore`
— that is the layout everything was verified against. Deploying as `ROOT.war` to
serve from `/` also works, since every URL is `contextPath`-derived, but it is a
different configuration from the one that was tested.

---

## Step 6 — the environment, in a systemd unit

**Tomcat does not read `.env`.** Exporting variables in your shell only helps if
that same shell starts Tomcat — which is not true under systemd. Put them in the
unit:

```ini
# /etc/systemd/system/tomcat.service
[Unit]
Description=Tomcat
After=network.target mysqld.service
Requires=mysqld.service

[Service]
Type=forking
User=tomcat
Group=tomcat

Environment="DB_URL=jdbc:mysql://localhost:3306/computer_store?serverTimezone=UTC&characterEncoding=UTF-8"
Environment="DB_USERNAME=store_user"
Environment="DB_PASSWORD=<a real password>"
Environment="DB_POOL_MAX=20"
Environment="DB_POOL_MIN=5"

# The 2FA encryption key belongs here, not in the file above.
Environment="COMPUTERSTORE_2FA_ENCRYPTION_KEY=<a real secret>"

# Refuse to start on a seeded default password. Leave this ON in public.
Environment="JAVA_OPTS=-Dcomputerstore.security.checkDefaultCredentials=true"

ExecStart=/opt/tomcat/bin/startup.sh
ExecStop=/opt/tomcat/bin/shutdown.sh
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now tomcat
sudo systemctl status tomcat
```

`DB_URL` must point at **`localhost`**, not the public IP — going out to the
internet address for your own database will hang behind a firewall.

Never commit this file. The `Environment=` lines are secrets.

---

## Step 7 — HTTPS, which the PWA requires

**Without TLS the PWA does nothing.** `pwa.js` checks `window.isSecureContext`
and bails silently; on plain HTTP over a public IP there is no service worker, no
install prompt, and no offline page. No error, just a dead feature.

Caddy gets a certificate automatically and renews it:

```bash
sudo dnf install -y 'dnf-command(caddy)'   # or the Caddy repo for OL9
```

```caddyfile
# /etc/caddy/Caddyfile
your-domain.example {
    reverse_proxy 127.0.0.1:8080
    encode gzip zstd
}
```

```bash
sudo systemctl enable --now caddy
sudo firewall-cmd --permanent --add-service=http --add-service=https
sudo firewall-cmd --reload
```

Caddy needs port 80 open to solve the HTTP-01 challenge. Leave **8080 closed
externally** — Caddy is the only thing that should reach Tomcat.

---

## Verify before you call it done

```bash
curl -I https://your-domain.example/                       # 200
curl -I https://your-domain.example/manifest.webmanifest   # 200, application/manifest+json
curl -I https://your-domain.example/sw.js                  # 200, text/javascript
```

Then in a browser, on a real phone or a mobile emulator:

- [ ] Site loads over HTTPS, no mixed-content warnings
- [ ] No horizontal scroll at 390px wide
- [ ] Cart badge updates, add-to-cart works
- [ ] `admin` cannot log in with `admin123`
- [ ] Install prompt appears (Android Chrome: ⋮ → Install app)
- [ ] `/realtime` connects — check the console for the `retry: 3000` handshake

**Headless Chrome cannot be resized to a phone width.** `--window-size=390,844`
is silently clamped to 500px, and `--force-device-scale-factor=2
--window-size=780,1688` gives a 780px viewport at dpr 2. Only CDP's
`Emulation.setDeviceMetricsOverride` produces a true 390px viewport. For a quick
check, use a real phone or DevTools device mode.

---

## What this deployment is and is not

**Is:** a working public demo — catalogue, cart, checkout (simulated payment),
accounts, admin, installable PWA.

**Is not:**

- **Sessions are in-memory** and `<distributable>` is not declared. Strictly one
  instance. Every Tomcat restart logs everyone out. Fine for a demo; it is why
  you cannot scale this or put a load balancer in front of it.
- **Rate limiting is per-instance**, so it means nothing until there is more than
  one.
- **The SSE `/realtime` stream** holds a thread per subscriber. A few dozen open
  tabs is fine; it is not built for thousands.
- **No backups.** A VM is a disk, and a disk fails. If the data matters, take
  `mysqldump` on a schedule — there is nothing free that does this for you.
- **Always Free can be withdrawn.** It has already been changed once, in July
  2026.

## Staying free

- **Stop the instance when idle.** Boot volumes and reserved IPs keep billing
  while an instance is stopped. Check the console's cost estimate before you
  create anything.
- **Watch egress.** Always Free includes a monthly transfer allowance, but it is
  finite and the easiest thing to exceed by accident.
- **Check the current allowances** rather than trusting this page — they have
  changed once already.
