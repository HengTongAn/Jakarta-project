<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="My Account - TechStore"/>
<%@ include file="../../layouts/header.jspf" %>
<div class="container py-4">
    <div class="row g-3">
        <div class="col-lg-3">
            <div class="card card-hover mb-3">
                <div class="card-body text-center">
                    <c:choose>
                        <c:when test="${not empty sessionScope.user.avatarUrl}">
                            <img class="avatar-circle avatar-circle-image mx-auto mb-2"
                                 src="${pageContext.request.contextPath}/${sessionScope.user.avatarUrl}"
                                 alt="Profile picture">
                        </c:when>
                        <c:otherwise>
                            <div class="avatar-circle mx-auto mb-2">${empty sessionScope.user.fullName ? '?' : sessionScope.user.fullName.substring(0,1)}</div>
                        </c:otherwise>
                    </c:choose>
                    <h6 class="fw-bold mb-0"><c:out value="${sessionScope.user.fullName}"/></h6>
                    <p class="text-muted small mb-0"><c:out value="${sessionScope.user.email}"/></p>
                </div>
            </div>
            <div class="card card-hover customer-sidebar">
                <div class="card-body p-0">
                    <div class="list-group list-group-flush">
                        <a class="list-group-item list-group-item-action active" href="${pageContext.request.contextPath}/account"><i class="bi bi-speedometer2 me-2"></i>Dashboard</a>
                        <a class="list-group-item list-group-item-action" href="${pageContext.request.contextPath}/account/orders"><i class="bi bi-box-seam me-2"></i>My Orders</a>
                        <a class="list-group-item list-group-item-action" href="${pageContext.request.contextPath}/account/profile"><i class="bi bi-person-vcard me-2"></i>Profile</a>
                        <a class="list-group-item list-group-item-action" href="${pageContext.request.contextPath}/account/settings"><i class="bi bi-shield-lock me-2"></i>Security</a>
                    </div>
                </div>
            </div>
        </div>

        <div class="col-lg-9">
            <div class="row g-3 mb-3 account-stats">
                <div class="col-md-4">
                    <div class="card stats-card">
                        <div class="card-body d-flex align-items-center gap-3">
                            <div class="icon bg-primary bg-opacity-10 text-primary">O</div>
                            <div><div class="fs-4 fw-bold">${recentOrders.size()}</div><div class="small text-muted">Recent orders</div></div>
                        </div>
                    </div>
                </div>
                <div class="col-md-4">
                    <div class="card stats-card">
                        <div class="card-body d-flex align-items-center gap-3">
                            <div class="icon bg-success bg-opacity-10 text-success">C</div>
                            <div><div class="fs-4 fw-bold">${cartCount}</div><div class="small text-muted">Cart items</div></div>
                        </div>
                    </div>
                </div>
                <div class="col-md-4">
                    <div class="card stats-card">
                        <div class="card-body d-flex align-items-center gap-3">
                            <div class="icon bg-info bg-opacity-10 text-info">P</div>
                            <div><div class="fs-4 fw-bold"><a href="${pageContext.request.contextPath}/account/profile">Edit profile</a></div><div class="small text-muted">Update details</div></div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="card card-hover">
<div class="card-header bg-body fw-semibold d-flex justify-content-between align-items-center">                    Recent orders
                    <a href="${pageContext.request.contextPath}/account/orders" class="small">View all</a>
                </div>
                <div class="table-responsive">
                    <table class="table align-middle mb-0">
                        <thead class="table-light">
                        <tr><th>Order #</th><th>Date</th><th>Items</th><th class="text-end">Total</th><th class="text-center">Status</th></tr>
                        </thead>
                        <tbody>
                        <c:forEach var="order" items="${recentOrders}">
                            <tr>
                                <td><a href="${pageContext.request.contextPath}/account/orders?id=${order.orderId}">#${order.orderId}</a></td>
                                <td><fmt:formatDate value="${order.orderDate}" pattern="dd MMM yyyy"/></td>
                                <td><c:out value="${order.itemCount}"/></td>
                                <td class="text-end money">$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></td>
<td class="text-center">
                                        <span data-order-status="${order.orderId}">
                                            <c:set var="_statusLabel" value="${order.status}"/>
                                            <%@ include file="../../components/status-badge.jspf" %>
                                        </span>
                                    </td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty recentOrders}">
                            <tr><td colspan="5" class="text-center text-muted py-4">You have not placed any orders yet.</td></tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../../layouts/footer.jspf" %>
