<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Receipt #${transaction.transactionId} - Apach_PC/STORE"/>
<%-- receipt-only: the shell decides what a printed page keeps. See the
     "Receipt printing" block in assets/css/app/pages.css. --%>
<c:set var="bodyClass" value="receipt-page"/>
<%@ include file="../../layouts/header.jspf" %>

<%-- Settled state, bound once and compared as a string for the same reason the
     payment pages do it: EL coerces the enum via toString(), and this Tomcat
     has already bitten us once on record accessors. A pending or failed
     transaction has no receipt, and the banner below says so rather than
     printing an amount that was never taken. --%>
<c:set var="_txStatus" value="${transaction.status}"/>
<c:set var="_txSettled" value="${_txStatus.name() == 'COMPLETED'
                              and transaction.transactionType.name() == 'PAYMENT'}"/>
<c:set var="_txType" value="${transaction.transactionType}"/>
<c:set var="_txMethod" value="${transaction.paymentMethod}"/>

<div class="container py-4">
    <%-- The two actions the receipt exists for.

         Close is always offered: a customer looking at a declined or pending
         attempt still has to be able to leave, and the order page is the right
         place to send them. Print is not -- it sits inside the settled branch
         below, so a payment that never happened cannot be printed as though it
         had. --%>
    <div class="d-flex flex-wrap gap-2 mb-3 no-print">
        <a href="${pageContext.request.contextPath}/account/orders?id=${transaction.orderId}"
           class="btn btn-outline-secondary btn-sm">
            <i class="bi bi-x-lg me-1" aria-hidden="true"></i>Close
        </a>
        <c:if test="${_txSettled}">
            <button type="button" class="btn btn-brand btn-sm" id="printReceipt">
                <i class="bi bi-printer me-1" aria-hidden="true"></i>Print
            </button>
        </c:if>
    </div>

    <article class="card receipt-sheet" aria-labelledby="receiptHeading">
        <div class="card-body p-4 p-md-5">

            <%-- Merchant block. A real receipt is issued by someone: the store
                 identifies itself and gives the customer a reference to quote. --%>
            <div class="d-flex flex-wrap justify-content-between align-items-start gap-3 pb-3 mb-3 receipt-rule">
                <div>
                    <%-- receipt-brand, not footer-brand: the latter is #fff for the
                         dark footer and would print invisible on this white sheet. --%>
                    <p class="receipt-brand mb-1">
                        <img class="brand-logo-sm" src="${pageContext.request.contextPath}/assets/images/apach-pc-store.svg" alt="" aria-hidden="true">
                        <span>Apach_PC/STORE</span>
                    </p>
                    <p class="text-muted small mb-0">Computer parts and practical setups.</p>
                </div>
                <div class="text-md-end">
                    <h5 class="fw-bold mb-1" id="receiptHeading">Payment receipt</h5>
                    <p class="text-muted small mb-0">
                        Receipt <span class="fw-semibold text-body">#${transaction.transactionId}</span>
                    </p>
                </div>
            </div>

            <c:choose>
                <c:when test="${_txType.name() == 'REFUND' or _txType.name() == 'PARTIAL_REFUND'}">
                    <%-- A refund is COMPLETED, so the settled test above does not
                         catch it, and a refund page carrying "Payment received" and
                         a "Total paid" line would state the opposite of what
                         happened. Reachable by URL as well as by the button, which
                         is why the check is here and not only in the list. --%>
                    <div class="alert alert-info mb-4" role="alert">
                        <h6 class="fw-bold">
                            <i class="bi bi-arrow-counterclockwise me-1" aria-hidden="true"></i>
                            Money returned to you
                        </h6>
                        <p class="mb-0 small">
                            This is a refund of
                            $<fmt:formatNumber value="${transaction.amount}" pattern="#,##0.00"/>
                            on order #${transaction.orderId}, recorded on
                            <fmt:formatDate value="${transaction.createdAt}" pattern="dd MMM yyyy"/>.
                            It was processed by our team; contact support if it has not reached your account.
                        </p>
                    </div>

                    <dl class="row receipt-meta small mb-0">
                        <dt class="col-6 col-sm-3 text-muted fw-normal">Refund reference</dt>
                        <dd class="col-6 col-sm-9 mb-2">
                            <code class="receipt-ref">#${transaction.transactionId}</code>
                        </dd>

                        <dt class="col-6 col-sm-3 text-muted fw-normal">Original payment</dt>
                        <dd class="col-6 col-sm-9 mb-0">
                            <a href="${pageContext.request.contextPath}/account/orders?id=${transaction.orderId}">Order #${transaction.orderId}</a>
                        </dd>
                    </dl>
                </c:when>

                <c:when test="${not _txSettled}">
                    <%-- Reached by URL, not by the payment flow: a receipt only
                         exists for money that moved. Print is withheld here
                         because a printed "receipt" for a pending or failed
                         attempt is a document that looks like proof of
                         payment. --%>
                    <div class="alert alert-warning mb-0" role="alert">
                        <h6 class="fw-bold">
                            <i class="bi bi-exclamation-triangle me-1" aria-hidden="true"></i>
                            This transaction is not a completed payment
                        </h6>
                        <p class="mb-0 small">
                            Nothing was charged for it, so there is no receipt to issue. The record is
                            kept here because a declined or pending attempt is still worth seeing.
                        </p>
                    </div>
                </c:when>

                <c:otherwise>
                    <%-- Confirmation strip: the one thing a customer checks
                         first. --%>
                    <div class="receipt-paid mb-4">
                        <div class="receipt-paid-icon"><i class="bi bi-check-lg" aria-hidden="true"></i></div>
                        <div>
                            <p class="fw-bold mb-0">Payment received</p>
                            <p class="small text-muted mb-0">
                                Thank you. Keep this receipt for your records.
                            </p>
                        </div>
                    </div>

                    <%-- Who and when. --%>
                    <dl class="row receipt-meta mb-4">
                        <dt class="col-6 col-sm-3 text-muted fw-normal">Billed to</dt>
                        <dd class="col-6 col-sm-9 mb-2">
                            <c:out value="${transaction.customerName}"/>
                            <c:if test="${not empty transaction.customerUsername}">
                                <span class="text-muted small">(<c:out value="${transaction.customerUsername}"/>)</span>
                            </c:if>
                        </dd>

                        <dt class="col-6 col-sm-3 text-muted fw-normal">Order</dt>
                        <dd class="col-6 col-sm-9 mb-2">
                            <a href="${pageContext.request.contextPath}/account/orders?id=${transaction.orderId}">#${transaction.orderId}</a>
                        </dd>

                        <dt class="col-6 col-sm-3 text-muted fw-normal">Paid on</dt>
                        <dd class="col-6 col-sm-9 mb-2">
                            <fmt:formatDate value="${transaction.updatedAt}" pattern="dd MMM yyyy HH:mm"/>
                        </dd>

                        <dt class="col-6 col-sm-3 text-muted fw-normal">Payment method</dt>
                        <dd class="col-6 col-sm-9 mb-0">
                            <%@ include file="../../components/transaction-method-badge.jspf" %>
                        </dd>
                    </dl>

                    <%-- What was bought. This is the part that makes the page a
                         receipt rather than a payment confirmation: a customer
                         disputing a charge needs to see the lines. --%>
                    <div class="table-responsive">
                        <table class="table align-middle receipt-lines mb-0">
                            <thead>
                            <tr>
                                <th scope="col">Description</th>
                                <th scope="col" class="text-center">Qty</th>
                                <th scope="col" class="text-end">Unit price</th>
                                <th scope="col" class="text-end">Amount</th>
                            </tr>
                            </thead>
                            <tbody>
                            <c:choose>
                                <c:when test="${empty order.items}">
                                    <tr>
                                        <td colspan="4" class="text-center text-muted py-3">
                                            Line items are not available for this order.
                                        </td>
                                    </tr>
                                </c:when>
                                <c:otherwise>
                                    <c:forEach var="item" items="${order.items}">
                                        <tr>
                                            <td><c:out value="${item.productName}"/></td>
                                            <td class="text-center"><c:out value="${item.quantity}"/></td>
                                            <td class="text-end money">
                                                $<fmt:formatNumber value="${item.unitPrice}" pattern="#,##0.00"/>
                                            </td>
                                            <td class="text-end money">
                                                $<fmt:formatNumber value="${item.subtotal}" pattern="#,##0.00"/>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                </c:otherwise>
                            </c:choose>
                            </tbody>
                            <tfoot>
                                <tr>
                                    <td colspan="3" class="text-end fw-semibold">Total paid</td>
                                    <td class="text-end money fw-bold">
                                        $<fmt:formatNumber value="${transaction.amount}" pattern="#,##0.00"/>
                                        <small class="text-muted fw-normal d-block"><c:out value="${transaction.currency}"/></small>
                                    </td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>

                    <%-- Gateway block. The reference is what a customer quotes
                         to their bank, so it is printed verbatim and kept
                         selectable rather than drawn as an image. --%>
                    <dl class="row receipt-meta small mt-4 pt-3 receipt-rule mb-0">
                        <dt class="col-6 col-sm-3 text-muted fw-normal">Provider reference</dt>
                        <dd class="col-6 col-sm-9 mb-2">
                            <c:choose>
                                <c:when test="${not empty transaction.gatewayTransactionId}">
                                    <code class="receipt-ref"><c:out value="${transaction.gatewayTransactionId}"/></code>
                                </c:when>
                                <c:otherwise><span class="text-muted">Not issued</span></c:otherwise>
                            </c:choose>
                        </dd>

                        <dt class="col-6 col-sm-3 text-muted fw-normal">Result code</dt>
                        <dd class="col-6 col-sm-9 mb-2">
                            <c:choose>
                                <c:when test="${not empty transaction.gatewayResponseCode}">
                                    <code><c:out value="${transaction.gatewayResponseCode}"/></code>
                                </c:when>
                                <c:otherwise><span class="text-muted">&mdash;</span></c:otherwise>
                            </c:choose>
                        </dd>

                        <dt class="col-6 col-sm-3 text-muted fw-normal">Status</dt>
                        <dd class="col-6 col-sm-9 mb-0">
                            <%@ include file="../../components/transaction-status-badge.jspf" %>
                        </dd>
                    </dl>

                    <p class="text-muted small text-center mt-4 pt-3 mb-0 receipt-rule">
                        Questions about this charge? Quote order #${transaction.orderId} and the provider
                        reference above. A full copy stays in your account under My transactions.
                    </p>
                </c:otherwise>
            </c:choose>
        </div>
    </article>

    <p class="text-center text-muted small mt-3 mb-0 no-print">
        <a href="${pageContext.request.contextPath}/account/transactions?id=${transaction.transactionId}">
            View the full transaction record
        </a>
    </p>
</div>

<script>
    // Print is a button rather than a link so it cannot be middle-clicked into
    // a new tab that then prints the wrong document, and so it is unreachable
    // from the keyboard as a bare URL. The CSS in pages.css strips the site
    // chrome, so window.print() prints the receipt sheet and nothing else.
    (function () {
        var button = document.getElementById('printReceipt');
        if (button) {
            button.addEventListener('click', function () {
                window.print();
            });
        }
    })();
</script>

<%@ include file="../../layouts/footer.jspf" %>
