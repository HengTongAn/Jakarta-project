<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="History - Admin"/>
<c:set var="colspan" value="${viewArchive ? 10 : 9}"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4 history-page">
    <div class="history-hero d-flex flex-wrap justify-content-between align-items-center gap-3 mb-3">
        <div>
            <h4 class="fw-bold mb-0">History</h4>
            <span class="text-muted small">Append-only audit trail. Records are never edited or deleted; old records move to the archive by retention policy.</span>
        </div>
        <c:if test="${viewMode != 'alerts'}">
            <form method="get" action="${pageContext.request.contextPath}/admin/history" class="history-search d-flex gap-2">
                <c:if test="${viewArchive}"><input type="hidden" name="view" value="archive"/></c:if>
                <input type="search" class="form-control history-keyword" name="keyword" value="${fn:escapeXml(keyword)}"
                       placeholder="Search actor, IP address, resource id..." aria-label="Search history"/>
                <button class="btn btn-primary" type="submit">Search</button>
            </form>
        </c:if>
    </div>

    <ul class="nav nav-pills mb-3 gap-1">
        <li class="nav-item">
            <a class="nav-link${viewMode == 'active' || (empty viewMode && not viewArchive) ? ' active' : ''}" href="${pageContext.request.contextPath}/admin/history">Active history</a>
        </li>
        <li class="nav-item">
            <a class="nav-link${viewArchive ? ' active' : ''}" href="${pageContext.request.contextPath}/admin/history?view=archive">Archive</a>
        </li>
        <li class="nav-item">
            <a class="nav-link${viewMode == 'alerts' ? ' active' : ''}" href="${pageContext.request.contextPath}/admin/history?view=alerts">
                Alerts
                <c:if test="${not empty alertsCount}"><span class="badge text-bg-danger ms-1">${alertsCount}</span></c:if>
                <c:if test="${not empty alerts}"><span class="badge text-bg-danger ms-1">${fn:length(alerts)}</span></c:if>
            </a>
        </li>
    </ul>

    <c:choose>
        <c:when test="${viewMode == 'alerts'}">
            <c:choose>
                <c:when test="${empty alerts}">
                    <div class="card card-hover">
                        <div class="card-body text-center text-muted py-5">
                            <i class="bi bi-shield-check fs-2 d-block mb-2"></i>
                            No suspicious patterns detected in the recent audit trail.
                        </div>
                    </div>
                </c:when>
                <c:otherwise>
                    <c:forEach var="a" items="${alerts}">
                        <c:set var="relatedKeyword" value="${not empty a.ipAddress ? a.ipAddress : a.actor}"/>
                        <div class="card card-hover mb-2">
                            <div class="card-body">
                                <div class="d-flex flex-wrap justify-content-between align-items-start gap-2">
                                    <div>
                                        <div class="d-flex flex-wrap align-items-center gap-2 mb-1">
                                            <span class="badge ${a.severity == 'High' ? 'bg-danger' : 'bg-warning text-dark'}">${a.severity}</span>
                                            <span class="fw-semibold small text-uppercase text-muted">${a.type}</span>
                                        </div>
                                        <div class="fw-semibold"><c:out value="${a.summary}"/></div>
                                        <div class="small text-muted">
                                            Actor <c:out value="${empty a.actor ? '-' : a.actor}"/> &middot;
                                            IP <c:out value="${empty a.ipAddress ? '-' : a.ipAddress}"/> &middot;
                                            ${a.count} time<c:if test="${a.count != 1}">s</c:if> &middot;
                                            <fmt:formatDate value="${a.firstSeen}" pattern="dd MMM HH:mm"/> &rarr;
                                            <fmt:formatDate value="${a.lastSeen}" pattern="dd MMM HH:mm"/>
                                        </div>
                                    </div>
                                    <c:if test="${not empty relatedKeyword}">
                                        <a class="btn btn-sm btn-outline-primary"
                                           href="${pageContext.request.contextPath}/admin/history?keyword=${fn:escapeXml(relatedKeyword)}">
                                            View related entries
                                        </a>
                                    </c:if>
                                </div>
                            </div>
                        </div>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
        </c:when>
        <c:otherwise>
            <div class="row g-2 mb-3 history-stats">
                <c:if test="${not viewArchive}">
                    <div class="col-6 col-lg-3">
                        <div class="history-stat">
                            <span class="h4 mb-0">${statsToday}</span>
                            <span class="small text-muted">records today</span>
                        </div>
                    </div>
                    <div class="col-6 col-lg-3">
                        <div class="history-stat">
                            <span class="h4 mb-0">${statsWeek}</span>
                            <span class="small text-muted">last 7 days</span>
                        </div>
                    </div>
                    <div class="col-6 col-lg-3">
                        <div class="history-stat">
                            <span class="h4 mb-0">${statsFailed24h}</span>
                            <span class="small text-muted">failed logins / 24h</span>
                        </div>
                    </div>
                    <div class="col-6 col-lg-3">
                        <div class="history-stat">
                            <span class="h4 mb-0">${statsFailed7d}</span>
                            <span class="small text-muted">failed logins / 7d</span>
                        </div>
                    </div>
                </c:if>
                <c:if test="${not viewArchive and not empty statsByType}">
                    <div class="col-12">
                        <div class="history-stat d-flex flex-wrap align-items-center gap-2">
                            <span class="small text-muted">By type:</span>
                            <c:forEach var="t" items="${statsByType}">
                                <span class="badge text-bg-light border">${t.key}: ${t.value}</span>
                            </c:forEach>
                        </div>
                    </div>
                </c:if>
            </div>

            <div class="d-flex flex-wrap justify-content-between align-items-center mb-2">
                <p class="small text-muted mb-0">
                    ${total} record<c:if test="${total != 1}">s</c:if> found<c:if test="${not empty keyword}"> for "<c:out value="${keyword}"/>"</c:if>
                </p>
            </div>

            <div class="card card-hover history-table-card">
                <div class="table-responsive">
                    <table class="table align-middle mb-0">
                        <thead class="table-light">
                        <tr>
                            <th class="ps-3" style="width:34px"></th>
                            <th>#</th>
                            <th>When</th>
                            <c:if test="${viewArchive}"><th>Archived</th></c:if>
                            <th>Type</th>
                            <th>Action</th>
                            <th>Actor</th>
                            <th>Target</th>
                            <th>Details</th>
                            <th>IP</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="e" items="${entries}">
                            <tr class="history-row">
                                <td class="ps-3">
                                    <button type="button" class="btn btn-sm btn-link history-expander p-0 border-0"
                                            data-bs-toggle="collapse" data-bs-target="#hist-${e.auditId}" aria-expanded="false"
                                            aria-label="Show details for record ${e.auditId}">
                                        <i class="bi bi-chevron-right"></i>
                                    </button>
                                </td>
                                <td class="text-muted small">${e.auditId}</td>
                                <td class="small text-nowrap">
                                    <fmt:formatDate value="${e.createdAt}" pattern="dd MMM HH:mm:ss"/>
                                    <c:if test="${not empty e.requestId}">
                                        <div class="text-muted font-monospace" style="font-size:.7rem;max-width:130px;overflow:hidden;text-overflow:ellipsis" title="${e.requestId}">${e.requestId}</div>
                                    </c:if>
                                </td>
                                <c:if test="${viewArchive}"><td class="small text-nowrap text-muted"><fmt:formatDate value="${e.archivedAt}" pattern="dd MMM yyyy"/></td></c:if>
                                <td>
                                    <span class="badge ${e.actionType.name() == 'AUTH' ? 'bg-info text-dark'
                                        : e.actionType.name() == 'ADMIN' ? 'bg-danger'
                                        : e.actionType.name() == 'DATA' ? 'bg-primary'
                                        : e.actionType.name() == 'SECURITY' ? 'bg-warning text-dark'
                                        : 'bg-secondary'}">${e.actionType.name()}</span>
                                </td>
                                <td class="fw-semibold small"><c:out value="${e.actionName}"/></td>
                                <td class="small"><c:out value="${empty e.actor ? '-' : e.actor}"/></td>
                                <td class="small">
                                    <c:choose>
                                        <c:when test="${not empty e.drillUrl}">
                                            <a href="${e.drillUrl}"><c:out value="${empty e.targetLabel ? '-' : e.targetLabel}"/></a>
                                        </c:when>
                                        <c:otherwise><c:out value="${empty e.targetLabel ? '-' : e.targetLabel}"/></c:otherwise>
                                    </c:choose>
                                </td>
                                <td class="small text-muted"
                                    title="<c:out value="${e.details}"/>"><c:out value="${fn:length(e.details) > 60 ? fn:substring(e.details, 0, 60).concat('...') : e.details}"/></td>
                                <td class="small text-nowrap"><c:out value="${empty e.ipAddress ? '-' : e.ipAddress}"/></td>
                            </tr>
                            <tr class="history-detail-row">
                                <td colspan="${colspan}">
                                    <div class="collapse" id="hist-${e.auditId}">
                                        <div class="history-detail p-3">
                                            <div class="history-detail-grid">
                                                <div><span class="fw-semibold d-block small">Request ID</span><span class="small font-monospace"><c:out value="${empty e.requestId ? '-' : e.requestId}"/></span></div>
                                                <div><span class="fw-semibold d-block small">Session ID</span><span class="small font-monospace"><c:out value="${empty e.sessionId ? '-' : e.sessionId}"/></span></div>
                                                <div><span class="fw-semibold d-block small">Created at</span><span class="small"><fmt:formatDate value="${e.createdAt}" pattern="dd MMM yyyy HH:mm:ss"/></span></div>
                                                <c:if test="${viewArchive}">
                                                    <div><span class="fw-semibold d-block small">Archived at</span><span class="small"><fmt:formatDate value="${e.archivedAt}" pattern="dd MMM yyyy HH:mm:ss"/></span></div>
                                                </c:if>
                                                <div><span class="fw-semibold d-block small">Actor</span><span class="small"><c:out value="${empty e.actor ? '-' : e.actor}"/></span></div>
                                                <div><span class="fw-semibold d-block small">IP address</span><span class="small font-monospace"><c:out value="${empty e.ipAddress ? '-' : e.ipAddress}"/></span></div>
                                                <div><span class="fw-semibold d-block small">Target</span><span class="small"><c:choose><c:when test="${not empty e.drillUrl}"><a href="${e.drillUrl}"><c:out value="${empty e.targetLabel ? '-' : e.targetLabel}"/></a></c:when><c:otherwise><c:out value="${empty e.targetLabel ? '-' : e.targetLabel}"/></c:otherwise></c:choose></span></div>
                                                <div class="col-span-2"><span class="fw-semibold d-block small">Details</span><span class="small"><c:out value="${empty e.details ? '-' : e.details}"/></span></div>
                                            </div>
                                            <pre class="mt-3 mb-0"><c:out value="${empty e.rawJson ? '{}' : e.rawJson}"/></pre>
                                        </div>
                                    </div>
                                </td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty entries}">
                            <tr><td colspan="${colspan}" class="text-center text-muted py-4">No audit records match.</td></tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>
            </div>

            <c:if test="${totalPages > 1}">
                <nav class="mt-3">
                    <ul class="pagination pagination-sm justify-content-center">
                        <li class="page-item ${page <= 1 ? 'disabled' : ''}">
                            <c:url var="prevUrl" value="/admin/history">
                                <c:if test="${viewArchive}"><c:param name="view" value="archive"/></c:if>
                                <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}"/></c:if>
                                <c:param name="page" value="${page - 1}"/>
                            </c:url>
                            <a class="page-link" href="${prevUrl}">&laquo; Prev</a>
                        </li>
                        <li class="page-item disabled"><span class="page-link">Page ${page} / ${totalPages}</span></li>
                        <li class="page-item ${page >= totalPages ? 'disabled' : ''}">
                            <c:url var="nextUrl" value="/admin/history">
                                <c:if test="${viewArchive}"><c:param name="view" value="archive"/></c:if>
                                <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}"/></c:if>
                                <c:param name="page" value="${page + 1}"/>
                            </c:url>
                            <a class="page-link" href="${nextUrl}">Next &raquo;</a>
                        </li>
                    </ul>
                </nav>
            </c:if>
        </c:otherwise>
    </c:choose>
</div>
<%@ include file="../common/footer.jspf" %>