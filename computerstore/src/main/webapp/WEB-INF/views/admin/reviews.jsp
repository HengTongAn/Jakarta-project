<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Reviews - Admin"/>
<%@ include file="../layouts/header.jspf" %>
<%@ include file="../layouts/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h4 class="fw-bold mb-0">Reviews <span class="badge bg-secondary rounded-pill align-middle">${totalReviews}</span></h4>
        <div class="d-flex flex-wrap gap-2">
            <a class="btn btn-sm ${empty selectedStatus ? 'btn-primary' : 'btn-outline-secondary'}"
               href="${pageContext.request.contextPath}/admin/reviews">All</a>
            <a class="btn btn-sm ${selectedStatus.name() == 'PENDING' ? 'btn-warning' : 'btn-outline-secondary'}"
               href="${pageContext.request.contextPath}/admin/reviews?status=PENDING">Pending
                <span class="badge bg-dark bg-opacity-25 rounded-pill">${pendingCount}</span></a>
            <a class="btn btn-sm ${selectedStatus.name() == 'APPROVED' ? 'btn-success' : 'btn-outline-secondary'}"
               href="${pageContext.request.contextPath}/admin/reviews?status=APPROVED">Approved
                <span class="badge bg-dark bg-opacity-25 rounded-pill">${approvedCount}</span></a>
            <a class="btn btn-sm ${selectedStatus.name() == 'REJECTED' ? 'btn-danger' : 'btn-outline-secondary'}"
               href="${pageContext.request.contextPath}/admin/reviews?status=REJECTED">Rejected
                <span class="badge bg-dark bg-opacity-25 rounded-pill">${rejectedCount}</span></a>
        </div>
    </div>

    <c:if test="${pendingCount > 0 && empty selectedStatus}">
        <div class="alert alert-warning py-2 small">
            <i class="bi bi-star-half me-1" aria-hidden="true"></i>
            <strong>${pendingCount}</strong> review(s) waiting for approval.
            <a href="${pageContext.request.contextPath}/admin/reviews?status=PENDING" class="alert-link">Review them now</a>.
        </div>
    </c:if>

    <div class="card card-hover">
        <div class="table-responsive">
            <table class="table align-middle mb-0">
                <thead class="table-light">
                <tr>
                    <th>#</th><th>Product</th><th>Reviewer</th><th class="text-center">Rating</th>
                    <th>Review</th><th class="text-center">Verified</th><th class="text-center">Status</th>
                    <th class="text-end" data-nosort>Actions</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="rv" items="${reviews}">
                    <tr>
                        <td>${rv.reviewId}</td>
                        <td>
                            <div class="fw-semibold">
                                <a class="text-decoration-none" href="${pageContext.request.contextPath}/products?id=${rv.productId}" target="_blank" rel="noopener">
                                    <c:out value="${rv.productName}"/>
                                </a>
                            </div>
                            <div class="small text-muted"><c:out value="${rv.productSku}"/></div>
                        </td>
                        <td>
                            <div class="fw-semibold"><c:out value="${rv.userName}"/></div>
                            <div class="small text-muted"><c:out value="${rv.userUsername}"/></div>
                        </td>
                        <td class="text-center text-warning">
                            <c:forEach begin="1" end="${rv.rating}">★</c:forEach><c:forEach begin="${rv.rating + 1}" end="5">☆</c:forEach>
                        </td>
                        <td style="max-width:360px">
                            <c:if test="${not empty rv.title}">
                                <div class="fw-semibold"><c:out value="${rv.title}"/></div>
                            </c:if>
                            <div class="small text-muted review-trunc" title="<c:out value='${rv.reviewText}'/>"><c:out value="${rv.reviewText}"/></div>
                            <div class="text-muted small mt-1"><fmt:formatDate value="${rv.createdAt}" pattern="dd MMM yyyy HH:mm"/></div>
                        </td>
                        <td class="text-center">
                            <c:choose>
                                <c:when test="${rv.verified}">
                                    <span class="badge bg-success-subtle text-success border"><i class="bi bi-patch-check-fill me-1"></i>Verified</span>
                                </c:when>
                                <c:otherwise><span class="text-muted small">—</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td class="text-center">
                            <c:choose>
                                <c:when test="${rv.status == 'APPROVED'}"><span class="badge bg-success">Approved</span></c:when>
                                <c:when test="${rv.status == 'REJECTED'}"><span class="badge bg-danger">Rejected</span></c:when>
                                <c:otherwise><span class="badge bg-warning text-dark">Pending</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td class="text-end">
                            <div class="d-inline-flex gap-1">
                                <c:if test="${rv.status != 'APPROVED'}">
                                    <form method="post" action="${pageContext.request.contextPath}/admin/reviews">
                                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                                        <input type="hidden" name="action" value="approve">
                                        <input type="hidden" name="reviewId" value="${rv.reviewId}">
                                        <input type="hidden" name="status" value="${empty selectedStatus ? '' : selectedStatus.name()}">
                                        <button type="submit" class="btn btn-success btn-sm">Approve</button>
                                    </form>
                                </c:if>
                                <c:if test="${rv.status != 'REJECTED'}">
                                    <form method="post" action="${pageContext.request.contextPath}/admin/reviews">
                                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                                        <input type="hidden" name="action" value="reject">
                                        <input type="hidden" name="reviewId" value="${rv.reviewId}">
                                        <input type="hidden" name="status" value="${empty selectedStatus ? '' : selectedStatus.name()}">
                                        <button type="submit" class="btn btn-outline-secondary btn-sm">Reject</button>
                                    </form>
                                </c:if>
                                <form method="post" action="${pageContext.request.contextPath}/admin/reviews"
                                      onsubmit="return confirm('Delete review #${rv.reviewId} permanently?');">
                                    <input type="hidden" name="csrfToken" value="${csrfToken}">
                                    <input type="hidden" name="action" value="delete">
                                    <input type="hidden" name="reviewId" value="${rv.reviewId}">
                                    <input type="hidden" name="status" value="${empty selectedStatus ? '' : selectedStatus.name()}">
                                    <button type="submit" class="btn btn-outline-danger btn-sm">Delete</button>
                                </form>
                            </div>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty reviews}">
                    <tr><td colspan="8" class="text-center text-muted py-4">No reviews${empty selectedStatus ? '' : ' with this status'} yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>

    <c:if test="${totalPages > 1}">
        <nav class="mt-3">
            <ul class="pagination pagination-sm justify-content-center">
                <li class="page-item ${page <= 1 ? 'disabled' : ''}">
                    <c:url var="prevUrl" value="/admin/reviews">
                        <c:if test="${not empty selectedStatus}"><c:param name="status" value="${selectedStatus.name()}"/></c:if>
                        <c:param name="page" value="${page - 1}"/>
                    </c:url>
                    <a class="page-link" href="${prevUrl}">&laquo; Prev</a>
                </li>
                <li class="page-item disabled"><span class="page-link">Page ${page} / ${totalPages}</span></li>
                <li class="page-item ${page >= totalPages ? 'disabled' : ''}">
                    <c:url var="nextUrl" value="/admin/reviews">
                        <c:if test="${not empty selectedStatus}"><c:param name="status" value="${selectedStatus.name()}"/></c:if>
                        <c:param name="page" value="${page + 1}"/>
                    </c:url>
                    <a class="page-link" href="${nextUrl}">Next &raquo;</a>
                </li>
            </ul>
        </nav>
    </c:if>
</div>
<%@ include file="../layouts/footer.jspf" %>
