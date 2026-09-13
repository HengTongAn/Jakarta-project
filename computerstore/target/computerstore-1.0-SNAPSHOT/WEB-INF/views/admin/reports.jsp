<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Reports - Admin"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4">
    <h4 class="fw-bold mb-3">Reports</h4>

    <div class="row g-3 mb-3">
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-primary bg-opacity-10 text-primary">O</div>
                    <div><div class="fs-4 fw-bold">${summary.totalOrders}</div><div class="small text-muted">Total orders</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-success bg-opacity-10 text-success">$</div>
                    <div><div class="fs-4 fw-bold money">$<fmt:formatNumber value="${summary.totalRevenue}" pattern="#,##0.00"/></div><div class="small text-muted">Total revenue</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-info bg-opacity-10 text-info">S</div>
                    <div><div class="fs-4 fw-bold">${summary.itemsSold}</div><div class="small text-muted">Items sold</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-secondary bg-opacity-10 text-secondary">P</div>
                    <div><div class="fs-4 fw-bold">${summary.productCount}</div><div class="small text-muted">Products in catalogue</div></div>
                </div>
            </div>
        </div>
    </div>

    <div class="row g-3 mb-3">
        <div class="col-lg-4">
            <div class="card card-hover h-100">
                <div class="card-body">
                    <h6 class="fw-bold mb-3">Orders by status</h6>
                    <table class="table table-sm table-borderless mb-0 small">
                        <tbody>
                        <tr><td>Pending</td><td class="text-end fw-semibold">${summary.pending}</td></tr>
                        <tr><td>Processing</td><td class="text-end fw-semibold">${summary.processing}</td></tr>
                        <tr><td>Completed</td><td class="text-end fw-semibold">${summary.completed}</td></tr>
                        <tr><td>Cancelled</td><td class="text-end fw-semibold">${summary.cancelled}</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        <div class="col-lg-4">
            <div class="card card-hover h-100">
                <div class="card-body">
                    <h6 class="fw-bold mb-3">Stock values</h6>
                    <table class="table table-sm table-borderless mb-0 small">
                        <tbody>
                        <tr><td>Total stock value</td><td class="text-end fw-semibold money">$<fmt:formatNumber value="${stockValues.totalStockValue}" pattern="#,##0.00"/></td></tr>
                        <tr><td>Low stock value</td><td class="text-end fw-semibold money">$<fmt:formatNumber value="${stockValues.lowStockValue}" pattern="#,##0.00"/></td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        <div class="col-lg-4">
            <div class="card card-hover h-100">
                <div class="card-body d-flex flex-column gap-2">
                    <h6 class="fw-bold mb-0">Alerts</h6>
                    <p class="small text-muted mb-0">
                        Low stock products: <b>${lowStock.size()}</b><br>
                        Out of stock products: <b>${outOfStock.size()}</b>
                    </p>
                    <a href="${pageContext.request.contextPath}/admin/inventory" class="btn btn-outline-primary btn-sm">Go to inventory</a>
                </div>
            </div>
        </div>
    </div>

    <div class="card card-hover">
        <div class="card-header bg-white fw-semibold">Restock attention (low stock)</div>
        <div class="table-responsive">
            <table class="table align-middle mb-0">
                <thead class="table-light">
                <tr><th>Product</th><th class="text-center">Stock</th><th class="text-end">Unit price</th></tr>
                </thead>
                <tbody>
                <c:forEach var="p" items="${lowStock}">
                    <tr>
                        <td class="small"><c:out value="${p.name}"/></td>
                        <td class="text-center text-warning fw-semibold">${p.stockQuantity}</td>
                        <td class="text-end money">$<fmt:formatNumber value="${p.price}" pattern="#,##0.00"/></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty lowStock}">
                    <tr><td colspan="3" class="text-center text-muted py-4">No low stock items.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>