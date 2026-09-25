<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Admin Dashboard - TechStore"/>
<%@ include file="../layouts/header.jspf" %>
<%@ include file="../layouts/admin-nav.jspf" %>

<div class="container py-4" data-dashboard>
    <div class="d-flex flex-wrap justify-content-between align-items-end gap-2 mb-4">
        <div>
            <h4 class="fw-bold mb-1">Admin Dashboard</h4>
            <p class="text-muted mb-0">Welcome back, <c:out value="${sessionScope.user.fullName}"/>. Here's how the store looks today.</p>
        </div>
        <a href="${pageContext.request.contextPath}/admin/products?action=new" class="btn btn-brand"><i class="bi bi-plus-lg" aria-hidden="true"></i> Add product</a>
    </div>

    <div class="row g-3 mb-3 row-cols-2 row-cols-md-3 row-cols-xl-5">
        <div class="col">
            <a class="card stats-card stats-card-link text-decoration-none h-100" href="${pageContext.request.contextPath}/admin/reports"
               data-help="Money from paid orders - cancelled and refunded orders are left out."
               data-help-title="Revenue"
               data-help-why="The store's main number. Revenue down while orders grow? Prices or discounts need a look.">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-warning bg-opacity-10 text-warning"><i class="bi bi-currency-dollar" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold money" data-live-stat="revenue">$<fmt:formatNumber value="${stats.totalRevenue}" pattern="#,##0.00"/></div><div class="small text-muted">Revenue</div></div>
                    <span class="admin-help-toggle ms-auto" role="button" tabindex="0" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></span>
                </div>
            </a>
        </div>
        <div class="col">
            <a class="card stats-card stats-card-link text-decoration-none h-100" href="${pageContext.request.contextPath}/admin/orders"
               data-help="Orders waiting for you to process them."
               data-help-title="Pending orders"
               data-help-why="Customers are waiting on these - handle them fast to keep reviews happy and refunds rare.">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-secondary bg-opacity-10 text-secondary"><i class="bi bi-clock-history" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold" data-live-stat="pendingOrders">${stats.pendingOrders}</div><div class="small text-muted">Pending orders</div></div>
                    <span class="admin-help-toggle ms-auto" role="button" tabindex="0" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></span>
                </div>
            </a>
        </div>
        <div class="col">
            <a class="card stats-card stats-card-link text-decoration-none h-100" href="${pageContext.request.contextPath}/admin/inventory"
               data-help="Products with only a few units left."
               data-help-title="Low stock"
               data-help-why="Restock before they hit zero - an empty shelf is a lost sale.">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-danger bg-opacity-10 text-danger"><i class="bi bi-exclamation-triangle" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold" data-live-stat="lowStockCount">${stats.lowStockCount}</div><div class="small text-muted">Low stock</div></div>
                    <span class="admin-help-toggle ms-auto" role="button" tabindex="0" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></span>
                </div>
            </a>
        </div>
        <div class="col">
            <a class="card stats-card stats-card-link text-decoration-none h-100" href="${pageContext.request.contextPath}/admin/reviews"
               data-help="Customer reviews waiting for approval before they go public."
               data-help-title="Pending reviews"
               data-help-why="Approve the good ones fast and act on problem reviews early - both protect the store's reputation.">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-warning bg-opacity-10 text-warning"><i class="bi bi-star-half" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold" data-live-stat="pendingReviews">${stats.pendingReviews}</div><div class="small text-muted">Pending reviews</div></div>
                    <span class="admin-help-toggle ms-auto" role="button" tabindex="0" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></span>
                </div>
            </a>
        </div>
        <div class="col">
            <a class="card stats-card stats-card-link text-decoration-none h-100" href="${pageContext.request.contextPath}/admin/products"
               data-help="How many products are in the catalogue."
               data-help-title="Products"
               data-help-why="A quick health check - a surprise drop means something was deleted or discontinued.">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-primary bg-opacity-10 text-primary"><i class="bi bi-box-seam" aria-hidden="true"></i></div>
                    <div><div class="fs-4 fw-bold" data-live-stat="totalProducts">${stats.totalProducts}</div><div class="small text-muted">Products</div></div>
                    <span class="admin-help-toggle ms-auto" role="button" tabindex="0" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></span>
                </div>
            </a>
        </div>
    </div>

    <div class="row g-3 mb-3">
        <div class="col-lg-7">
            <section class="card card-hover dashboard-chart-card h-100" aria-labelledby="salesChartTitle"
             data-help="The latest sales, colored by status. Cancelled and refunded ones are hidden so the chart matches real revenue."
             data-help-title="Recent sales"
             data-help-why="Spot a promotion spike or an evening rush at a glance - and this chart updates itself, so there is no refresh to remember.">
