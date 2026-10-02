<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%--
    Two modes share this page:

      - no orderId: the whole ledger, with the status/method filters and pagination
      - orderId set: one order's transactions, reached from an order's page

    They share a table but not a heading, a toolbar or a set of stat cards, so the
    mode is branched once here rather than guessed at from which attributes happen to
    be set. A view that renders the global filters on an order-scoped request is
    offering controls the query behind them ignores.
--%>
<c:set var="_orderScoped" value="${not empty orderId}"/>
<c:set var="pageTitle" value="Transactions - Admin"/>
<%@ include file="../../layouts/header.jspf" %>
<%@ include file="../../layouts/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <div>
            <c:choose>
                <c:when test="${_orderScoped}">
                    <h4 class="fw-bold mb-1">Transactions for order #${orderId} <span class="badge bg-secondary rounded-pill align-middle" id="transactionsCount">${totalTransactions}</span></h4>
                    <a href="${pageContext.request.contextPath}/admin/orders?id=${orderId}" class="small me-2">Back to order #${orderId}</a>
                    <a href="${pageContext.request.contextPath}/admin/transactions" class="small">All transactions</a>
                </c:when>
                <c:otherwise>
                    <h4 class="fw-bold mb-0">Transactions <span class="badge bg-secondary rounded-pill align-middle" id="transactionsCount">${totalTransactions}</span></h4>
                </c:otherwise>
            </c:choose>
        </div>
        <div class="d-flex flex-wrap gap-2 align-items-center admin-filter-toolbar">
            <%-- The search box filters the rows already on the page, so it works in both modes.
                 The two selects below submit to the server and only the unfiltered query honours
                 them, which is why they belong to one mode only. --%>
            <label for="transactionsFilter" class="visually-hidden">Filter transactions</label>
            <input type="search" id="transactionsFilter" class="form-control form-control-sm table-filter" placeholder="Filter transactions…">
            <c:if test="${not _orderScoped}">
                <form method="get" action="${pageContext.request.contextPath}/admin/transactions" class="d-flex gap-2">
                    <label for="transactionStatusFilter" class="visually-hidden">Filter by status</label>
                    <select id="transactionStatusFilter" name="status" class="form-select form-select-sm" onchange="this.form.submit()">
                        <option value="">All statuses</option>
                        <option value="PENDING" ${not empty selectedStatus and selectedStatus.name() == 'PENDING' ? 'selected' : ''}>Pending</option>
                        <option value="PROCESSING" ${not empty selectedStatus and selectedStatus.name() == 'PROCESSING' ? 'selected' : ''}>Processing</option>
                        <option value="COMPLETED" ${not empty selectedStatus and selectedStatus.name() == 'COMPLETED' ? 'selected' : ''}>Completed</option>
                        <option value="FAILED" ${not empty selectedStatus and selectedStatus.name() == 'FAILED' ? 'selected' : ''}>Failed</option>
                        <option value="REFUNDED" ${not empty selectedStatus and selectedStatus.name() == 'REFUNDED' ? 'selected' : ''}>Refunded</option>
                        <option value="PARTIALLY_REFUNDED" ${not empty selectedStatus and selectedStatus.name() == 'PARTIALLY_REFUNDED' ? 'selected' : ''}>Partially Refunded</option>
                        <option value="CHARGEBACK" ${not empty selectedStatus and selectedStatus.name() == 'CHARGEBACK' ? 'selected' : ''}>Chargeback</option>
                    </select>
                    <label for="transactionMethodFilter" class="visually-hidden">Filter by payment method</label>
                    <select id="transactionMethodFilter" name="paymentMethod" class="form-select form-select-sm" onchange="this.form.submit()">
                        <option value="">All methods</option>
                        <option value="ABA" ${selectedPaymentMethod == 'ABA' ? 'selected' : ''}>ABA Payway</option>
                        <option value="CARD" ${selectedPaymentMethod == 'CARD' ? 'selected' : ''}>Card</option>
                        <option value="CASH" ${selectedPaymentMethod == 'CASH' ? 'selected' : ''}>Cash</option>
                    </select>
                </form>
            </c:if>
        </div>
    </div>

    <c:if test="${not _orderScoped}">
        <div class="row g-3 mb-3">
            <div class="col-md-3 col-xl-2">
                <div class="card card-hover">
                    <div class="card-body py-2">
                        <div class="small text-muted">Total</div>
                        <div class="fw-bold fs-5">${stats.totalTransactions}</div>
                    </div>
                </div>
            </div>
            <div class="col-md-3 col-xl-2">
                <div class="card card-hover">
                    <div class="card-body py-2">
                        <div class="small text-muted">Paid</div>
                        <div class="fw-bold fs-5 text-success money">$<fmt:formatNumber value="${stats.grossPayments}" pattern="#,##0.00"/></div>
                    </div>
                </div>
            </div>
            <div class="col-md-3 col-xl-2">
                <div class="card card-hover">
                    <div class="card-body py-2">
                        <div class="small text-muted">Refunded</div>
                        <div class="fw-bold fs-5 money">$<fmt:formatNumber value="${stats.grossRefunds}" pattern="#,##0.00"/></div>
                    </div>
                </div>
            </div>
            <div class="col-md-3 col-xl-2">
                <div class="card card-hover">
                    <div class="card-body py-2">
                        <div class="small text-muted">Net</div>
                        <div class="fw-bold fs-5 money">$<fmt:formatNumber value="${stats.netAmount}" pattern="#,##0.00"/></div>
                    </div>
                </div>
            </div>
            <div class="col-md-3 col-xl-2">
                <div class="card card-hover">
                    <div class="card-body py-2">
                        <div class="small text-muted">Failed</div>
                        <div class="fw-bold fs-5 text-danger">${stats.failedCount}</div>
                    </div>
                </div>
            </div>
            <div class="col-md-3 col-xl-2">
                <div class="card card-hover">
                    <div class="card-body py-2">
                        <div class="small text-muted">Pending</div>
                        <div class="fw-bold fs-5 text-warning">${stats.pendingCount}</div>
                    </div>
                </div>
            </div>
        </div>
    </c:if>

    <div class="card card-hover">
        <div class="table-responsive">
            <table class="table align-middle mb-0" data-sortable data-filter-target="transactionsFilter" data-count="transactionsCount">
                <thead class="table-light">
                <tr>
                    <th data-sort="number">ID</th>
                    <th data-sort="number">Order #</th>
                    <c:if test="${_orderScoped}"><th>Customer</th></c:if>
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
                        <td>
                            <a href="${pageContext.request.contextPath}/admin/transactions?id=${tx.transactionId}">#${tx.transactionId}</a>
                        </td>
                        <td>
                            <a href="${pageContext.request.contextPath}/admin/orders?id=${tx.orderId}">#${tx.orderId}</a>
                        </td>
                        <c:if test="${_orderScoped}">
                            <td>
                                <div class="fw-semibold"><c:out value="${tx.customerName}"/></div>
                                <div class="small text-muted"><c:out value="${tx.customerUsername}"/></div>
                            </td>
                        </c:if>
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
                        <td>
                            <c:choose>
                                <c:when test="${not empty tx.gatewayTransactionId}">
                                    <code class="small"><c:out value="${tx.gatewayTransactionId}"/></code>
                                </c:when>
                                <c:otherwise>
                                    <span class="text-muted small">&mdash;</span>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td><fmt:formatDate value="${tx.createdAt}" pattern="dd MMM yyyy HH:mm"/></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty transactions}">
                    <tr>
                        <td colspan="9" class="text-center py-4">
                            <c:choose>
                                <c:when test="${_orderScoped}">
                                    <span class="text-muted">No transactions were recorded against this order.</span>
                                </c:when>
                                <c:otherwise>
                                    <span class="text-muted">No transactions yet. A row appears here when a customer pays for an order.</span>
                                </c:otherwise>
                            </c:choose>
                        </td>
                    </tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>

    <c:if test="${not _orderScoped and totalPages > 1}">
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