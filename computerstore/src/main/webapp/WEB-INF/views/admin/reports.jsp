<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Reports - Admin"/>
<%@ include file="../layouts/header.jspf" %>
<%@ include file="../layouts/admin-nav.jspf" %>

<div class="container py-4" data-reports data-reports-range="${activeRange}">
    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3"
         data-help="Sales analytics for a period you choose: revenue, orders, best sellers and stock value."
         data-help-title="These reports"
         data-help-why="Answer questions like 'is this week better than last?' or 'what sells best all time?' in one click.">
        <div>
            <h4 class="fw-bold mb-0">Reports</h4>
            <span class="small text-muted">Sales analytics · stock valuation</span>
        </div>
        <span class="d-flex align-items-center gap-2">
            <div class="btn-group" role="group" aria-label="Report period">
                <a class="btn btn-sm ${activeRange == 'today' ? 'btn-primary' : 'btn-outline-primary'}"
                   href="?range=today">Today</a>
                <a class="btn btn-sm ${activeRange == '7d' ? 'btn-primary' : 'btn-outline-primary'}"
                   href="?range=7d">Last 7 days</a>
                <a class="btn btn-sm ${activeRange == '30d' ? 'btn-primary' : 'btn-outline-primary'}"
                   href="?range=30d">Last 30 days</a>
                <a class="btn btn-sm ${activeRange == 'all' ? 'btn-primary' : 'btn-outline-primary'}"
                   href="?range=all">All time</a>
            </div>
            <button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this page?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
        </span>
    </div>

    <div class="row g-3 mb-3">
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card"
             data-help="Net revenue for this period, from paid orders only."
             data-help-title="Revenue"
             data-help-why="The headline number for the window you picked - compare Today against 30 days to see the trend.">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-primary bg-opacity-10 text-primary">$</div>
                    <div>
                        <div class="fs-4 fw-bold money" data-live-report="totalRevenue">$<fmt:formatNumber value="${summary.totalRevenue}" pattern="#,##0.00"/></div>
                        <div class="small text-muted">Revenue · ${periodLabel}</div>
                    </div>
                    <button type="button" class="admin-help-toggle ms-auto" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card"
             data-help="How many items left the shelves in this period."
             data-help-title="Items sold"
             data-help-why="Many items but few orders means small carts - a signal to bundle or upsell.">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-success bg-opacity-10 text-success">S</div>
                    <div>
                        <div class="fs-4 fw-bold" data-live-report="itemsSold">${summary.itemsSold}</div>
                        <div class="small text-muted">Items sold · ${periodLabel}</div>
                    </div>
                    <button type="button" class="admin-help-toggle ms-auto" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card"
             data-help="Orders created in this period, all statuses."
             data-help-title="Orders"
             data-help-why="Volume and trend tell you if the shop is getting busier or quieter.">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-info bg-opacity-10 text-info">O</div>
                    <div>
                        <div class="fs-4 fw-bold" data-live-report="totalOrders">${summary.totalOrders}</div>
                        <div class="small text-muted">Orders · ${periodLabel}</div>
                    </div>
                    <button type="button" class="admin-help-toggle ms-auto" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                </div>
            </div>
        </div>
        <div class="col-md-3 col-sm-6">
            <div class="card stats-card"
             data-help="Average money per order in this period."
             data-help-title="Average order value"
             data-help-why="Raising it - upsells, bundles, free-shipping thresholds - is often easier than finding new customers.">
                <div class="card-body d-flex align-items-center gap-3">
                    <div class="icon bg-secondary bg-opacity-10 text-secondary">A</div>
                    <div>
                        <div class="fs-4 fw-bold money" data-live-report="avgOrderValue">$<fmt:formatNumber value="${summary.avgOrderValue}" pattern="#,##0.00"/></div>
                        <div class="small text-muted">Avg order value · ${periodLabel}</div>
                    </div>
                    <button type="button" class="admin-help-toggle ms-auto" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                </div>
            </div>
        </div>
    </div>

    <div class="row g-3 mb-3">
        <div class="col-lg-8">
            <div class="card card-hover dashboard-chart-card h-100" aria-labelledby="revenueTrendTitle"
             data-help="Daily net sales across the selected period."
             data-help-title="Revenue trend"
             data-help-why="Shows the shape: steady, climbing, or a one-day spike you can trace back to a promotion. Updates itself when orders change.">
                <div class="card-header bg-body d-flex justify-content-between align-items-center">
                    <div>
                        <h5 id="revenueTrendTitle" class="mb-0 fw-semibold">Revenue trend</h5>
                        <span class="small text-muted">Net sales · ${periodLabel}</span>
                    </div>
                    <span class="d-inline-flex align-items-center gap-2">
                        <span class="badge text-bg-primary" data-live-indicator><i class="bi bi-broadcast me-1" aria-hidden="true"></i><span data-live-indicator-label>Live</span></span>
                        <button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                    </span>
                </div>
                <div class="card-body">
                    <div class="chart-wrap">
                        <canvas id="revenueTrendChart" aria-label="Line chart of revenue over the selected period" role="img"></canvas>
                        <div class="chart-empty d-none" id="revenueTrendEmpty">No sales in this period yet.</div>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-lg-4 d-flex flex-column gap-3">
            <div class="card card-hover"
                 data-help="Every order split by its current status."
                 data-help-title="Orders by status"
                 data-help-why="A pile of 'pending' means work waiting; a pile of 'refunded' may mean something is wrong with a product.">
                <div class="card-body">
                    <h6 class="fw-bold mb-3 d-flex align-items-center justify-content-between">Orders by status <span class="small text-muted fw-normal">· ${periodLabel}</span>
                        <button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                    </h6>
                    <table class="table table-sm table-borderless mb-0 small">
                        <tbody>
                        <tr><td>Pending</td><td class="text-end fw-semibold" data-live-funnel="pending">${summary.pending}</td></tr>
                        <tr><td>Processing</td><td class="text-end fw-semibold" data-live-funnel="processing">${summary.processing}</td></tr>
                        <tr><td>Shipped</td><td class="text-end fw-semibold" data-live-funnel="shipped">${summary.shipped}</td></tr>
                        <tr><td>Completed</td><td class="text-end fw-semibold" data-live-funnel="completed">${summary.completed}</td></tr>
                        <tr><td>Cancelled</td><td class="text-end fw-semibold" data-live-funnel="cancelled">${summary.cancelled}</td></tr>
                        <tr><td>Refunded</td><td class="text-end fw-semibold" data-live-funnel="refunded">${summary.refunded}</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
            <div class="card card-hover"
                 data-help="What your whole inventory is worth right now (all time)."
                 data-help-title="Stock values"
                 data-help-why="The low-stock value is money nearly gone from the shelves - restocking it keeps capital working.">
                <div class="card-body">
                    <h6 class="fw-bold mb-3 d-flex align-items-center justify-content-between">Stock values <span class="small text-muted fw-normal">(all time)</span>
                        <button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
                    </h6>
                    <table class="table table-sm table-borderless mb-0 small">
                        <tbody>
                        <tr><td>Total stock value</td><td class="text-end fw-semibold money" data-live-stock="totalStockValue">$<fmt:formatNumber value="${stockValues.totalStockValue}" pattern="#,##0.00"/></td></tr>
                        <tr><td>Low stock value</td><td class="text-end fw-semibold money" data-live-stock="lowStockValue">$<fmt:formatNumber value="${stockValues.lowStockValue}" pattern="#,##0.00"/></td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>

    <div class="card card-hover"
         data-help="This period's best sellers, by units and revenue."
         data-help-title="Top-selling products"
         data-help-why="These are the products to keep deep in stock - losing one of them hurts the most.">
        <div class="card-header bg-body fw-semibold d-flex justify-content-between align-items-center">
            <span>Top-selling products <span class="small text-muted fw-normal">· ${periodLabel}</span></span>
            <button type="button" class="admin-help-toggle" data-help-toggle aria-label="What is this card?"><i class="bi bi-question-lg" aria-hidden="true"></i></button>
        </div>
        <div class="table-responsive">
            <table class="table align-middle mb-0">
                <thead class="table-light">
                <tr><th class="text-muted fw-semibold" style="width:3rem">#</th><th>Product</th><th class="text-center">Units sold</th><th class="text-end">Revenue</th></tr>
                </thead>
                <tbody data-top-sellers>
                <c:forEach var="s" items="${topSellers}" varStatus="st">
                    <tr>
                        <td class="text-muted">${st.index + 1}</td>
                        <td class="small"><c:out value="${s.name}"/></td>
                        <td class="text-center fw-semibold">${s.quantity}</td>
                        <td class="text-end money">$<fmt:formatNumber value="${s.revenue}" pattern="#,##0.00"/></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty topSellers}">
                    <tr><td colspan="4" class="text-center text-muted py-4">No sales in this period yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</div>

<script src="${pageContext.request.contextPath}/assets/vendor/chart.js/chart.umd.min.js"></script>
<script>
    // Initial series data for reports-live.js. Chart creation lives there so
    // a later refresh can also build the chart when data first arrives.
    window.__reportSeries = [<c:forEach var="pt" items="${series}" varStatus="s">{label:'${pt.label}', value:${pt.value}}${s.last ? '' : ','}</c:forEach>];
</script>
<script src="${pageContext.request.contextPath}/assets/js/reports-live.js"></script>
<%@ include file="../layouts/footer.jspf" %>