<div class="card-header bg-body d-flex justify-content-between align-items-center">                    <div><h5 id="salesChartTitle" class="mb-0 fw-semibold">Recent sales</h5><span class="small text-muted">Latest completed store activity</span></div>
                    <span class="d-inline-flex align-items-center gap-2">
                        <span class="badge text-bg-primary" data-live-indicator><i class="bi bi-broadcast me-1" aria-hidden="true"></i><span data-live-indicator-label>Live</span></span>
                        <button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                    </span>
                </div>
                <div class="card-body"><div class="chart-wrap"><canvas id="salesChart" aria-label="Bar chart of recent order revenue" role="img"></canvas><div class="chart-empty d-none" data-sales-empty>No recent sales to display yet.</div></div></div>
            </section>
        </div>
        <div class="col-lg-5">
            <section class="card card-hover dashboard-chart-card h-100" aria-labelledby="stockChartTitle"
             data-help="The whole catalogue split into in stock, low stock and out of stock."
             data-help-title="Inventory health"
             data-help-why="One glance tells you if the store can keep selling - lots of red means restocking is urgent.">
<div class="card-header bg-body d-flex justify-content-between align-items-center"><div><h5 id="stockChartTitle" class="mb-0 fw-semibold">Inventory health</h5><span class="small text-muted">Products by current stock status</span></div>
                    <button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                </div>                <div class="card-body"><div class="chart-wrap"><canvas id="stockChart" aria-label="Column chart of inventory health" role="img"></canvas></div></div>
            </section>
        </div>
    </div>

    <div class="row g-3">
        <div class="col-lg-7">
            <div class="card card-hover"
             data-help="The newest orders with their current status."
             data-help-title="Recent orders"
             data-help-why="See orders the moment they land and act on them without leaving this page.">
<div class="card-header bg-body fw-semibold d-flex justify-content-between align-items-center">
                    <span>Recent orders</span>
                    <button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                </div>                <div class="table-responsive">
                    <table class="table align-middle mb-0">
                        <thead class="table-light">
                        <tr><th>Order #</th><th>Customer</th><th>Date</th><th class="text-end">Total</th><th class="text-center">Status</th></tr>
                        </thead>
                        <tbody data-recent-orders>
                        <c:forEach var="order" items="${stats.recentOrders}">
                            <tr>
                                <td><a href="${pageContext.request.contextPath}/admin/orders?id=${order.orderId}">#${order.orderId}</a></td>
                                <td><c:out value="${order.customerName}"/></td>
                                <td><fmt:formatDate value="${order.orderDate}" pattern="dd MMM HH:mm"/></td>
                                <td class="text-end money">$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></td>
                                <td class="text-center">
                                    <c:set var="_statusLabel" value="${order.status}"/>
                                    <%@ include file="../components/status-badge.jspf" %>
                                </td>
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
            <div class="card card-hover"
             data-help="The latest stock changes: new orders, cancellations and manual adjustments."
             data-help-title="Recent inventory activity"
             data-help-why="Know why stock moved - it catches problems like a double-deducted product or an unexpected cancellation.">
