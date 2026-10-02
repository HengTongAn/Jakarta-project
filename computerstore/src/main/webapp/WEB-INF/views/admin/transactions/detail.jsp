<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Transaction #${transaction.transactionId} - Admin"/>
<%@ include file="../../layouts/header.jspf" %>
<%@ include file="../../layouts/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <a href="${pageContext.request.contextPath}/admin/transactions" class="btn btn-outline-secondary btn-sm">&larr; All transactions</a>
        <c:set var="_txStatus" value="${transaction.status}"/>
        <%@ include file="../../components/transaction-status-badge.jspf" %>
    </div>

    <div class="card card-hover mb-3">
        <div class="card-body">
            <div class="row text-muted small">
                <div class="col-6 col-md-3">
                    <div class="fw-semibold text-body">Transaction</div>
                    #${transaction.transactionId}
                </div>
                <div class="col-6 col-md-3">
                    <div class="fw-semibold text-body">Order</div>
                    <a href="${pageContext.request.contextPath}/admin/orders?id=${transaction.orderId}">#${transaction.orderId}</a>
                </div>
                <div class="col-6 col-md-3">
                    <div class="fw-semibold text-body">Customer</div>
                    <c:out value="${transaction.customerName}"/>
                    <div class="text-muted"><c:out value="${transaction.customerUsername}"/></div>
                </div>
                <div class="col-6 col-md-3">
                    <div class="fw-semibold text-body">Amount</div>
                    <span class="money">$<fmt:formatNumber value="${transaction.amount}" pattern="#,##0.00"/> <small class="text-muted"><c:out value="${transaction.currency}"/></small></span>
                </div>
            </div>
        </div>
    </div>

    <div class="card card-hover mb-3">
        <div class="card-body">
            <h6 class="fw-bold mb-3">Transaction actions</h6>
            <c:choose>
                <c:when test="${not empty settledRefund}">
                    <p class="text-muted mb-2">
                        This payment was already refunded by transaction
                        <a href="${pageContext.request.contextPath}/admin/transactions?id=${settledRefund.transactionId}">#${settledRefund.transactionId}</a>.
                    </p>
                    <c:set var="_txStatus" value="${settledRefund.status}"/>
                    <%@ include file="../../components/transaction-status-badge.jspf" %>
                </c:when>
                <c:when test="${empty refundBlockedReason}">
                    <form method="post" action="${pageContext.request.contextPath}/admin/transactions"
                          onsubmit="return confirm('Refund this payment in full? This will record a REFUND in the ledger and move the order to REFUNDED (stock restored). No payment provider will be called.');">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="action" value="refund">
                        <input type="hidden" name="transactionId" value="${transaction.transactionId}">
                        <button type="submit" class="btn btn-outline-danger">
                            <i class="bi bi-arrow-counterclockwise me-1" aria-hidden="true"></i>Refund payment in full
                        </button>
                    </form>
                    <p class="text-muted small mt-2 mb-0">
                        Refunds are ledger-only (no gateway call). The refund row is recorded as COMPLETED and the order is marked REFUNDED with stock restored.
                    </p>
                </c:when>
                <c:otherwise>
                    <p class="text-danger mb-0"><i class="bi bi-info-circle me-1" aria-hidden="true"></i><c:out value="${refundBlockedReason}"/></p>
                </c:otherwise>
            </c:choose>
        </div>
    </div>

    <div class="row g-3">
        <div class="col-lg-6">
            <div class="card card-hover h-100">
                <div class="card-body">
                    <h6 class="fw-bold mb-3">Movement</h6>
                    <dl class="row mb-0 small">
                        <dt class="col-5 text-muted fw-normal">Type</dt>
                        <dd class="col-7 mb-2">
                            <c:set var="_txType" value="${transaction.transactionType}"/>
                            <%@ include file="../../components/transaction-type-badge.jspf" %>
                        </dd>

                        <dt class="col-5 text-muted fw-normal">Method</dt>
                        <dd class="col-7 mb-2">
                            <c:set var="_txMethod" value="${transaction.paymentMethod}"/>
                            <%@ include file="../../components/transaction-method-badge.jspf" %>
                        </dd>

                        <dt class="col-5 text-muted fw-normal">Raised</dt>
                        <dd class="col-7 mb-2"><fmt:formatDate value="${transaction.createdAt}" pattern="dd MMM yyyy HH:mm:ss"/></dd>

                        <dt class="col-5 text-muted fw-normal">Last updated</dt>
                        <dd class="col-7 mb-2"><fmt:formatDate value="${transaction.updatedAt}" pattern="dd MMM yyyy HH:mm:ss"/></dd>

                        <c:if test="${not empty transaction.orderTotal}">
                            <dt class="col-5 text-muted fw-normal">Order total</dt>
                            <dd class="col-7 mb-2 money">$<fmt:formatNumber value="${transaction.orderTotal}" pattern="#,##0.00"/></dd>
                        </c:if>

                        <%-- Only meaningful for a payment: on a refund the same subtraction
                             reads as "how much was kept", which is noise rather than a
                             discrepancy. compareTo, not equals, because 40.00 and 40.0 are the
                             same amount and equals would call them different. --%>
                        <c:if test="${not empty transaction.orderTotal and transaction.transactionType.name() == 'PAYMENT'}">
                            <dt class="col-5 text-muted fw-normal">Matches order</dt>
                            <dd class="col-7 mb-2">
                                <c:choose>
                                    <c:when test="${transaction.amount.compareTo(transaction.orderTotal) == 0}">
                                        <span class="badge bg-success badge-status"><i class="bi bi-check-lg me-1" aria-hidden="true"></i>Yes</span>
                                    </c:when>
                                    <c:otherwise>
                                        <span class="badge bg-warning text-dark badge-status">
                                            <i class="bi bi-exclamation-triangle me-1" aria-hidden="true"></i>
                                            <fmt:formatNumber value="${transaction.amount.subtract(transaction.orderTotal)}" pattern="#,##0.00"/>
                                        </span>
                                    </c:otherwise>
                                </c:choose>
                            </dd>
                        </c:if>
                    </dl>
                </div>
            </div>
        </div>

        <div class="col-lg-6">
            <div class="card card-hover h-100">
                <div class="card-body">
                    <h6 class="fw-bold mb-3">Gateway response</h6>
                    <dl class="row mb-0 small">
                        <dt class="col-5 text-muted fw-normal">
                            <c:choose>
                                <c:when test="${transaction.transactionType.name() == 'REFUND' or transaction.transactionType.name() == 'PARTIAL_REFUND'}">
                                    Gateway refund ID
                                </c:when>
                                <c:otherwise>
                                    Gateway ID
                                </c:otherwise>
                            </c:choose>
                        </dt>
                        <dd class="col-7 mb-2" style="overflow-wrap:anywhere;">
                            <c:choose>
                                <c:when test="${not empty transaction.gatewayTransactionId}">
                                    <code><c:out value="${transaction.gatewayTransactionId}"/></code>
                                </c:when>
                                <%-- A refund never has one: no provider call was made, and
                                     inventing an id shaped like a real gateway reference
                                     would be the most misleading value on the page. The
                                     "Not issued yet" wording below would also be wrong for
                                     it -- nothing is pending. --%>
                                <c:when test="${transaction.transactionType.name() == 'REFUND' or transaction.transactionType.name() == 'PARTIAL_REFUND'}">
                                    <span class="text-muted">None &mdash; refunded in the ledger, no provider called</span>
                                </c:when>
                                <c:otherwise>
                                    <span class="text-muted">Not issued yet</span>
                                </c:otherwise>
                            </c:choose>
                        </dd>

                        <dt class="col-5 text-muted fw-normal">Response code</dt>
                        <dd class="col-7 mb-2">
                            <c:choose>
                                <c:when test="${not empty transaction.gatewayResponseCode}">
                                    <code><c:out value="${transaction.gatewayResponseCode}"/></code>
                                </c:when>
                                <c:otherwise>
                                    <span class="text-muted">&mdash;</span>
                                </c:otherwise>
                            </c:choose>
                        </dd>

                        <dt class="col-5 text-muted fw-normal">Message</dt>
                        <dd class="col-7 mb-2" style="overflow-wrap:anywhere;">
                            <c:choose>
                                <c:when test="${not empty transaction.gatewayResponseMessage}">
                                    <c:out value="${transaction.gatewayResponseMessage}"/>
                                </c:when>
                                <c:otherwise>
                                    <span class="text-muted">&mdash;</span>
                                </c:otherwise>
                            </c:choose>
                        </dd>
                    </dl>
                </div>
            </div>
        </div>
    </div>

    <div class="card card-hover mt-3">
        <div class="card-header bg-transparent">
            <h6 class="fw-bold mb-0">Every transaction on order #${transaction.orderId}</h6>
        </div>
        <div class="table-responsive">
            <table class="table align-middle mb-0">
                <thead class="table-light">
                <tr><th>ID</th><th>Type</th><th class="text-end">Amount</th><th>Method</th><th class="text-center">Status</th><th>Date</th></tr>
                </thead>
                <tbody>
                <c:forEach var="row" items="${orderTransactions}">
                    <tr${row.transactionId == transaction.transactionId ? ' class="table-active"' : ''}>
                        <td>
                            <c:choose>
                                <c:when test="${row.transactionId == transaction.transactionId}">
                                    #${row.transactionId} <span class="badge bg-secondary">this one</span>
                                </c:when>
                                <c:otherwise>
                                    <a href="${pageContext.request.contextPath}/admin/transactions?id=${row.transactionId}">#${row.transactionId}</a>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:set var="_txType" value="${row.transactionType}"/>
                            <%@ include file="../../components/transaction-type-badge.jspf" %>
                        </td>
                        <td class="text-end money">$<fmt:formatNumber value="${row.amount}" pattern="#,##0.00"/></td>
                        <td>
                            <c:set var="_txMethod" value="${row.paymentMethod}"/>
                            <%@ include file="../../components/transaction-method-badge.jspf" %>
                        </td>
                        <td class="text-center">
                            <c:set var="_txStatus" value="${row.status}"/>
                            <%@ include file="../../components/transaction-status-badge.jspf" %>
                        </td>
                        <td><fmt:formatDate value="${row.createdAt}" pattern="dd MMM yyyy HH:mm"/></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty orderTransactions}">
                    <tr><td colspan="6" class="text-center text-muted py-4">No other transactions recorded on this order.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</div>
<%@ include file="../../layouts/footer.jspf" %>