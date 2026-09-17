<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="History - Admin"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4 history-page">
    <div class="history-hero d-flex flex-wrap justify-content-between align-items-center gap-3 mb-3">
        <div>
            <h4 class="fw-bold mb-0">History</h4>
            <span class="text-muted small">Active audit records are searchable. Older records are archived by retention policy, never manually deleted.</span>
        </div>
    </div>

    <div class="card card-hover history-filters mb-3">
        <div class="card-body py-2">
            <form method="get" action="${pageContext.request.contextPath}/admin/history"
                  class="row g-2 align-items-end">
                <div class="col-6 col-md-auto">
                    <label class="form-label small text-muted mb-0">Type</label>
                    <select name="type" class="form-select form-select-sm">
                        <option value="">All types</option>
                        <c:forEach var="t" items="${['AUTH','ADMIN','DATA','SECURITY','SYSTEM']}">
                            <option value="${t}" ${filterType == t ? 'selected' : ''}>${t}</option>
                        </c:forEach>
                    </select>
                </div>
                <div class="col-6 col-md-auto">
                    <label class="form-label small text-muted mb-0">Actor</label>
                    <select name="actor" class="form-select form-select-sm">
                        <option value="">All actors</option>
                        <c:forEach var="a" items="${actors}">
                            <option value="${a}" ${filterActor == a ? 'selected' : ''}>${a}</option>
                        </c:forEach>
                    </select>
                </div>
                <div class="col-12 col-md-auto history-keyword">
                    <label class="form-label small text-muted mb-0">Keyword</label>
                    <input type="text" name="keyword" value="${filterKeyword}" class="form-control form-control-sm"
                           placeholder="search action / target / details"/>
                </div>
                <div class="col-6 col-md-auto">
                    <label class="form-label small text-muted mb-0">From</label>
                    <input type="date" name="from" value="${filterFrom}" class="form-control form-control-sm">
                </div>
                <div class="col-6 col-md-auto">
                    <label class="form-label small text-muted mb-0">To</label>
                    <input type="date" name="to" value="${filterTo}" class="form-control form-control-sm">
                </div>
                <div class="col-12 col-md-auto d-flex gap-2">
                    <button class="btn btn-sm btn-brand">Filter</button>
                    <a href="${pageContext.request.contextPath}/admin/history" class="btn btn-sm btn-outline-secondary">Clear</a>
                </div>
            </form>
        </div>
    </div>

    <div class="history-actions card card-hover mb-3"><div class="card-body d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div><strong class="d-block">Retention controls</strong><span class="small text-muted">Export filtered records or archive old records to keep active history fast.</span></div>
        <div class="d-flex flex-wrap gap-2">
            <form method="post" action="${pageContext.request.contextPath}/admin/history" class="d-inline">
                <input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="action" value="export">
                <input type="hidden" name="type" value="${filterType}"><input type="hidden" name="actor" value="${filterActor}"><input type="hidden" name="keyword" value="${filterKeyword}"><input type="hidden" name="from" value="${filterFrom}"><input type="hidden" name="to" value="${filterTo}">
                <button type="submit" class="btn btn-outline-primary btn-sm" data-loading="Preparing export…"><i class="bi bi-file-earmark-arrow-down me-1"></i>Export CSV <span class="d-none d-md-inline">(up to ${exportLimit})</span></button>
            </form>
            <form method="post" action="${pageContext.request.contextPath}/admin/history" class="d-inline-flex gap-2 align-items-center">
                <input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="action" value="archive">
                <label class="visually-hidden" for="retentionDays">Archive records older than</label>
                <select id="retentionDays" name="retentionDays" class="form-select form-select-sm"><option value="365">Archive after 1 year</option><option value="730">Archive after 2 years</option><option value="2555">Archive after 7 years</option></select>
                <button type="submit" class="btn btn-outline-secondary btn-sm" data-confirm data-loading="Archiving…"><i class="bi bi-box-arrow-in-down me-1"></i>Archive old</button>
            </form>
        </div>
    </div></div>

    <p class="small text-muted mb-2">
        ${total} record<c:if test="${total != 1}">s</c:if> found
        <c:if test="${not empty filterType or not empty filterActor or not empty filterKeyword or not empty filterFrom or not empty filterTo}">(filtered)</c:if>.
    </p>

    <div class="card card-hover history-table-card">
        <div class="table-responsive">
            <table class="table align-middle mb-0">
                <thead class="table-light">
                <tr>
                    <th>#</th>
                    <th>When</th>
                    <th>Type</th>
                    <th>Action</th>
                    <th>Actor</th>
                    <th>Target</th>
                    <th>Details</th>
                    <th>IP</th><th class="text-end">Manage</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="e" items="${entries}">
                    <tr>
                        <td class="text-muted small">${e.auditId}</td>
                        <td class="small text-nowrap"><fmt:formatDate value="${e.createdAt}" pattern="dd MMM HH:mm:ss"/></td>
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
                                <c:when test="${not empty e.resourceId and not empty e.resourceType}">
                                    <c:out value="${e.resourceType}"/> #<c:out value="${e.resourceId}"/>
                                </c:when>
                                <c:when test="${not empty e.resourceId}">
                                    <c:out value="${e.resourceId}"/>
                                </c:when>
                                <c:when test="${not empty e.resourceType}">
                                    <c:out value="${e.resourceType}"/>
                                </c:when>
                                <c:otherwise>-</c:otherwise>
                            </c:choose>
                        </td>
                        <td class="small text-muted"
                            title="<c:out value="${e.details}"/>"><c:out value="${fn:length(e.details) > 60 ? fn:substring(e.details, 0, 60).concat('...') : e.details}"/></td>
                        <td class="small text-nowrap"><c:out value="${empty e.ipAddress ? '-' : e.ipAddress}"/></td>
                        <td class="text-end">
                            <form method="post" action="${pageContext.request.contextPath}/admin/history" class="d-inline" onsubmit="return confirm('Permanently delete this history record? This cannot be undone.');">
                                <input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="action" value="delete"><input type="hidden" name="auditId" value="${e.auditId}"><input type="hidden" name="confirmDelete" value="DELETE">
                                <button class="btn btn-outline-danger btn-sm" type="submit" title="Delete history record"><i class="bi bi-trash3"></i><span class="d-none d-lg-inline ms-1">Delete</span></button>
                            </form>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty entries}">
                    <tr><td colspan="9" class="text-center text-muted py-4">No audit records match.</td></tr>
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
                        <c:if test="${not empty filterType}"><c:param name="type" value="${filterType}"/></c:if>
                        <c:if test="${not empty filterActor}"><c:param name="actor" value="${filterActor}"/></c:if>
                        <c:if test="${not empty filterKeyword}"><c:param name="keyword" value="${filterKeyword}"/></c:if>
                        <c:if test="${not empty filterFrom}"><c:param name="from" value="${filterFrom}"/></c:if>
                        <c:if test="${not empty filterTo}"><c:param name="to" value="${filterTo}"/></c:if>
                        <c:param name="page" value="${page - 1}"/>
                    </c:url>
                    <a class="page-link" href="${prevUrl}">&laquo; Prev</a>
                </li>
                <li class="page-item disabled"><span class="page-link">Page ${page} / ${totalPages}</span></li>
                <li class="page-item ${page >= totalPages ? 'disabled' : ''}">
                    <c:url var="nextUrl" value="/admin/history">
                        <c:if test="${not empty filterType}"><c:param name="type" value="${filterType}"/></c:if>
                        <c:if test="${not empty filterActor}"><c:param name="actor" value="${filterActor}"/></c:if>
                        <c:if test="${not empty filterKeyword}"><c:param name="keyword" value="${filterKeyword}"/></c:if>
                        <c:if test="${not empty filterFrom}"><c:param name="from" value="${filterFrom}"/></c:if>
                        <c:if test="${not empty filterTo}"><c:param name="to" value="${filterTo}"/></c:if>
                        <c:param name="page" value="${page + 1}"/>
                    </c:url>
                    <a class="page-link" href="${nextUrl}">Next &raquo;</a>
                </li>
            </ul>
        </nav>
    </c:if>
</div>
<%@ include file="../common/footer.jspf" %>
