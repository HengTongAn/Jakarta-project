<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Admin Dashboard - TechStore"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4">
    <h4 class="fw-bold mb-3">Admin Dashboard</h4>

    <div class="row g-3 mb-3">
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-primary bg-opacity-10 text-primary">P</div>
                    <div><div class="fs-4 fw-bold">${stats.totalProducts}</div><div class="small text-muted">Products</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-success bg-opacity-10 text-success">C</div>
                    <div><div class="fs-4 fw-bold">${stats.totalCustomers}</div><div class="small text-muted">Customers</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-info bg-opacity-10 text-info">O</div>
                    <div><div class="fs-4 fw-bold">${stats.totalOrders}</div><div class="small text-muted">Orders</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-warning bg-opacity-10 text-warning">$</div>
                    <div><div class="fs-4 fw-bold money">$<fmt:formatNumber value="${stats.totalRevenue}" pattern="#,##0.00"/></div><div class="small text-muted">Revenue</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-danger bg-opacity-10 text-danger">L</div>
                    <div><div class="fs-4 fw-bold">${stats.lowStockCount}</div><div class="small text-muted">Low stock</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-dark bg-opacity-10 text-dark">X</div>
                    <div><div class="fs-4 fw-bold">${stats.outOfStockCount}</div><div class="small text-muted">Out of stock</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-secondary bg-opacity-10 text-secondary">W</div>
                    <div><div class="fs-4 fw-bold">${stats.pendingOrders}</div><div class="small text-muted">Pending orders</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-primary bg-opacity-10 text-primary">+</div>
                    <div class="small text-muted">Quick links: <a href="${pageContext.request.contextPath}/admin/products?action=new">Add product</a></div>
                </div>
            </div>
        </div>
    </div>

    <div class="row g-3">
        <div class="col-lg-7">
            <div class="card card-hover">
                <div class="card-header bg-white fw-semibold">Recent orders</div>
                <div class="table-responsive">
                    <table class="table align-middle mb-0">
                        <thead class="table-light">
                        <tr><th>Order #</th><th>Customer</th><th>Date</th><th class="text-end">Total</th><th class="text-center">Status</th></tr>
                        </thead>
                        <tbody>
                        <c:forEach var="order" items="${stats.recentOrders}">
                            <tr>
                                <td><a href="${pageContext.request.contextPath}/admin/orders?id=${order.orderId}">#${order.orderId}</a></td>
                                <td><c:out value="${order.customerName}"/></td>
                                <td><fmt:formatDate value="${order.orderDate}" pattern="dd MMM HH:mm"/></td>
                                <td class="text-end money">$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></td>
                                <td class="text-center"><span class="badge bg-secondary badge-status">${order.status}</span></td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty stats.recentOrders}">
                            <tr><td colspan="5" class="text-center text-muted py-4">No orders placed yet.</td></tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        <div class="col-lg-5">
            <div class="card card-hover">
                <div class="card-header bg-white fw-semibold">Recent inventory activity</div>
                <div class="table-responsive">
                    <table class="table align-middle mb-0">
                        <thead class="table-light">
                        <tr><th>Product</th><th class="text-end">Qty change</th><th class="text-center">Action</th></tr>
                        </thead>
                        <tbody>
                        <c:forEach var="log" items="${stats.recentLogs}">
                            <tr>
                                <td class="small"><c:out value="${log.productName}"/></td>
                                <td class="text-end small">
                                    ${log.oldQuantity} &rarr; ${log.newQuantity}
                                </td>
                                <td class="text-center">
                                    <c:choose>
                                        <c:when test="${log.action == 'ORDER_CREATED'}"><span class="badge bg-info badge-status">Order</span></c:when>
                                        <c:when test="${log.action == 'ORDER_CANCELLED'}"><span class="badge bg-secondary badge-status">Cancelled</span></c:when>
                                        <c:otherwise><span class="badge bg-success badge-status">Adjust</span></c:otherwise>
                                    </c:choose>
                                </td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty stats.recentLogs}">
                            <tr><td colspan="3" class="text-center text-muted py-4">No inventory activity yet.</td></tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>