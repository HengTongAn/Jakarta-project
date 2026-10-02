<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="My Transactions - Apach_PC/STORE"/>
<%@ include file="../../layouts/header.jspf" %>

<div class="container py-4">
    <h4 class="fw-bold mb-3">My Transactions</h4>

    <div class="row g-3">
        <div class="col-lg-3">
            <%@ include file="account-nav.jspf" %>
        </div>

        <div class="col-lg-9">
            <div class="row g-3 mb-3">
                <div class="col-sm-4">
                    <div class="card card-hover h-100">
                        <div class="card-body py-2">
                            <div class="small text-muted">Paid</div>
                            <div class="fw-bold fs-5 text-success money">$<fmt:formatNumber value="${stats.grossPayments}" pattern="#,##0.00"/></div>
                        </div>
                    </div>
                </div>
                <div class="col-sm-4">
                    <div class="card card-hover h-100">
                        <div class="card-body py-2">
                            <div class="small text-muted">Refunded</div>
                            <div class="fw-bold fs-5 money">$<fmt:formatNumber value="${stats.grossRefunds}" pattern="#,##0.00"/></div>
                        </div>
                    </div>
                </div>
                <div class="col-sm-4">
                    <div class="card card-hover h-100">
                        <div class="card-body py-2">
                            <div class="small text-muted">Net paid</div>
                            <div class="fw-bold fs-5 money">$<fmt:formatNumber value="${stats.netAmount}" pattern="#,##0.00"/></div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="card card-hover">
                <div class="table-responsive">
                    <table class="table align-middle mb-0">
                        <thead class="table-light">
                        <tr><th>Order #</th><th>Date</th><th>Type</th><th class="text-end">Amount</th><th>Method</th><th class="text-center">Status</th><th></th></tr>
                        </thead>
                        <tbody>
                        <c:choose>
                            <c:when test="${empty transactions}">
                                <tr>
                                    <td colspan="7">
                                        <div class="text-center py-4 empty-state">
                                            <span class="empty-icon"><i class="bi bi-currency-dollar"></i></span>
                                            <h6 class="fw-bold mb-1">No transactions yet</h6>
                                            <p class="text-muted mb-3">
                                                A record appears here whenever you pay for an order, whether it goes through or is declined.
                                            </p>
                                            <a href="${pageContext.request.contextPath}/account/orders" class="btn btn-brand btn-sm">My orders</a>
                                        </div>
                                    </td>
                                </tr>
                            </c:when>
                            <c:otherwise>
                                <c:forEach var="tx" items="${transactions}">
                                    <tr>
                                        <td>
                                            <a href="${pageContext.request.contextPath}/account/orders?id=${tx.orderId}">#${tx.orderId}</a>
                                        </td>
                                        <td><fmt:formatDate value="${tx.createdAt}" pattern="dd MMM yyyy HH:mm"/></td>
                                        <td>
                                            <c:set var="_txType" value="${tx.transactionType}"/>
                                            <%@ include file="../../components/transaction-type-badge.jspf" %>
                                        </td>
                                        <td class="text-end money">$<fmt:formatNumber value="${tx.amount}" pattern="#,##0.00"/></td>
                                        <td>
                                            <c:set var="_txMethod" value="${tx.paymentMethod}"/>
                                            <%@ include file="../../components/transaction-method-badge.jspf" %>
                                        </td>
                                        <td class="text-center">
                                            <c:set var="_txStatus" value="${tx.status}"/>
                                            <%@ include file="../../components/transaction-status-badge.jspf" %>
                                        </td>
                                        <td class="text-end">
                                            <%-- Two actions, not one: the detail page explains a
                                                 transaction, the receipt is the document the customer
                                                 keeps. Only settled payments can be receipted, so the
                                                 receipt is offered on that row alone -- a receipt for a
                                                 declined or pending attempt would be a document that
                                                 looks like proof of money that never arrived.

                                                 The type check is not redundant with the status one.
                                                 A refund is COMPLETED too, so an admin refund shows up
                                                 in this customer's list looking exactly as settled as
                                                 their payment did, and would otherwise offer a "receipt"
                                                 for money coming the other way. --%>
                                            <c:set var="_txSettled" value="${tx.status.name() == 'COMPLETED'
                                                                    and tx.transactionType.name() == 'PAYMENT'}"/>
                                            <c:if test="${_txSettled}">
                                                <a href="${pageContext.request.contextPath}/account/transactions?id=${tx.transactionId}&amp;view=receipt" class="btn btn-outline-primary btn-sm">
                                                    <i class="bi bi-receipt me-1" aria-hidden="true"></i>Receipt
                                                </a>
                                            </c:if>
                                            <a href="${pageContext.request.contextPath}/account/transactions?id=${tx.transactionId}" class="btn btn-outline-secondary btn-sm">Details</a>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../../layouts/footer.jspf" %>