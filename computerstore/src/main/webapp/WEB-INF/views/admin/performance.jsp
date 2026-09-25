<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Performance - Admin"/>
<%@ include file="../layouts/header.jspf" %>
<%@ include file="../layouts/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex flex-wrap justify-content-between align-items-end gap-2 mb-4">
        <div>
            <h4 class="fw-bold mb-1">Performance Monitor</h4>
            <p class="text-muted mb-0">Live cache, connection pool and JVM statistics. Auto-refreshes every 30s.</p>
        </div>
    </div>

    <div class="row g-3 mb-3">
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-primary bg-opacity-10 text-primary"><i class="bi bi-database" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold">${poolStats.active}<span class="fs-6 text-muted">/ ${poolStats.max}</span></div><div class="small text-muted">Pool active connections</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-success bg-opacity-10 text-success"><i class="bi bi-hourglass-split" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold">${poolStats.waiting}</div><div class="small text-muted">Threads waiting (pool)</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-info bg-opacity-10 text-info"><i class="bi bi-memory" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold">${jvm.heapUsedMb} MB</div><div class="small text-muted">Heap used (max ${jvm.heapMaxMb} MB)</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-warning bg-opacity-10 text-warning"><i class="bi bi-activity" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold"><fmt:formatNumber value="${jvm.uptimeSeconds / 3600}" pattern="#.#"/>h</div><div class="small text-muted">Uptime</div></div>
                </div>
            </div>
        </div>
    </div>

    <div class="row g-3 mb-3">
        <div class="col-lg-7">
            <div class="card">
<div class="card-header bg-body fw-semibold"><i class="bi bi-box-seam me-1" aria-hidden="true"></i> Cache statistics <span class="badge ${cacheEnabled ? 'bg-success' : 'bg-secondary'} align-middle">${cacheEnabled ? 'enabled' : 'disabled'}</span></div>                <div class="card-body table-responsive p-0">
                    <table class="table table-sm align-middle mb-0">
                        <thead class="table-light">
                            <tr><th>Cache</th><th class="text-end">Entries</th><th class="text-end">Hits</th><th class="text-end">Misses</th><th class="text-end">Hit rate</th></tr>
                        </thead>
                        <tbody>
                            <c:forEach items="${cacheStats}" var="entry">
                                <tr>
                                    <td><c:out value="${entry.key}"/></td>
                                    <td class="text-end"><fmt:formatNumber value="${entry.value.size}"/></td>
                                    <td class="text-end"><fmt:formatNumber value="${entry.value.hits}"/></td>
                                    <td class="text-end"><fmt:formatNumber value="${entry.value.misses}"/></td>
                                    <td class="text-end"><fmt:formatNumber value="${entry.value.hitRate}" pattern="#0.0"/>%</td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </div>
<div class="card-footer bg-body text-muted small">Caches are in-JVM only; a multi-instance deployment should disable them or accept per-instance staleness.</div>            </div>
        </div>
        <div class="col-lg-5">
            <div class="card mb-3">
<div class="card-header bg-body fw-semibold"><i class="bi bi-database-gear me-1" aria-hidden="true"></i> Connection pool (HikariCP)</div>                <div class="card-body">
                    <c:choose>
                        <c:when test="${empty poolStats.total}">
                            <p class="text-muted mb-0">Pool is not initialised yet (no database traffic).</p>
                        </c:when>
                        <c:otherwise>
                            <div class="row text-center g-2">
                                <div class="col-4"><div class="small text-muted">Total</div><div class="fs-5 fw-bold">${poolStats.total}</div></div>
                                <div class="col-4"><div class="small text-muted">Active</div><div class="fs-5 fw-bold">${poolStats.active}</div></div>
                                <div class="col-4"><div class="small text-muted">Idle</div><div class="fs-5 fw-bold">${poolStats.idle}</div></div>
                                <div class="col-4"><div class="small text-muted">Waiting</div><div class="fs-5 fw-bold">${poolStats.waiting}</div></div>
                                <div class="col-4"><div class="small text-muted">Max</div><div class="fs-5 fw-bold">${poolStats.max}</div></div>
                                <div class="col-4"><div class="small text-muted">Min idle</div><div class="fs-5 fw-bold">${poolStats.min}</div></div>
                            </div>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
            <div class="card mb-3">
<div class="card-header bg-body fw-semibold"><i class="bi bi-cpu me-1" aria-hidden="true"></i> JVM</div>                <div class="card-body small">
                    <div class="d-flex justify-content-between"><span class="text-muted">Heap used / committed</span><span><fmt:formatNumber value="${jvm.heapUsedMb}" pattern="#,##0"/> / <fmt:formatNumber value="${jvm.heapCommittedMb}" pattern="#,##0"/> MB</span></div>
                    <div class="d-flex justify-content-between mt-1"><span class="text-muted">Heap max</span><span><fmt:formatNumber value="${jvm.heapMaxMb}" pattern="#,##0"/> MB</span></div>
                    <div class="d-flex justify-content-between mt-1"><span class="text-muted">Free memory</span><span><fmt:formatNumber value="${jvm.freeMb}" pattern="#,##0"/> MB</span></div>
                    <div class="d-flex justify-content-between mt-1"><span class="text-muted">Processors</span><span>${jvm.processors}</span></div>
                    <div class="d-flex justify-content-between mt-1"><span class="text-muted">Compression</span><span>${compressionEnabled ? 'enabled' : 'disabled'}</span></div>
                </div>
            </div>
        </div>
    </div>

    <div class="card mb-3">
        <div class="card-header bg-body fw-semibold"><i class="bi bi-activity me-1" aria-hidden="true"></i> Database queries <span class="badge bg-secondary align-middle">storefront hot path</span></div>
        <div class="card-body table-responsive p-0">
            <table class="table table-sm align-middle mb-0">
                <thead class="table-light">
                    <tr><th class="text-end">Count</th><th class="text-end">Avg (ms)</th><th class="text-end">Max (ms)</th><th class="text-end">Slow (&gt;1s)</th><th>Query</th></tr>
                </thead>
                <tbody>
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

    <div class="card">
        <div class="card-header bg-body fw-semibold"><i class="bi bi-lightbulb me-1" aria-hidden="true"></i> Recommendations</div>        <ul class="list-group list-group-flush">
            <c:forEach items="${recommendations}" var="tip">
                <li class="list-group-item"><i class="bi bi-arrow-right-circle me-2 text-primary" aria-hidden="true"></i><c:out value="${tip}"/></li>
            </c:forEach>
        </ul>
    </div>
</div>

<script>
(() => { window.setTimeout(() => window.location.reload(), 30000); })();
</script>
<%@ include file="../layouts/footer.jspf" %>
