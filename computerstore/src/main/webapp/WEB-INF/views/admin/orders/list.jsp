<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Orders - Admin"/>
<%@ include file="../../layouts/header.jspf" %>
<%@ include file="../../layouts/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h4 class="fw-bold mb-0">Orders <span class="badge bg-secondary rounded-pill align-middle" id="ordersCount">${totalOrders}</span></h4>
        <div class="d-flex gap-2 align-items-center">
            <label for="ordersFilter" class="visually-hidden">Filter orders</label>
            <input type="search" id="ordersFilter" class="form-control form-control-sm table-filter" placeholder="Filter orders…">
            <form method="get" action="${pageContext.request.contextPath}/admin/orders" class="d-flex gap-2">
                <select name="status" class="form-select form-select-sm" onchange="this.form.submit()">
                    <option value="">All statuses</option>
                    <option value="PENDING" ${selectedStatus.name() == 'PENDING' ? 'selected' : ''}>Pending</option>
                    <option value="PROCESSING" ${selectedStatus.name() == 'PROCESSING' ? 'selected' : ''}>Processing</option>
                    <option value="SHIPPED" ${selectedStatus.name() == 'SHIPPED' ? 'selected' : ''}>Shipped</option>
                    <option value="COMPLETED" ${selectedStatus.name() == 'COMPLETED' ? 'selected' : ''}>Completed</option>
                    <option value="CANCELLED" ${selectedStatus.name() == 'CANCELLED' ? 'selected' : ''}>Cancelled</option>
                    <option value="REFUNDED" ${selectedStatus.name() == 'REFUNDED' ? 'selected' : ''}>Refunded</option>
                </select>
            </form>
        </div>
    </div>

    <div class="card card-hover">
        <div class="table-responsive">
            <table class="table align-middle mb-0" data-sortable data-filter-target="ordersFilter" data-count="ordersCount">
                <thead class="table-light">
                <tr>
                    <th data-sort="number">Order #</th><th>Customer</th><th>Date</th>
                    <th class="text-center" data-sort="number">Items</th>
                    <th class="text-end" data-sort="price">Total</th>
                    <th class="text-center">Status</th>
                    <th class="text-end" data-nosort>Actions</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="order" items="${orders}">
                    <tr>
                        <td>#${order.orderId}</td>
                        <td>
                            <div class="fw-semibold"><c:out value="${order.customerName}"/></div>
                            <div class="small text-muted"><c:out value="${order.customerUsername}"/></div>
                        </td>
                        <td><fmt:formatDate value="${order.orderDate}" pattern="dd MMM yyyy HH:mm"/></td>
                        <td class="text-center">${order.itemCount}</td>
                        <td class="text-end money">$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></td>
                        <td class="text-center">
                            <span data-order-status="${order.orderId}">
                                <c:set var="_statusLabel" value="${order.status}"/>
                                <%@ include file="../../components/status-badge.jspf" %>
                            </span>
                        </td>
                        <td class="text-end">
                            <a href="${pageContext.request.contextPath}/admin/orders?id=${order.orderId}" class="btn btn-outline-primary btn-sm">Manage</a>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty orders}">
                    <tr><td colspan="7" class="text-center text-muted py-4">No orders yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>

    <c:if test="${totalPages > 1}">
        <nav class="mt-3">
            <ul class="pagination pagination-sm justify-content-center">
                <li class="page-item ${page <= 1 ? 'disabled' : ''}">
                    <c:url var="prevUrl" value="/admin/orders">
                        <c:if test="${not empty selectedStatus}"><c:param name="status" value="${selectedStatus.name()}"/></c:if>
                        <c:param name="page" value="${page - 1}"/>
                    </c:url>
                    <a class="page-link" href="${prevUrl}">&laquo; Prev</a>
                </li>
                <li class="page-item disabled"><span class="page-link">Page ${page} / ${totalPages}</span></li>
                <li class="page-item ${page >= totalPages ? 'disabled' : ''}">
                    <c:url var="nextUrl" value="/admin/orders">
                        <c:if test="${not empty selectedStatus}"><c:param name="status" value="${selectedStatus.name()}"/></c:if>
                        <c:param name="page" value="${page + 1}"/>
                    </c:url>
                    <a class="page-link" href="${nextUrl}">Next &raquo;</a>
                </li>
            </ul>
        </nav>
    </c:if>
</div>
<%@ include file="../../layouts/footer.jspf" %>
