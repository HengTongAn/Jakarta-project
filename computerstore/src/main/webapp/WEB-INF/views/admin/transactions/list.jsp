<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Transactions - Admin"/>
<%@ include file="../../layouts/header.jspf" %>
<%@ include file="../../layouts/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h4 class="fw-bold mb-0">Transactions <span class="badge bg-secondary rounded-pill align-middle" id="transactionsCount">${totalTransactions}</span></h4>
        <div class="d-flex flex-wrap gap-2 align-items-center admin-filter-toolbar">
            <label for="transactionsFilter" class="visually-hidden">Filter transactions</label>
            <input type="search" id="transactionsFilter" class="form-control form-control-sm table-filter" placeholder="Filter transactions…">
            <form method="get" action="${pageContext.request.contextPath}/admin/transactions" class="d-flex gap-2">
                <select name="status" class="form-select form-select-sm" onchange="this.form.submit()">
                    <option value="">All statuses</option>
                    <option value="PENDING" ${not empty selectedStatus and selectedStatus.name() == 'PENDING' ? 'selected' : ''}>Pending</option>
                    <option value="PROCESSING" ${not empty selectedStatus and selectedStatus.name() == 'PROCESSING' ? 'selected' : ''}>Processing</option>
                    <option value="COMPLETED" ${not empty selectedStatus and selectedStatus.name() == 'COMPLETED' ? 'selected' : ''}>Completed</option>
                    <option value="FAILED" ${not empty selectedStatus and selectedStatus.name() == 'FAILED' ? 'selected' : ''}>Failed</option>
                    <option value="REFUNDED" ${not empty selectedStatus and selectedStatus.name() == 'REFUNDED' ? 'selected' : ''}>Refunded</option>
                    <option value="PARTIALLY_REFUNDED" ${not empty selectedStatus and selectedStatus.name() == 'PARTIALLY_REFUNDED' ? 'selected' : ''}>Partially Refunded</option>
                    <option value="CHARGEBACK" ${not empty selectedStatus and selectedStatus.name() == 'CHARGEBACK' ? 'selected' : ''}>Chargeback</option>
                </select>
                <select name="paymentMethod" class="form-select form-select-sm" onchange="this.form.submit()">
                    <option value="">All methods</option>
                    <option value="ABA" ${not empty selectedPaymentMethod and selectedPaymentMethod == 'ABA' ? 'selected' : ''}>ABA Payway</option>
                    <option value="CARD" ${not empty selectedPaymentMethod and selectedPaymentMethod == 'CARD' ? 'selected' : ''}>Card</option>
                </select>
            </form>
        </div>
    </div>

    <div class="row g-3 mb-3">
        <div class="col-md-3">
            <div class="card card-hover">
                <div class="card-body py-2">
                    <div class="small text-muted">Total Transactions</div>
                    <div class="fw-bold fs-5">${stats.totalTransactions}</div>
                </div>
            </div>
        </div>
        <div class="col-md-3">
            <div class="card card-hover">
                <div class="card-body py-2">
                    <div class="small text-muted">Completed Amount</div>
                    <div class="fw-bold fs-5">$<fmt:formatNumber value="${stats.totalCompletedAmount}" pattern="#,##0.00"/></div>
                </div>
            </div>
        </div>
        <div class="col-md-3">
            <div class="card card-hover">
                <div class="card-body py-2">
                    <div class="small text-muted">Failed</div>
                    <div class="fw-bold fs-5 text-danger">${stats.failedCount}</div>
                </div>
            </div>
        </div>
        <div class="col-md-3">
            <div class="card card-hover">
                <div class="card-body py-2">
                    <div class="small text-muted">Pending</div>
                    <div class="fw-bold fs-5 text-warning">${stats.pendingCount}</div>
                </div>
            </div>
        </div>
    </div>

    <div class="card card-hover">
        <div class="table-responsive">
            <table class="table align-middle mb-0" data-sortable data-filter-target="transactionsFilter" data-count="transactionsCount">
                <thead class="table-light">
                <tr>
                    <th data-sort="number">ID</th>
                    <th data-sort="number">Order #</th>
                    <th>Customer</th>
                    <th>Type</th>
                    <th class="text-end" data-sort="price">Amount</th>
                    <th>Method</th>
                    <th class="text-center">Status</th>
                    <th>Gateway ID</th>
                    <th>Date</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="tx" items="${transactions}">
                    <tr>
                        <td>${tx.transactionId}</td>
                        <td>#${tx.orderId}</td>
                        <td>
                            <div class="fw-semibold"><c:out value="${tx.customerName}"/></div>
                            <div class="small text-muted"><c:out value="${tx.customerUsername}"/></div>
                        </td>
                        <td>
                            <c:set var="_type" value="${tx.transactionType}"/>
                            <c:choose>
                            <c:when test="${_type == 'PAYMENT'}">
                                <span class="badge bg-primary">Payment</span>
                            </c:when>
                            <c:when test="${_type == 'REFUND'}">
                                <span class="badge bg-info">Refund</span>
                            </c:when>
                            <c:when test="${_type == 'PARTIAL_REFUND'}">
                                <span class="badge bg-info text-dark">Partial Refund</span>
                            </c:when>
                            <c:when test="${_type == 'CHARGEBACK'}">
                                <span class="badge bg-danger">Chargeback</span>
                            </c:when>
                            <c:otherwise>
                                <span class="badge bg-secondary"><c:out value="${_type}"/></span>
                            </c:otherwise>
                            </c:choose>
                        </td>
                        <td class="text-end money">$<fmt:formatNumber value="${tx.amount}" pattern="#,##0.00"/></td>
                        <td>
                            <c:set var="_method" value="${tx.paymentMethod}"/>
                            <c:choose>
                            <c:when test="${_method == 'ABA'}">
                                <span class="badge bg-success">ABA Payway</span>
                            </c:when>
                            <c:when test="${_method == 'CARD'}">
                                <span class="badge bg-warning text-dark">Card</span>
                            </c:when>
                            <c:otherwise>
                                <span class="badge bg-light text-muted"><c:out value="${_method}"/></span>
                            </c:otherwise>
                            </c:choose>
                        </td>
                        <td class="text-center">
                            <c:set var="_status" value="${tx.status}"/>
                            <c:choose>
                            <c:when test="${_status == 'COMPLETED'}">
                                <span class="badge bg-success"><i class="bi bi-check-lg me-1"></i>Completed</span>
                            </c:when>
                            <c:when test="${_status == 'PENDING'}">
                                <span class="badge bg-warning text-dark"><i class="bi bi-hourglass-split me-1"></i>Pending</span>
                            </c:when>
                            <c:when test="${_status == 'PROCESSING'}">
                                <span class="badge bg-info"><i class="bi bi-arrow-repeat me-1"></i>Processing</span>
                            </c:when>
                            <c:when test="${_status == 'FAILED'}">
                                <span class="badge bg-danger"><i class="bi bi-x-lg me-1"></i>Failed</span>
                            </c:when>
                            <c:when test="${_status == 'REFUNDED'}">
                                <span class="badge bg-secondary"><i class="bi bi-arrow-counterclockwise me-1"></i>Refunded</span>
                            </c:when>
                            <c:when test="${_status == 'PARTIALLY_REFUNDED'}">
                                <span class="badge bg-secondary text-dark"><i class="bi bi-arrow-counterclockwise me-1"></i>Partial Refund</span>
                            </c:when>
                            <c:when test="${_status == 'CHARGEBACK'}">
                                <span class="badge bg-danger"><i class="bi bi-exclamation-triangle me-1"></i>Chargeback</span>
                            </c:when>
                            <c:otherwise>
                                <span class="badge bg-light text-muted"><c:out value="${_status}"/></span>
                            </c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:choose>
                            <c:when test="${not empty tx.gatewayTransactionId}">
                                <code class="small"><c:out value="${tx.gatewayTransactionId}"/></code>
                            </c:when>
                            <c:otherwise>
                                <span class="text-muted small">—</span>
                            </c:otherwise>
                            </c:choose>
                        </td>
                        <td><fmt:formatDate value="${tx.createdAt}" pattern="dd MMM yyyy HH:mm"/></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty transactions}">
                    <tr><td colspan="9" class="text-center text-muted py-4">No transactions yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>

    <c:if test="${totalPages > 1}">
        <nav class="mt-3">
            <ul class="pagination pagination-sm justify-content-center">
                <li class="page-item ${page <= 1 ? 'disabled' : ''}">
                    <c:url var="prevUrl" value="/admin/transactions">
                        <c:if test="${not empty selectedStatus}"><c:param name="status" value="${selectedStatus.name()}"/></c:if>
                        <c:if test="${not empty selectedPaymentMethod}"><c:param name="paymentMethod" value="${selectedPaymentMethod}"/></c:if>
                        <c:param name="page" value="${page - 1}"/>
                    </c:url>
                    <a class="page-link" href="${prevUrl}">&laquo; Prev</a>
                </li>
                <li class="page-item disabled"><span class="page-link">Page ${page} / ${totalPages}</span></li>
                <li class="page-item ${page >= totalPages ? 'disabled' : ''}">
                    <c:url var="nextUrl" value="/admin/transactions">
                        <c:if test="${not empty selectedStatus}"><c:param name="status" value="${selectedStatus.name()}"/></c:if>
                        <c:if test="${not empty selectedPaymentMethod}"><c:param name="paymentMethod" value="${selectedPaymentMethod}"/></c:if>
                        <c:param name="page" value="${page + 1}"/>
                    </c:url>
                    <a class="page-link" href="${nextUrl}">Next &raquo;</a>
                </li>
            </ul>
        </nav>
    </c:if>
</div>
<%@ include file="../../layouts/footer.jspf" %>
