<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Performance - Admin"/>
<%@ include file="../layouts/header.jspf" %>
<%@ include file="../layouts/admin-nav.jspf" %>

<div class="container py-4" data-perf>
    <div class="d-flex flex-wrap justify-content-between align-items-end gap-2 mb-4"
         data-help="Shows how fast the app is right now: caches, database connections, memory, and slow queries."
         data-help-title="Why this page exists"
         data-help-why="Fast pages keep customers happy. Like a car dashboard, you can't fix what you can't see - this page catches problems early.">
        <div>
            <h4 class="fw-bold mb-1 d-flex align-items-center gap-2">Performance Monitor
                <button type="button" class="admin-help-toggle" data-help-toggle aria-label="Why does this page exist?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
            </h4>
            <p class="text-muted mb-0">Updates in place every 15s &middot; last updated <span data-live-updated>&mdash;</span></p>
        </div>
    </div>

    <%-- A plain-language orientation, visible without clicking anything. Every
         other explanation on this page is behind one of ten "?" buttons, and
         admin-help.js is deliberately opt-in, so an admin who does not go
         looking for help sees only numbers. --%>
    <p class="text-muted small mb-3">
        Everything below refreshes by itself. <strong>Start with
        &ldquo;What this means&rdquo; below</strong> &mdash; it says in plain words whether
        anything needs your attention. If it is empty, the numbers underneath are all
        normal. The two things to watch are <strong>Threads waiting</strong> (requests
        queuing for a database connection &mdash; should be 0) and <strong>Hit rate</strong>
        (how often the app answers from memory instead of asking the database &mdash; a
        dash means that cache has not been used yet, not that it is perfect).
    </p>

    <%-- Read this first. It is the only card that says whether anything needs
         your attention, and it used to be the LAST thing on the page -- below
         four tables of jargon, which is the wrong order for the one thing an
         admin actually needs. This list is patched in place by
         performance-live.js, so its identifying attribute must stay on the
         <ul> below -- and must appear only there, which is why the attribute
         name is deliberately not spelled out in this comment. --%>
    <div class="card mb-3"
         data-help="Plain-language tips, generated automatically from the numbers below."
         data-help-title="What this means"
         data-help-why="Read this first. Each line is something the app noticed about itself, in plain words, with what to do about it. If nothing is listed, the numbers underneath are all in their normal range.">
        <div class="card-header bg-body fw-semibold d-flex justify-content-between align-items-center"><span><i class="bi bi-lightbulb me-1" aria-hidden="true"></i> What this means</span><button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button></div>
        <ul class="list-group list-group-flush" data-live-recommendations>
            <c:forEach items="${recommendations}" var="tip">
                <li class="list-group-item"><i class="bi bi-arrow-right-circle me-2 text-primary" aria-hidden="true"></i><c:out value="${tip}"/></li>
            </c:forEach>
        </ul>
    </div>

    <%-- ==================================================================
         EXPERIENCED PAGE TIME (real-user monitoring)
         Placed above the infrastructure numbers on purpose. Everything below
         this point describes the server; this section describes what a customer
         felt, which is the thing worth acting on. The other cards are here to
         explain *why* a number in this section is what it is.
         ================================================================== --%>
    <h5 class="fw-bold mb-2 mt-4 d-flex align-items-center gap-2">
        <i class="bi bi-speedometer2" aria-hidden="true"></i> Experienced Page Time
        <button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is Experienced Page Time?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
    </h5>
    <p class="text-muted small mb-3">
        The only figures on this page measured in <strong>real customer browsers</strong>
        rather than read off the server. Every other card describes the app;
        these describe the wait a person actually had. Kept above the rest for
        that reason.
    </p>

    <c:choose>
        <c:when test="${experienceFailed}">
            <%-- A failed query must not look like "no data yet". Those are
                 different situations and only one of them is healthy, so this
                 says so explicitly rather than rendering an empty table. --%>
            <div class="card mb-3 border-danger">
                <div class="card-body d-flex align-items-start gap-3">
                    <div class="icon bg-danger bg-opacity-10 text-danger"><i class="bi bi-exclamation-triangle" aria-hidden="true"></i></div>
                    <div>
                        <h6 class="mb-1">Could not read page timing data</h6>
                        <p class="text-muted small mb-0">
                            The query failed &mdash; this is not an empty result. The usual
                            cause is the <code>page_experience_samples</code> table not
                            existing yet, if the migration has not been run on this
                            database. Everything else on this page still works.
                        </p>
                    </div>
                </div>
            </div>
        </c:when>
        <c:otherwise>
            <c:set var="ept" value="${experience}"/>
            <div class="card mb-3"
                 data-help="How long until a page could be clicked, measured in real customer browsers over the last few days. p50 is the typical customer; p95 is the slow tail that generates complaints."
                 data-help-title="Experienced Page Time (EPT)"
                 data-help-why="EPT is the wait a person actually had, which is not the same as how long the server took. If Server is small but Interactive is large, the server is not your problem - it is the connection or the page weight. p95 matters more than the average: most complaints come from the slowest 5%.">
                <div class="card-header bg-body fw-semibold d-flex justify-content-between align-items-center">
                    <span><i class="bi bi-stopwatch me-1" aria-hidden="true"></i> EPT overall
                        <span class="badge bg-secondary align-middle">measured in browsers</span></span>
                    <button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                </div>
                <div class="card-body">
                    <%-- Both basis paragraphs and the table are rendered in BOTH the
                         empty and populated states, with the same element
                         identities, and one of the paragraphs is hidden rather than
                         omitted. performance-live.js can only patch elements that
                         already exist, so a c:choose that emitted one set in the
                         empty case and another in the populated case would leave the
                         15s refresh unable to move this card from empty to
                         populated: the report would sit on "nothing sampled yet"
                         until a manual reload, which is exactly the transition an
                         operator would most want to watch happen. --%>
                    <p class="text-muted small mb-0 ${ept.rowCount == 0 ? '' : 'd-none'}"
                       data-live-ept-empty>
                        Nothing sampled yet. Measurements are collected from customer
                        browsers, so this fills in as people use the shop &mdash; it is not
                        a sign the shop is fast.
                    </p>
                    <p class="text-muted small ${ept.rowCount == 0 ? 'd-none' : ''}"
                       data-live-ept-basis>
                        Based on <strong data-live-ept-count><fmt:formatNumber value="${ept.rowCount}"/></strong>
                        sampled page loads over the last
                        <strong><span data-live-ept-days><c:out value="${retentionDays}"/></span> days</strong>,
                        keeping roughly 1 in <span data-live-ept-rate><c:out value="${sampleRate}"/></span>.
                        Counts describe the <em>sample</em>, not total traffic;
                        the timings are representative but the totals are not.
                    </p>
                    <div class="table-responsive">
                        <table class="table table-sm align-middle mb-0">
                            <thead class="table-light">
                                <tr>
                                    <th>Measure</th>
                                    <th class="text-end">p50</th>
                                    <th class="text-end">p75</th>
                                    <th class="text-end">p95</th>
                                    <th class="text-end">Avg</th>
                                    <th class="text-end">Worst</th>
                                </tr>
                            </thead>
                            <tbody data-live-ept-overall>
                                <c:if test="${ept.rowCount == 0}">
                                    <%-- Explains the absence rather than showing
                                         zeroes, which would read as an
                                         implausibly fast site. --%>
                                    <tr>
                                        <td colspan="6" class="text-muted small">Nothing sampled yet. Measurements are collected from customer browsers.</td>
                                    </tr>
                                </c:if>
                                <c:if test="${ept.rowCount != 0}">
                                <tr>
                                    <td><strong>Interactive</strong> <span class="text-muted small">&mdash; clickable</span></td>
                                    <td class="text-end fw-bold"><fmt:formatNumber value="${ept.interactive.p50}"/> ms</td>
                                    <td class="text-end"><fmt:formatNumber value="${ept.interactive.p75}"/> ms</td>
                                    <td class="text-end"><fmt:formatNumber value="${ept.interactive.p95}"/> ms</td>
                                    <td class="text-end text-muted"><fmt:formatNumber value="${ept.interactive.average}" pattern="#0.0"/> ms</td>
                                    <td class="text-end text-muted"><fmt:formatNumber value="${ept.interactive.max}"/> ms</td>
                                </tr>
                                <tr>
                                    <td>DOM ready <span class="text-muted small">&mdash; content ready</span></td>
                                    <td class="text-end"><fmt:formatNumber value="${ept.domReady.p50}"/> ms</td>
                                    <td class="text-end"><fmt:formatNumber value="${ept.domReady.p75}"/> ms</td>
                                    <td class="text-end"><fmt:formatNumber value="${ept.domReady.p95}"/> ms</td>
                                    <td class="text-end text-muted"><fmt:formatNumber value="${ept.domReady.average}" pattern="#0.0"/> ms</td>
                                    <td class="text-end text-muted"><fmt:formatNumber value="${ept.domReady.max}"/> ms</td>
                                </tr>
                                <tr>
                                    <td>Fully loaded <span class="text-muted small">&mdash; images, fonts</span></td>
                                    <td class="text-end"><fmt:formatNumber value="${ept.load.p50}"/> ms</td>
                                    <td class="text-end"><fmt:formatNumber value="${ept.load.p75}"/> ms</td>
                                    <td class="text-end"><fmt:formatNumber value="${ept.load.p95}"/> ms</td>
                                    <td class="text-end text-muted"><fmt:formatNumber value="${ept.load.average}" pattern="#0.0"/> ms</td>
                                    <td class="text-end text-muted"><fmt:formatNumber value="${ept.load.max}"/> ms</td>
                                </tr>
                                <tr>
                                    <td>First byte <span class="text-muted small">&mdash; includes connection setup</span></td>
                                    <td class="text-end"><fmt:formatNumber value="${ept.ttfb.p50}"/> ms</td>
                                    <td class="text-end"><fmt:formatNumber value="${ept.ttfb.p75}"/> ms</td>
                                    <td class="text-end"><fmt:formatNumber value="${ept.ttfb.p95}"/> ms</td>
                                    <td class="text-end text-muted"><fmt:formatNumber value="${ept.ttfb.average}" pattern="#0.0"/> ms</td>
                                    <td class="text-end text-muted"><fmt:formatNumber value="${ept.ttfb.max}"/> ms</td>
                                </tr>
                                <tr>
                                    <td><strong>Server think time</strong> <span class="text-muted small">&mdash; your app's share</span></td>
                                    <td class="text-end fw-bold"><fmt:formatNumber value="${ept.server.p50}"/> ms</td>
                                    <td class="text-end"><fmt:formatNumber value="${ept.server.p75}"/> ms</td>
                                    <td class="text-end"><fmt:formatNumber value="${ept.server.p95}"/> ms</td>
                                    <td class="text-end text-muted"><fmt:formatNumber value="${ept.server.average}" pattern="#0.0"/> ms</td>
                                    <td class="text-end text-muted"><fmt:formatNumber value="${ept.server.max}"/> ms</td>
                                </tr>
                                <tr>
                                    <td>Page weight <span class="text-muted small">&mdash; transferred</span></td>
                                    <%-- Both figures are divided here because the
                                         repository stores and returns raw bytes.
                                         Formatting a byte count and labelling it
                                         KB would report a page as ~1000x its
                                         real weight. --%>
                                    <td class="text-end">
                                        <fmt:formatNumber value="${ept.transfer.average / 1024}" pattern="#0.0"/> KB average,
                                        <fmt:formatNumber value="${ept.transfer.max / 1024}" pattern="#0.0"/> KB worst
                                    </td>
                                    <td colspan="5"></td>
                                </tr>
                                    </c:if>
                                </tbody>
                            </table>
                    </div>
                    <p class="text-muted small mt-3 mb-0">
                        <strong>How to read this:</strong> compare
                        <em>Server think time</em> with <em>Interactive</em>. A small
                        server time against a large interactive time means the app
                        answered promptly and the rest was the customer's connection
                        or the page's own weight &mdash; look at page weight and the
                        slowest tables below. A large server time means the app
                        itself is the delay, and the query table further down is where
                        to look next.
                    </p>
                </div>
            </div>

            <%-- Performance by browser: is the slowness specific to one browser?
                 That is the question this table exists to answer, and it is the
                 one a server-side number can never answer. --%>
            <div class="card mb-3"
                 data-help="The same EPT figures grouped by browser. Use this to tell a general slowdown (every browser slow) from a specific one (only Safari, or only mobile) that points at a rendering or compatibility problem."
                 data-help-title="Performance by browser"
                 data-help-why="If one browser is far slower than the rest, the cause is probably specific to it - a rendering path, an unsupported feature, or an extension - rather than your server. If they are all similar, the problem is general.">
                <div class="card-header bg-body fw-semibold d-flex justify-content-between align-items-center">
                    <span><i class="bi bi-window me-1" aria-hidden="true"></i> Performance by browser</span>
                    <button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                </div>
                <div class="card-body table-responsive p-0">
                    <table class="table table-sm align-middle mb-0">
                        <thead class="table-light">
                            <tr>
                                <th>Browser</th>
                                <th class="text-end">Samples</th>
                                <th class="text-end">p50</th>
                                <th class="text-end">p95</th>
                                <th class="text-end">Server p95</th>
                                <th class="text-end">Avg weight</th>
                            </tr>
                        </thead>
                        <tbody data-live-ept-browser>
                            <c:choose>
                                <c:when test="${empty ept.byBrowser}">
                                    <tr><td colspan="6" class="text-muted small">No browser samples yet.</td></tr>
                                </c:when>
                                <c:otherwise>
                                    <c:forEach items="${ept.byBrowser}" var="b">
                                        <tr>
                                            <td><c:out value="${b.key}"/></td>
                                            <td class="text-end"><fmt:formatNumber value="${b.clientCount}"/></td>
                                            <td class="text-end"><fmt:formatNumber value="${b.client.p50}"/> ms</td>
                                            <td class="text-end ${b.client.p95 > 2000 ? 'text-danger fw-bold' : ''}"><fmt:formatNumber value="${b.client.p95}"/> ms</td>
                                            <td class="text-end text-muted"><fmt:formatNumber value="${b.server.p95}"/> ms</td>
                                            <td class="text-end text-muted"><fmt:formatNumber value="${b.averageBytes / 1024}" pattern="#0.0"/> KB</td>
                                        </tr>
                                    </c:forEach>
                                </c:otherwise>
                            </c:choose>
                        </tbody>
                    </table>
                </div>
                <div class="card-footer bg-body text-muted small">Sorted slowest first. Browser families are grouped coarsely and no identifying information about a visitor is stored.</div>
            </div>

            <%-- Performance by page type / object: which records are the slow
                 ones. The ":detail" suffix marks a single record rather than a
                 list, which is the distinction that matters -- 24 cards render
                 very differently from one product and its reviews. --%>
            <div class="card mb-3"
                 data-help="The same EPT figures grouped by kind of page. Use this to find exactly which pages are the slow ones - a specific record type being slow points at that page's own work, not at the site as a whole."
                 data-help-title="Performance by page type"
                 data-help-why="Grouped by kind of page, not by individual URL, so that 200 different product pages appear as one entry instead of 200 single visits. ':detail' means one record (a single product or order); without it, a list of many.">
                <div class="card-header bg-body fw-semibold d-flex justify-content-between align-items-center">
                    <span><i class="bi bi-files me-1" aria-hidden="true"></i> Performance by page type</span>
                    <button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                </div>
                <div class="card-body table-responsive p-0">
                    <table class="table table-sm align-middle mb-0">
                        <thead class="table-light">
                            <tr>
                                <th>Page</th>
                                <th class="text-end">Samples</th>
                                <th class="text-end">p50</th>
                                <th class="text-end">p95</th>
                                <th class="text-end">Server p95</th>
                                <th class="text-end">Avg weight</th>
                            </tr>
                        </thead>
                        <tbody data-live-ept-pages>
                            <c:choose>
                                <c:when test="${empty ept.byPageType}">
                                    <tr><td colspan="6" class="text-muted small">No page samples yet.</td></tr>
                                </c:when>
                                <c:otherwise>
                                    <c:forEach items="${ept.byPageType}" var="b">
                                        <tr>
                                            <td><code class="text-muted"><c:out value="${b.key}"/></code></td>
                                            <td class="text-end"><fmt:formatNumber value="${b.clientCount}"/></td>
                                            <td class="text-end"><fmt:formatNumber value="${b.client.p50}"/> ms</td>
                                            <td class="text-end ${b.client.p95 > 2000 ? 'text-danger fw-bold' : ''}"><fmt:formatNumber value="${b.client.p95}"/> ms</td>
                                            <td class="text-end text-muted"><fmt:formatNumber value="${b.server.p95}"/> ms</td>
                                            <td class="text-end text-muted"><fmt:formatNumber value="${b.averageBytes / 1024}" pattern="#0.0"/> KB</td>
                                        </tr>
                                    </c:forEach>
                                </c:otherwise>
                            </c:choose>
                        </tbody>
                    </table>
                </div>
                <div class="card-footer bg-body text-muted small">
                    Grouped by route, so a list page and a single record
                    (<code>:detail</code>) are counted separately. Sorted slowest first.
                </div>
            </div>
        </c:otherwise>
    </c:choose>

    <h5 class="fw-bold mb-2 mt-4">Server internals</h5>
    <p class="text-muted small mb-3">
        Everything from here down is read live from the running application. It
        explains <em>why</em> the figures above came out as they did, but unlike
        them it describes the server and never the customer &mdash; and it all
        resets when the app is redeployed.
    </p>

    <div class="row g-3 mb-3">
        <div class="col-6 col-md-3">
            <div class="card stats-card"
                 data-help="Database connections currently in use."
                 data-help-title="Pool active connections"
                 data-help-why="High and steady means heavy traffic. If it keeps hitting the max, pages can start to stall.">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-primary bg-opacity-10 text-primary"><i class="bi bi-database" aria-hidden="true"></i></div>
                    <div>
                        <div class="fs-4 fw-bold"><span data-live-perf="poolActive">${poolStats.active}</span><span class="fs-6 text-muted">/ <span data-live-perf="poolMax">${poolStats.max}</span></span></div>
                        <div class="small text-muted">Pool active connections</div>
                    </div>
                    <button type="button" class="admin-help-toggle ms-auto" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                </div>
            </div>
        </div>
        <div class="col-6 col-md-3">
            <div class="card stats-card"
                 data-help="Requests that had to wait for a free connection."
                 data-help-title="Threads waiting"
                 data-help-why="Zero is ideal. When it climbs, traffic is outrunning the pool - a sign to raise the limit.">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-success bg-opacity-10 text-success"><i class="bi bi-hourglass-split" aria-hidden="true"></i></div>
                    <div>
                        <div class="fs-4 fw-bold"><span data-live-perf="poolWaiting">${poolStats.waiting}</span></div>
                        <div class="small text-muted">Threads waiting (pool)</div>
                    </div>
                    <button type="button" class="admin-help-toggle ms-auto" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                </div>
            </div>
        </div>
        <div class="col-6 col-md-3">
            <div class="card stats-card"
                 data-help="App memory in use, compared with the max available."
                 data-help-title="Heap used"
                 data-help-why="Creeping up slowly is normal. Jumping close to the max means a restart or a tuning pass is due.">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-info bg-opacity-10 text-info"><i class="bi bi-memory" aria-hidden="true"></i></div>
                    <div>
                        <div class="fs-4 fw-bold"><span data-live-perf="heapUsed">${jvm.heapUsedMb}</span> MB</div>
                        <div class="small text-muted">Heap used (max <span data-live-perf="heapMax">${jvm.heapMaxMb}</span> MB)</div>
                    </div>
                    <button type="button" class="admin-help-toggle ms-auto" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                </div>
            </div>
        </div>
        <div class="col-6 col-md-3">
            <div class="card stats-card"
                 data-help="How long since the app last restarted."
                 data-help-title="Uptime"
                 data-help-why="Long uptime is usually healthy - and it gives context for the other numbers.">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-warning bg-opacity-10 text-warning"><i class="bi bi-activity" aria-hidden="true"></i></div>
                    <div>
                        <div class="fs-4 fw-bold"><span data-live-perf="uptimeH"><fmt:formatNumber value="${jvm.uptimeSeconds / 3600}" pattern="#.#"/></span>h</div>
                        <div class="small text-muted">Uptime</div>
                    </div>
                    <button type="button" class="admin-help-toggle ms-auto" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                </div>
            </div>
        </div>
    </div>

    <div class="row g-3 mb-3">
        <div class="col-lg-7">
            <div class="card"
                 data-help="How often the app answers from fast memory instead of asking the database."
                 data-help-title="Cache statistics"
                 data-help-why="A high hit rate means snappy pages and a relaxed database. A sudden dip right after a cache clear is normal - it climbs back.">
                <div class="card-header bg-body fw-semibold d-flex justify-content-between align-items-center"><span><i class="bi bi-box-seam me-1" aria-hidden="true"></i> Cache statistics <span class="badge ${cacheEnabled ? 'bg-success' : 'bg-secondary'} align-middle" data-live-cache-badge>${cacheEnabled ? 'enabled' : 'disabled'}</span></span><button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button></div>
                <div class="card-body table-responsive p-0">
                    <table class="table table-sm align-middle mb-0">
                        <thead class="table-light">
                            <tr><th>Cache</th><th class="text-end">Entries</th><th class="text-end">Hits</th><th class="text-end">Misses</th><th class="text-end">Hit rate</th></tr>
                        </thead>
                        <tbody data-live-cache-table>
                            <c:forEach items="${cacheStats}" var="entry">
                                <tr>
                                    <td><c:out value="${entry.key}"/></td>
                                    <td class="text-end"><fmt:formatNumber value="${entry.value.size}"/></td>
                                    <td class="text-end"><fmt:formatNumber value="${entry.value.hits}"/></td>
                                    <td class="text-end"><fmt:formatNumber value="${entry.value.misses}"/></td>
                                    <%-- No rate until there is a request to divide. An untouched
                                         cache has no meaningful hit rate, and printing 100%
                                         would claim it is working perfectly. --%>
                                    <td class="text-end">
                                        <c:choose>
                                            <c:when test="${empty entry.value.hitRate}">
                                                <span class="text-muted" title="No requests recorded yet">&mdash;</span>
                                            </c:when>
                                            <c:otherwise>
                                                <fmt:formatNumber value="${entry.value.hitRate}" pattern="#0.0"/>%
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </div>
                <div class="card-footer bg-body text-muted small">Caches are in-JVM only; a multi-instance deployment should disable them or accept per-instance staleness.</div>
            </div>
        </div>
        <div class="col-lg-5">
            <div class="card mb-3"
                 data-help="Ready-made database connections the app keeps open and reuses."
                 data-help-title="Connection pool"
                 data-help-why="Opening a connection is slow; the pool removes that wait. Watch 'waiting' for overload signs.">
                <div class="card-header bg-body fw-semibold d-flex justify-content-between align-items-center"><span><i class="bi bi-database-gear me-1" aria-hidden="true"></i> Connection pool <span class="text-muted small fw-normal">(HikariCP)</span></span><button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button></div>
                <div class="card-body">
                    <c:choose>
                        <c:when test="${empty poolStats.total}">
                            <p class="text-muted mb-0" data-live-pool-empty>Pool is not initialised yet (no database traffic).</p>
                        </c:when>
                        <c:otherwise>
                            <div class="row text-center g-2">
                                <div class="col-4"><div class="small text-muted">Total</div><div class="fs-5 fw-bold" data-live-pool="total">${poolStats.total}</div></div>
                                <div class="col-4"><div class="small text-muted">Active</div><div class="fs-5 fw-bold" data-live-pool="active">${poolStats.active}</div></div>
                                <div class="col-4"><div class="small text-muted">Idle</div><div class="fs-5 fw-bold" data-live-pool="idle">${poolStats.idle}</div></div>
                                <div class="col-4"><div class="small text-muted">Waiting</div><div class="fs-5 fw-bold" data-live-pool="waiting">${poolStats.waiting}</div></div>
                                <div class="col-4"><div class="small text-muted">Max</div><div class="fs-5 fw-bold" data-live-pool="max">${poolStats.max}</div></div>
                                <div class="col-4"><div class="small text-muted">Min idle</div><div class="fs-5 fw-bold" data-live-pool="min">${poolStats.min}</div></div>
                            </div>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
            <div class="card mb-3"
                 data-help="The app's memory, CPU and whether HTTP compression is on."
                 data-help-title="App memory &amp; CPU"
                 data-help-why="Steady memory is healthy. Compression shrinks pages so they load faster on slow connections.">
                <div class="card-header bg-body fw-semibold d-flex justify-content-between align-items-center"><span><i class="bi bi-cpu me-1" aria-hidden="true"></i> App memory &amp; CPU <span class="text-muted small fw-normal">(JVM)</span></span><button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button></div>
                <div class="card-body small">
                    <div class="d-flex justify-content-between"><span class="text-muted">Heap used / committed</span><span><span data-live-jvm="heapUsed"><fmt:formatNumber value="${jvm.heapUsedMb}" pattern="#,##0"/></span> / <span data-live-jvm="heapCommitted"><fmt:formatNumber value="${jvm.heapCommittedMb}" pattern="#,##0"/></span> MB</span></div>
                    <div class="d-flex justify-content-between mt-1"><span class="text-muted">Heap max</span><span><span data-live-jvm="heapMax"><fmt:formatNumber value="${jvm.heapMaxMb}" pattern="#,##0"/></span> MB</span></div>
                    <div class="d-flex justify-content-between mt-1"><span class="text-muted">Free memory</span><span><span data-live-jvm="free"><fmt:formatNumber value="${jvm.freeMb}" pattern="#,##0"/></span> MB</span></div>
                    <div class="d-flex justify-content-between mt-1"><span class="text-muted">Processors</span><span data-live-jvm="processors">${jvm.processors}</span></div>
                    <div class="d-flex justify-content-between mt-1"><span class="text-muted">Compression</span><span data-live-jvm="compression">${compressionEnabled ? 'enabled' : 'disabled'}</span></div>
                </div>
            </div>
        </div>
    </div>

    <div class="card mb-3"
         data-help="The slowest queries first, with how often each one runs."
         data-help-title="Database queries"
         data-help-why="Anything marked 'slow' is worth caching or optimizing - the same query asked again is a hit, not a new trip to the database.">
        <div class="card-header bg-body fw-semibold d-flex justify-content-between align-items-center"><span><i class="bi bi-activity me-1" aria-hidden="true"></i> Database queries <span class="badge bg-secondary align-middle">storefront hot path</span></span><button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button></div>
        <p class="text-muted small px-3 pt-2 mb-0">
            The database work behind one visit to the shop. <strong>Avg (ms)</strong> is
            the average time each query took; <strong>Slow (&gt;1s)</strong> counts how
            often one took over a second. Only the queries that took longest are listed,
            and the SQL is shown so you can hand it to whoever maintains the code &mdash;
            you do not need to read SQL to use this table.
        </p>
        <div class="card-body table-responsive p-0">
            <table class="table table-sm align-middle mb-0">
                <thead class="table-light">
                    <tr><th class="text-end">Count</th><th class="text-end">Avg (ms)</th><th class="text-end">Max (ms)</th><th class="text-end">Slow (&gt;1s)</th><th>Query</th></tr>
                </thead>
                <tbody data-live-queries>
                    <c:choose>
                        <c:when test="${empty queryStats}">
                            <tr><td colspan="5" class="text-muted small">No queries recorded yet. Load the storefront (/products) and this table will show its real query profile.</td></tr>
                        </c:when>
                        <c:otherwise>
                            <c:forEach items="${queryStats}" var="q">
                                <tr>
                                    <td class="text-end"><fmt:formatNumber value="${q.stats.count}"/></td>
                                    <td class="text-end"><fmt:formatNumber value="${q.stats.avgTime}" pattern="#0.0"/></td>
                                    <td class="text-end"><fmt:formatNumber value="${q.stats.maxTime}"/></td>
                                    <td class="text-end ${q.stats.slowCount > 0 ? 'text-danger fw-bold' : ''}">${q.stats.slowCount}</td>
                                    <td class="small text-break"><code class="text-muted"><c:out value="${q.signature}"/></code></td>
                                </tr>
                            </c:forEach>
                        </c:otherwise>
                    </c:choose>
                </tbody>
            </table>
        </div>
    </div>
</div>

<script src="${pageContext.request.contextPath}/assets/js/performance-live.js?v=${assetsVersion}"></script>
<%@ include file="../layouts/footer.jspf" %>