package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.domain.entity.Order;
import com.hengtongan.computerstore.core.domain.entity.Product;
import com.hengtongan.computerstore.core.repository.InventoryRepository;
import com.hengtongan.computerstore.core.repository.OrderRepository;
import com.hengtongan.computerstore.core.repository.ProductRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reporting data for management: period-scoped sales analytics (revenue,
 * items sold, order funnel, revenue trend, top sellers) plus all-time stock
 * valuation. Every sales figure excludes cancelled/refunded orders so the
 * numbers always agree with the dashboard.
 */
public class ReportService {

    private final OrderRepository orderDAO = new OrderRepository();
    private final ProductRepository productDAO = new ProductRepository();
    private final InventoryRepository inventoryDAO = new InventoryRepository();

    /**
     * Period-scoped sales summary. {@code days} is the calendar window ending
     * today (1 = today, 7 = last 7 days, 30 = last 30 days); 0 or negative
     * means all time. Cancelled and refunded orders never count.
     */
    public Map<String, Object> getSalesSummary(int days) {
        Timestamp since = windowStart(days);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalOrders", orderDAO.countSince(since));
        summary.put("totalRevenue", orderDAO.totalRevenueSince(since));
        summary.put("itemsSold", orderDAO.totalItemsSoldSince(since));
        summary.put("pending", orderDAO.countByStatusSince(Order.Status.PENDING, since));
        summary.put("processing", orderDAO.countByStatusSince(Order.Status.PROCESSING, since));
        summary.put("shipped", orderDAO.countByStatusSince(Order.Status.SHIPPED, since));
        summary.put("completed", orderDAO.countByStatusSince(Order.Status.COMPLETED, since));
        summary.put("cancelled", orderDAO.countByStatusSince(Order.Status.CANCELLED, since));
        summary.put("refunded", orderDAO.countByStatusSince(Order.Status.REFUNDED, since));
        long orders = ((Number) summary.get("totalOrders")).longValue();
        BigDecimal revenue = (BigDecimal) summary.get("totalRevenue");
        summary.put("avgOrderValue", orders == 0
                ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP));
        return summary;
    }

    /**
     * Revenue by day (finite windows) or by month (all time), with zero-filled
     * gaps so the trend chart never jumps. Each point is {label, value}.
     */
    public List<Map<String, Object>> getRevenueSeries(int days) {
        List<Map<String, Object>> points = new ArrayList<>();
        if (days > 0) {
            LocalDate start = LocalDate.now().minusDays(days - 1L);
            Map<LocalDate, BigDecimal> byDay = new HashMap<>();
            for (Object[] row : orderDAO.revenueByDay(Timestamp.valueOf(start.atStartOfDay()))) {
                byDay.put(((Date) row[0]).toLocalDate(), (BigDecimal) row[1]);
            }
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");
            LocalDate today = LocalDate.now();
            for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
                points.add(point(day.format(fmt), byDay.getOrDefault(day, BigDecimal.ZERO)));
            }
        } else {
            Map<YearMonth, BigDecimal> byMonth = new LinkedHashMap<>();
            for (Object[] row : orderDAO.revenueByMonth()) {
                byMonth.put(YearMonth.parse((String) row[0]), (BigDecimal) row[1]);
            }
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");
            YearMonth now = YearMonth.now();
            YearMonth first = byMonth.isEmpty() ? now : byMonth.keySet().iterator().next();
            for (YearMonth month = first; !month.isAfter(now); month = month.plusMonths(1)) {
                points.add(point(month.format(fmt), byMonth.getOrDefault(month, BigDecimal.ZERO)));
            }
        }
        return points;
    }

    /** Top products by units sold in the window; each item is {name, quantity, revenue}. */
    public List<Map<String, Object>> getTopSellers(int days) {
        Timestamp since = windowStart(days);
        List<Map<String, Object>> sellers = new ArrayList<>();
        for (Object[] row : orderDAO.topSelling(5, since)) {
            Map<String, Object> seller = new LinkedHashMap<>();
            seller.put("name", row[0]);
            seller.put("quantity", row[1]);
            seller.put("revenue", row[2]);
            sellers.add(seller);
        }
        return sellers;
    }

    public List<Product> getLowStock() {
        return inventoryDAO.listLowStock(Product.LOW_STOCK_THRESHOLD);
    }

    public List<Product> getOutOfStock() {
        return inventoryDAO.listOutOfStock();
    }

    public Map<String, BigDecimal> getLowStockValues() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("totalStockValue", productDAO.totalStockValue());
        values.put("lowStockValue", productDAO.lowStockValue(Product.LOW_STOCK_THRESHOLD));
        return values;
    }

    /** Start of the calendar window for {@code days} (null = all time). */
    private static Timestamp windowStart(int days) {
        if (days <= 0) {
            return null;
        }
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        return Timestamp.valueOf(start.atStartOfDay());
    }

    private static Map<String, Object> point(String label, BigDecimal value) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("label", label);
        point.put("value", value);
        return point;
    }
}