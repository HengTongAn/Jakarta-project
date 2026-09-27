# Deploying to Tomcat

Context path is `/computerstore`. Tomcat 11.0.26 at `/opt/tomcat`.

## The deployment sequence, in this order

```bash
cd /home/tim/Desktop/Jakarta-project/computerstore

# 1. Build a clean WAR. Always clean: stale JSPs in target/ survive otherwise.
mvn -o clean package

# 2. Stop the container and wait for it to actually stop.
/opt/tomcat/bin/shutdown.sh
sleep 4

# 3. Remove the old deployment AND the work directory. The work directory is
#    what caches compiled JSPs, and it survives the WAR being deleted.
/opt/tomcat/bin/shutdown.sh 2>/dev/null
rm -rf /opt/tomcat/webapps/computerstore \
       /opt/tomcat/webapps/computerstore.war \
       /opt/tomcat/work/Catalina/localhost/computerstore

# 4. Copy the new WAR.
cp target/computerstore.war /opt/tomcat/webapps/computerstore.war

# 5. Start and wait for it to come up.
/opt/tomcat/bin/startup.sh
for i in $(seq 1 45); do
  sleep 2
  c=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/computerstore/)
  [ "$c" = "200" ] && break
done
echo "HTTP $c"
```

### Step 3 before step 4 is not a style preference

Copying the WAR before removing the old exploded directory makes Tomcat
redeploy the stale exploded tree over your new WAR, or — depending on timing —
removes the file you just copied. The visible result is a clean 404 with no
error in any log. It has happened. Do the `rm` first.

Skipping the `work/` directory leaves previously compiled JSPs in place, so a
JSP you fixed still serves its old version and you conclude the fix did not
work.

### Never `pkill -f catalina`

Use `shutdown.sh`. A hard kill leaves `work/` half-written and the next
deployment fails during JSP compilation, which presents as an application error
rather than a deployment error.

## Giving Tomcat the environment

Tomcat does not read `.env`. Set variables in the service, or export them before
`startup.sh`:

```bash
set -a; . ./.env; set +a
/opt/tomcat/bin/startup.sh
```

If you use a systemd unit or the Tomcat service wrapper, put the `Environment=`
lines there instead — exporting in your shell only helps if the same shell
starts Tomcat.

The 2FA encryption key is an environment variable or a system property; a
`-D` flag belongs in `CATALINA_OPTS`.

## Verifying a deployment

Do not trust a 200 on `/`. Check all of it:

```bash
# 1. it boots
curl -s -o /dev/null -w '/  -> %{http_code}\n' http://localhost:8080/computerstore/

# 2. it is the build you think it is
ls -l --time-style=+%H:%M /opt/tomcat/webapps/computerstore.war target/computerstore.war

# 3. no startup errors
grep -cE "SEVERE|JasperException|Exception" /opt/tomcat/logs/catalina.out

# 4. representative pages
for p in / /products /login /cart; do
  printf '%-12s %s\n' "$p" "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:8080/computerstore$p")"
done
```

`catalina.out` is append-only and includes every previous run. Comparing a count
across deploys is meaningless without truncating or noting the offset first —
`tail -n 200` after the restart, not `grep -c` on the whole file.

JSP compilation failures appear in `catalina.out` as a Jasper stack trace, not
as an HTTP 500. A page returning 500 with nothing useful in `error.log` is a
JSP compile error until proven otherwise.

## Doing the real thing once

After a deploy, exercise a flow that crosses the layer boundary you changed. A
JSP-only change is invisible to `curl` in ways that have caught people out — for
instance, HTML5 form validation is enforced by the browser and by nothing else.
`curl` posts the form regardless, so a checkout page whose other payment methods
are silently disabled by a `required` field in a hidden panel passes every
server-side test and fails completely for the customer.

For anything touching a form, load the page in a real browser. That reasoning is
written up in
[../development/view-linting.md](../development/view-linting.md#the-required-in-a-hidden-panel-trap).

## Related

- [local-development.md](local-development.md) — first-time setup.
- [configuration.md](configuration.md) — settings that survive a deploy.
