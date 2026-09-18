<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Admin Dashboard - TechStore"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex flex-wrap justify-content-between align-items-end gap-2 mb-4">
        <div>
            <h4 class="fw-bold mb-1">Admin Dashboard</h4>
            <p class="text-muted mb-0">Welcome back, <c:out value="${sessionScope.user.fullName}"/>. Here's how the store looks today.</p>
        </div>
        <a href="${pageContext.request.contextPath}/admin/products?action=new" class="btn btn-brand"><i class="bi bi-plus-lg" aria-hidden="true"></i> Add product</a>
    </div>

    <div class="row g-3 mb-3">
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-primary bg-opacity-10 text-primary"><i class="bi bi-box-seam" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold">${stats.totalProducts}</div><div class="small text-muted">Products</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-success bg-opacity-10 text-success"><i class="bi bi-people" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold">${stats.totalCustomers}</div><div class="small text-muted">Customers</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-info bg-opacity-10 text-info"><i class="bi bi-bag-check" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold">${stats.totalOrders}</div><div class="small text-muted">Orders</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-warning bg-opacity-10 text-warning"><i class="bi bi-currency-dollar" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold money">$<fmt:formatNumber value="${stats.totalRevenue}" pattern="#,##0.00"/></div><div class="small text-muted">Revenue</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-danger bg-opacity-10 text-danger"><i class="bi bi-exclamation-triangle" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold">${stats.lowStockCount}</div><div class="small text-muted">Low stock</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-dark bg-opacity-10 text-dark"><i class="bi bi-x-circle" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold">${stats.outOfStockCount}</div><div class="small text-muted">Out of stock</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-secondary bg-opacity-10 text-secondary"><i class="bi bi-clock-history" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold">${stats.pendingOrders}</div><div class="small text-muted">Pending orders</div></div>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card stock-health">
                <div class="card-body">
                    <div class="d-flex align-items-center gap-2 mb-2">
                        <span class="icon bg-primary bg-opacity-10 text-primary"><i class="bi bi-graph-up-arrow" aria-hidden="true"></i></span>
                        <span class="small fw-bold">Stock health</span>
                    </div>
                    <c:set var="inStock" value="${stats.totalProducts - stats.lowStockCount - stats.outOfStockCount}"/>
                    <div class="stock-health-bar mb-2" role="img"
                         aria-label="in stock ${inStock}, low stock ${stats.lowStockCount}, out of stock ${stats.outOfStockCount}">
                        <span class="bh bh-in" style="width:${stats.totalProducts == 0 ? 0 : inStock * 100 / stats.totalProducts}%"></span>
                        <span class="bh bh-low" style="width:${stats.totalProducts == 0 ? 0 : stats.lowStockCount * 100 / stats.totalProducts}%"></span>
                        <span class="bh bh-out" style="width:${stats.totalProducts == 0 ? 0 : stats.outOfStockCount * 100 / stats.totalProducts}%"></span>
                    </div>
                    <div class="small d-flex justify-content-between text-muted">
                        <span><i class="bi bi-circle-fill text-success small me-1" aria-hidden="true"></i>${inStock} in stock</span>
                        <span><i class="bi bi-circle-fill text-warning small me-1" aria-hidden="true"></i>${stats.lowStockCount} low</span>
                        <span><i class="bi bi-circle-fill text-danger small me-1" aria-hidden="true"></i>${stats.outOfStockCount} out</span>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <div class="row g-3 mb-3">
        <div class="col-lg-7">
            <section class="card card-hover dashboard-chart-card h-100" aria-labelledby="salesChartTitle">
                <div class="card-header bg-white d-flex justify-content-between align-items-center">
                    <div><h5 id="salesChartTitle" class="mb-0 fw-semibold">Recent sales</h5><span class="small text-muted">Latest completed store activity</span></div>
                    <span class="badge text-bg-primary"><i class="bi bi-broadcast me-1"></i>Live</span>
                </div>
                <div class="card-body"><div class="chart-wrap"><canvas id="salesChart" aria-label="Bar chart of recent order revenue" role="img"></canvas></div></div>
            </section>
        </div>
        <div class="col-lg-5">
            <section class="card card-hover dashboard-chart-card h-100" aria-labelledby="stockChartTitle">
                <div class="card-header bg-white"><h5 id="stockChartTitle" class="mb-0 fw-semibold">Inventory health</h5><span class="small text-muted">Products by current stock status</span></div>
                <div class="card-body"><div class="chart-wrap"><canvas id="stockChart" aria-label="Column chart of inventory health" role="img"></canvas></div></div>
            </section>
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
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.7/dist/chart.umd.min.js"></script>
<script>
(() => {
    const orders = [<c:forEach var="o" items="${stats.recentOrders}" varStatus="s">{label:'Order #${o.orderId}', value:${o.totalAmount}}${s.last ? '' : ','}</c:forEach>];
    const money = value => '$' + Number(value || 0).toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2});
    const common = { responsive:true, maintainAspectRatio:false, animation:{duration:700}, plugins:{legend:{display:false}, tooltip:{callbacks:{label:c => money(c.parsed.y)}}} };
    new Chart(document.getElementById('salesChart'), {type:'bar', data:{labels:orders.map(o=>o.label), datasets:[{data:orders.map(o=>o.value), borderRadius:9, borderSkipped:false, backgroundColor:'rgba(37,99,235,.82)', hoverBackgroundColor:'#1d4ed8'}]}, options:{...common, scales:{x:{grid:{display:false}}, y:{beginAtZero:true, ticks:{callback:money}, grid:{color:'rgba(148,163,184,.18)'}}}}});
    new Chart(document.getElementById('stockChart'), {type:'bar', data:{labels:['In stock','Low stock','Out of stock'], datasets:[{data:[${inStock},${stats.lowStockCount},${stats.outOfStockCount}], borderRadius:10, borderSkipped:false, backgroundColor:['#16a34a','#f59e0b','#ef4444']}]}, options:{...common, scales:{x:{grid:{display:false}}, y:{beginAtZero:true, ticks:{precision:0}, grid:{color:'rgba(148,163,184,.18)'}}}}});
    window.setTimeout(() => window.location.reload(), 60000);
})();
</script>
<%@ include file="../common/footer.jspf" %>
