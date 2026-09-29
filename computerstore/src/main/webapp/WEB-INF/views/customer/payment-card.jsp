<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Card payment - Apach_PC/STORE"/>
<%@ include file="../layouts/header.jspf" %>

<div class="container py-4 aba-page">
    <div class="row justify-content-center">
        <div class="col-lg-7 col-xl-6">
            <div class="card card-hover aba-card">
                <div class="card-body p-4">
                    <div class="d-flex justify-content-between align-items-start mb-3">
                        <div>
                            <p class="eyebrow mb-1">Order #${order.orderId}</p>
                            <h5 class="fw-bold mb-0">Card payment</h5>
                        </div>
                        <c:if test="${not empty payment}">
                        <span class="aba-badge"><i class="bi bi-credit-card-2-front me-1"></i>${payment.cardBrand}</span>
                        </c:if>
                    </div>

                    <c:if test="${simulated}">
                    <div class="payment-demo-note mb-3" role="note">
                        <i class="bi bi-info-circle-fill"></i>
                        <span><strong>Simulation mode.</strong> The order and the payment record are real. No card was charged and no number was stored.</span>
                    </div>
                    </c:if>

                    <div class="aba-amount-box">
                        <span>Order total</span>
                        <strong class="money">$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></strong>
                    </div>

                    <c:choose>
                    <c:when test="${empty payment}">
                        <div class="text-center my-4">
                            <p class="text-muted mb-0">No card payment has been attempted for this order.</p>
                        </div>
                    </c:when>

                    <c:when test="${order.paymentStatus == 'PAID'}">
                        <div class="text-center my-4">
                            <div class="aba-status-icon aba-status-paid mb-3"><i class="bi bi-check-lg"></i></div>
                            <h4 class="fw-bold mb-2">Payment confirmed</h4>
                            <p class="text-muted">
                                <c:out value="${payment.cardBrand}"/> ending <strong><c:out value="${payment.cardLast4}"/></strong>
                            </p>
                            <a class="btn btn-brand" href="${pageContext.request.contextPath}/account/orders?id=${order.orderId}">
                                <i class="bi bi-receipt me-2"></i>View my order
                            </a>
                        </div>
                    </c:when>

                    <c:otherwise>
                        <%-- Declined or otherwise not settled. The order is still
                             PENDING on purpose, so the customer can retry rather
                             than having lost the basket. --%>
                        <div class="text-center my-4">
                            <div class="aba-status-icon aba-status-failed mb-3"><i class="bi bi-x-lg"></i></div>
                            <h4 class="fw-bold mb-2">Payment not completed</h4>
                            <c:if test="${not empty payment.message}">
                            <p class="text-muted"><c:out value="${payment.message}"/></p>
                            </c:if>
                            <p class="text-muted small mb-0">
                                <c:out value="${payment.cardBrand}"/> ending <strong><c:out value="${payment.cardLast4}"/></strong>
                            </p>
                        </div>
                        <div class="d-grid gap-2">
                            <a class="btn btn-brand" href="${pageContext.request.contextPath}/checkout">
                                <i class="bi bi-arrow-repeat me-2"></i>Try a different card
                            </a>
                            <a class="btn btn-outline-secondary" href="${pageContext.request.contextPath}/account/orders?id=${order.orderId}">View my order</a>
                        </div>
                        <c:if test="${simulated}">
                        <p class="text-muted small text-center mt-3 mb-0">
                            The order is saved. You can retry as many times as you like &mdash; each attempt is recorded separately.
                        </p>
                        </c:if>
                    </c:otherwise>
                    </c:choose>
                </div>
            </div>

            <p class="text-center text-muted small mt-3 mb-0">
                We only ever keep the last four digits of a card. A full card number is never stored or logged.
            </p>
        </div>
    </div>
</div>
<%@ include file="../layouts/footer.jspf" %>
