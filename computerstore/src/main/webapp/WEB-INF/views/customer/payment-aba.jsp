<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Pay with ABA Payway - Apach_PC/STORE"/>
<%@ include file="../layouts/header.jspf" %>

<div class="container py-4 aba-page">
    <div class="row justify-content-center">
        <div class="col-lg-7 col-xl-6">

            <c:choose>
            <%-- Already settled: the gateway confirmed, so show the receipt. --%>
            <c:when test="${order.paymentStatus == 'PAID'}">
                <div class="card card-hover aba-card text-center">
                    <div class="card-body p-4 p-md-5">
                        <div class="aba-status-icon aba-status-paid mb-3"><i class="bi bi-check-lg"></i></div>
                        <h4 class="fw-bold mb-2">Payment confirmed</h4>
                        <p class="text-muted mb-4">Order #${order.orderId} has been paid in full. Thank you!</p>
                        <div class="aba-amount money">$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></div>
                        <%-- receiptPath is pre-resolved by the servlet: it is the receipt
                             when a completed transaction row exists for this order, and the
                             order page when it does not. The view never guesses an id. --%>
                        <div class="d-grid gap-2 mt-4">
                            <a class="btn btn-brand" href="${pageContext.request.contextPath}${receiptPath}"><i class="bi bi-receipt me-2"></i>View receipt</a>
                            <a class="btn btn-outline-secondary" href="${pageContext.request.contextPath}/account/orders?id=${order.orderId}">View my order</a>
                            <a class="btn btn-outline-secondary" href="${pageContext.request.contextPath}/products">Continue shopping</a>
                        </div>
                    </div>
                </div>
            </c:when>

            <c:otherwise>
                <div class="card card-hover aba-card">
                    <div class="card-body p-4">
                        <div class="d-flex justify-content-between align-items-start mb-3">
                            <div>
                                <p class="eyebrow mb-1">Order #${order.orderId}</p>
                                <h5 class="fw-bold mb-0">Pay with ABA Payway</h5>
                            </div>
                            <span class="aba-badge"><i class="bi bi-bank me-1"></i>ABA</span>
                        </div>

                        <c:if test="${simulated}">
                        <div class="payment-demo-note mb-3" role="note">
                            <i class="bi bi-info-circle-fill"></i>
                            <span><strong>Simulation mode.</strong> This is the real payment flow running against a local simulator. The QR below is not a bank code and no money moves &mdash; but the order, the transaction record and the confirmation are all genuine.</span>
                        </div>
                        </c:if>

                        <div class="aba-amount-box">
                            <span>Amount to pay</span>
                            <strong class="money">$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></strong>
                        </div>

                        <%-- Bound once and compared as a string. EL coerces the
                             enum via toString(), which avoids a method call in the
                             expression -- this Tomcat has already bitten us once on
                             record accessors. --%>
                        <c:set var="paymentStatus" value="${payment.status}"/>
                        <c:choose>
                        <c:when test="${empty payment}">
                            <div class="text-center my-4">
                                <p class="text-muted mb-0">No payment has been started for this order yet.</p>
                            </div>
                        </c:when>

                        <c:when test="${paymentStatus == 'PENDING'}">
                            <div class="text-center my-4">
                                <c:choose>
                                <c:when test="${not empty payment.qrImage}">
                                    <img class="aba-qr" src="<c:out value='${payment.qrImage}'/>" alt="ABA Payway payment QR code" width="240" height="240">
                                </c:when>
                                <c:otherwise>
                                    <p class="text-muted">Preparing your payment&hellip;</p>
                                </c:otherwise>
                                </c:choose>
                                <c:if test="${not empty payment.abaPhone}">
                                <p class="aba-txn mt-3 mb-1">ABA number <strong><c:out value="${payment.abaPhone}"/></strong></p>
                                </c:if>
                                <p class="aba-txn mb-1">Transaction <code><c:out value="${payment.transactionId}"/></code></p>
                                <p class="text-muted small mb-0">Open the ABA app and scan this code, or enter the transaction id.</p>
                            </div>

                            <c:if test="${simulated}">
                            <div class="aba-demo-controls">
                                <p class="fw-semibold small mb-2"><i class="bi bi-sliders me-1"></i>Simulate what the gateway reports</p>
                                <div class="d-flex gap-2">
                                    <form method="post" action="${pageContext.request.contextPath}/payment/aba" class="flex-fill">
                                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                                        <input type="hidden" name="order" value="${order.orderId}">
                                        <input type="hidden" name="action" value="simulate_paid">
                                        <button type="submit" class="btn btn-success w-100"><i class="bi bi-check-lg me-1"></i>Payment succeeded</button>
                                    </form>
                                    <form method="post" action="${pageContext.request.contextPath}/payment/aba" class="flex-fill">
                                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                                        <input type="hidden" name="order" value="${order.orderId}">
                                        <input type="hidden" name="action" value="simulate_cancelled">
                                        <button type="submit" class="btn btn-outline-danger w-100"><i class="bi bi-x-lg me-1"></i>Customer cancelled</button>
                                    </form>
                                </div>
                            </div>
                            </c:if>

                            <div class="text-center mt-3">
                                <a class="btn btn-brand" href="${pageContext.request.contextPath}/payment/aba/return?order=${order.orderId}">
                                    <i class="bi bi-arrow-repeat me-2"></i>Check payment status
                                </a>
                                <p class="text-muted small mt-2 mb-0">This is where ABA sends the customer back. It re-checks with the gateway before marking anything paid.</p>
                            </div>
                        </c:when>

                        <c:otherwise>
                            <div class="text-center my-4">
                                <div class="aba-status-icon aba-status-failed mb-3"><i class="bi bi-x-lg"></i></div>
                                <h6 class="fw-bold mb-2">Payment ${paymentStatus}</h6>
                                <c:if test="${not empty payment.message}">
                                <p class="text-muted small"><c:out value="${payment.message}"/></p>
                                </c:if>
                                <p class="text-muted small mb-0">Transaction <code><c:out value="${payment.transactionId}"/></code></p>
                            </div>
                            <div class="d-grid gap-2">
                                <a class="btn btn-brand" href="${pageContext.request.contextPath}/account/orders?id=${order.orderId}">View my order</a>
                            </div>
                        </c:otherwise>
                        </c:choose>
                    </div>
                </div>
            </c:otherwise>
            </c:choose>

            <p class="text-center text-muted small mt-3 mb-0">
                Need help? <a href="${pageContext.request.contextPath}/products#faq">Read the FAQ</a> or use the contact channels in the footer.
            </p>
        </div>
    </div>
</div>
<%@ include file="../layouts/footer.jspf" %>
