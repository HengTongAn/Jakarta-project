<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Transaction #${transaction.transactionId} - Apach_PC/STORE"/>
<%@ include file="../../layouts/header.jspf" %>

<div class="container py-4">
    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <a href="${pageContext.request.contextPath}/account/transactions" class="btn btn-outline-secondary btn-sm">&larr; My transactions</a>
        <div class="d-flex flex-wrap align-items-center gap-2">
            <%-- Receipt link only for a settled payment. The page itself renders for
                 every attempt, so a receipt link shown unconditionally would offer a
                 document for a payment that never happened. --%>
            <c:set var="_txStatus" value="${transaction.status}"/>
            <c:set var="_txType" value="${transaction.transactionType}"/>
            <c:if test="${_txStatus.name() == 'COMPLETED' and _txType.name() == 'PAYMENT'}">
                <a href="${pageContext.request.contextPath}/account/transactions?id=${transaction.transactionId}&amp;view=receipt" class="btn btn-brand btn-sm">
                    <i class="bi bi-receipt me-1" aria-hidden="true"></i>View receipt
                </a>
            </c:if>
            <%@ include file="../../components/transaction-status-badge.jspf" %>
        </div>
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
                    <a href="${pageContext.request.contextPath}/account/orders?id=${transaction.orderId}">#${transaction.orderId}</a>
                </div>
                <div class="col-6 col-md-3">
                    <div class="fw-semibold text-body">Amount</div>
                    <span class="money">$<fmt:formatNumber value="${transaction.amount}" pattern="#,##0.00"/> <small class="text-muted"><c:out value="${transaction.currency}"/></small></span>
                </div>
                <div class="col-6 col-md-3">
                    <div class="fw-semibold text-body">Date</div>
                    <fmt:formatDate value="${transaction.createdAt}" pattern="dd MMM yyyy HH:mm"/>
                </div>
            </div>
        </div>
    </div>

    <div class="row g-3">
        <div class="col-lg-6">
            <div class="card card-hover h-100">
                <div class="card-body">
                    <h6 class="fw-bold mb-3">Payment</h6>
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

                        <dt class="col-5 text-muted fw-normal">Last updated</dt>
                        <dd class="col-7 mb-2"><fmt:formatDate value="${transaction.updatedAt}" pattern="dd MMM yyyy HH:mm"/></dd>
                    </dl>
                </div>
            </div>
        </div>

        <div class="col-lg-6">
            <div class="card card-hover h-100">
                <div class="card-body">
                    <h6 class="fw-bold mb-3">What the payment provider said</h6>
                    <dl class="row mb-0 small">
                        <dt class="col-5 text-muted fw-normal">Reference</dt>
                        <dd class="col-7 mb-2" style="overflow-wrap:anywhere;">
                            <c:choose>
                                <c:when test="${not empty transaction.gatewayTransactionId}">
                                    <code><c:out value="${transaction.gatewayTransactionId}"/></code>
                                </c:when>
                                <c:otherwise>
                                    <span class="text-muted">Not issued yet</span>
                                </c:otherwise>
                            </c:choose>
                        </dd>

                        <dt class="col-5 text-muted fw-normal">Result code</dt>
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
            <h6 class="fw-bold mb-0">Your attempts on order #${transaction.orderId}</h6>
        </div>
        <div class="table-responsive">
            <table class="table align-middle mb-0">
                <thead class="table-light">
                <tr><th>Transaction</th><th>Date</th><th class="text-end">Amount</th><th class="text-center">Status</th></tr>
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
                                    <a href="${pageContext.request.contextPath}/account/transactions?id=${row.transactionId}">#${row.transactionId}</a>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td><fmt:formatDate value="${row.createdAt}" pattern="dd MMM yyyy HH:mm"/></td>
                        <td class="text-end money">$<fmt:formatNumber value="${row.amount}" pattern="#,##0.00"/></td>
                        <td class="text-center">
                            <c:set var="_txStatus" value="${row.status}"/>
                            <%@ include file="../../components/transaction-status-badge.jspf" %>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty orderTransactions}">
                    <tr><td colspan="4" class="text-center text-muted py-4">No other attempts recorded on this order.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</div>
<%@ include file="../../layouts/footer.jspf" %>