<div class="card-header bg-body fw-semibold d-flex justify-content-between align-items-center">
                    <span>Recent inventory activity</span>
                    <button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                </div>                <div class="table-responsive">
                    <table class="table align-middle mb-0">
                        <thead class="table-light">
                        <tr><th>Product</th><th class="text-end">Qty change</th><th class="text-center">Action</th></tr>
                        </thead>
                        <tbody data-recent-logs>
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
                                        <c:when test="${log.action == 'MANUAL_ADJUST_RECEIVED'}"><span class="badge bg-success badge-status">Received</span></c:when>
                                        <c:when test="${log.action == 'MANUAL_ADJUST_DAMAGED'}"><span class="badge bg-danger badge-status">Damaged</span></c:when>
                                        <c:when test="${log.action == 'MANUAL_ADJUST_COUNT'}"><span class="badge bg-success badge-status">Counted</span></c:when>
                                        <c:when test="${log.action == 'MANUAL_ADJUST_RETURNED'}"><span class="badge bg-secondary badge-status">Return</span></c:when>
                                        <c:when test="${log.action == 'MANUAL_ADJUST_CORRECTION'}"><span class="badge bg-warning text-dark badge-status">Correction</span></c:when>
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
<c:set var="inStock" value="${stats.totalProducts - stats.lowStockCount - stats.outOfStockCount}"/>
<script src="${pageContext.request.contextPath}/assets/vendor/chart.js/chart.umd.min.js"></script>
<script>
(() => {
    const orders = [<c:forEach var="o" items="${stats.recentOrders}" varStatus="s">{label:'Order #${o.orderId}', value:${o.totalAmount}, status:'${o.status}'}${s.last ? '' : ','}</c:forEach>];
    const money = value => '$' + Number(value || 0).toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2});
    const common = { responsive:true, maintainAspectRatio:false, animation:{duration:700}, plugins:{legend:{display:false}, tooltip:{callbacks:{label:c => money(c.parsed.y)}}} };
    // The Revenue card excludes CANCELLED/REFUNDED in its SQL, so the chart
    // must match: those bars would otherwise overstate revenue.
    const STATUS_COLORS = { PENDING:'#f59e0b', PROCESSING:'#0ea5e9', SHIPPED:'#2563eb', COMPLETED:'#16a34a' };
    const visibleOrders = orders.filter(o => o.status !== 'CANCELLED' && o.status !== 'REFUNDED');
    const salesCanvas = document.getElementById('salesChart');
    const salesEmpty = document.querySelector('[data-sales-empty]');
    salesCanvas.classList.toggle('d-none', visibleOrders.length === 0);
    if (salesEmpty) {
        salesEmpty.classList.toggle('d-none', visibleOrders.length > 0);
    }
    window.dashCharts = {
        sales: new Chart(salesCanvas, {type:'bar', data:{labels:visibleOrders.map(o=>o.label), datasets:[{data:visibleOrders.map(o=>o.value), borderRadius:9, borderSkipped:false, backgroundColor:visibleOrders.map(o => STATUS_COLORS[o.status] || 'rgba(37,99,235,.82)'), hoverBackgroundColor:'#1d4ed8'}]}, options:{...common, scales:{x:{grid:{display:false}}, y:{beginAtZero:true, ticks:{callback:money}, grid:{color:'rgba(148,163,184,.18)'}}}}}),
        stock: new Chart(document.getElementById('stockChart'), {type:'bar', data:{labels:['In stock','Low stock','Out of stock'], datasets:[{data:[${inStock},${stats.lowStockCount},${stats.outOfStockCount}], borderRadius:10, borderSkipped:false, backgroundColor:['#16a34a','#f59e0b','#ef4444']}]}, options:{...common, scales:{x:{grid:{display:false}}, y:{beginAtZero:true, ticks:{precision:0}, grid:{color:'rgba(148,163,184,.18)'}}}}})
    };
})();
</script>
<script src="${pageContext.request.contextPath}/assets/js/dashboard-live.js"></script>
<%@ include file="../layouts/footer.jspf" %>
