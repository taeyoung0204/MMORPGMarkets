package market.web;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import market.db.Database;

@WebServlet({"/api/me/sales", "/api/me/orders"})
public class SalesOrdersServlet extends HttpServlet {
    private static final List<String> DEFAULT_STATUSES = List.of("부분체결", "미체결");
    private static final Set<String> ORDER_STATUSES = Set.of("전량체결", "부분체결", "미체결");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        Long memberId = (Long) request.getAttribute("targetMemberId");
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("memberId", memberId);
        List<Map<String, Object>> rows;
        try {
            if ("/api/me/sales".equals(request.getServletPath())) {
                rows = sales(memberId);
            } else {
                String view = request.getParameter("view");
                if (view == null || view.isBlank()) {
                    view = "status";
                }
                filters.put("view", view);
                switch (view) {
                    case "status" -> {
                        List<String> statuses = statuses(request);
                        filters.put("status", statuses);
                        rows = orderStatus(memberId, statuses);
                    }
                    case "summary" -> {
                        Long orderId = orderId(request);
                        filters.put("orderId", orderId);
                        rows = orderSummary(memberId, orderId);
                    }
                    default -> throw new IllegalArgumentException("view는 status 또는 summary여야 합니다.");
                }
            }
        } catch (SQLException e) {
            throw new ServletException("판매·주문 조회 실패", e);
        }
        Json.send(response, 200, Map.of("rows", rows, "filters", filters));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Allow", "GET");
        Json.error(response, 405, "GET 요청을 사용하세요.");
    }

    private List<Map<String, Object>> sales(Long memberId) throws SQLException {
        String sql = """
                SELECT M.MemberID, M.Nickname,
                       SUM(E.FilledBundles) AS SoldBundles,
                       SUM(E.FilledBundles * E.BundlePrice) AS GrossSales,
                       SUM(FLOOR(E.FilledBundles * E.BundlePrice * 0.05)) AS TotalFee,
                       SUM(E.FilledBundles * E.BundlePrice
                           - FLOOR(E.FilledBundles * E.BundlePrice * 0.05)) AS NetSales
                FROM MEMBER M JOIN LISTING L ON L.SellerID = M.MemberID
                JOIN EXECUTION_DETAIL E ON E.ListingID = L.ListingID
                """;
        if (memberId != null) {
            sql += " WHERE M.MemberID = ?";
        }
        sql += " GROUP BY M.MemberID, M.Nickname";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (memberId != null) {
                statement.setLong(1, memberId);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(Map.of("memberId", result.getLong("MemberID"),
                            "nickname", result.getString("Nickname"),
                            "soldBundles", result.getLong("SoldBundles"),
                            "grossSales", result.getLong("GrossSales"),
                            "totalFee", result.getLong("TotalFee"),
                            "netSales", result.getLong("NetSales")));
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> orderStatus(Long memberId, List<String> statuses) throws SQLException {
        String sql = """
                SELECT O.OrderID, O.BuyerID, O.ItemID, O.RequestedBundles,
                       O.ConfirmedAvgPrice, O.OrderStatus
                FROM PURCHASE_ORDER O
                WHERE O.OrderStatus IN (
                """ + String.join(", ", Collections.nCopies(statuses.size(), "?")) + ")";
        if (memberId != null) {
            sql += " AND O.BuyerID = ?";
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (String status : statuses) {
                statement.setString(index++, status);
            }
            if (memberId != null) {
                statement.setLong(index, memberId);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(Map.of("orderId", result.getLong("OrderID"),
                            "buyerId", result.getLong("BuyerID"),
                            "itemId", result.getLong("ItemID"),
                            "requestedBundles", result.getLong("RequestedBundles"),
                            "confirmedAvgPrice", result.getLong("ConfirmedAvgPrice"),
                            "orderStatus", result.getString("OrderStatus")));
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> orderSummary(Long memberId, Long orderId) throws SQLException {
        String sql = """
                SELECT T.OrderID, M.Nickname AS BuyerNickname, I.ItemName,
                       T.OrderStatus, T.ConfirmedAvgPrice, T.RequestedBundles, T.FilledBundles,
                       T.RequestedBundles - T.FilledBundles AS UnfilledBundles,
                       T.ConfirmedAvgPrice * T.RequestedBundles AS MaxPayment,
                       T.ActualPayment,
                       T.ConfirmedAvgPrice * T.RequestedBundles - T.ActualPayment AS Refund
                FROM (
                    SELECT O.OrderID, O.BuyerID, O.ItemID, O.OrderStatus,
                           O.RequestedBundles, O.ConfirmedAvgPrice,
                           NVL(SUM(E.FilledBundles), 0) AS FilledBundles,
                           NVL(SUM(E.FilledBundles * E.BundlePrice), 0) AS ActualPayment
                    FROM PURCHASE_ORDER O LEFT JOIN EXECUTION_DETAIL E ON O.OrderID = E.OrderID
                    GROUP BY O.OrderID, O.BuyerID, O.ItemID, O.OrderStatus,
                             O.RequestedBundles, O.ConfirmedAvgPrice
                ) T, MEMBER M, ITEM I
                WHERE T.BuyerID = M.MemberID AND T.ItemID = I.ItemID
                """;
        if (memberId != null) {
            sql += " AND T.BuyerID = ?";
        }
        if (orderId != null) {
            sql += " AND T.OrderID = ?";
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (memberId != null) {
                statement.setLong(index++, memberId);
            }
            if (orderId != null) {
                statement.setLong(index, orderId);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(Map.ofEntries(
                            Map.entry("orderId", result.getLong("OrderID")),
                            Map.entry("buyerNickname", result.getString("BuyerNickname")),
                            Map.entry("itemName", result.getString("ItemName")),
                            Map.entry("orderStatus", result.getString("OrderStatus")),
                            Map.entry("confirmedAvgPrice", result.getLong("ConfirmedAvgPrice")),
                            Map.entry("requestedBundles", result.getLong("RequestedBundles")),
                            Map.entry("filledBundles", result.getLong("FilledBundles")),
                            Map.entry("unfilledBundles", result.getLong("UnfilledBundles")),
                            Map.entry("maxPayment", result.getLong("MaxPayment")),
                            Map.entry("actualPayment", result.getLong("ActualPayment")),
                            Map.entry("refund", result.getLong("Refund"))));
                }
            }
        }
        return rows;
    }

    private List<String> statuses(HttpServletRequest request) {
        String[] values = request.getParameterValues("status");
        if (values == null) {
            return DEFAULT_STATUSES;
        }
        List<String> statuses = Arrays.stream(values).distinct().toList();
        if (!ORDER_STATUSES.containsAll(statuses)) {
            throw new IllegalArgumentException("주문 상태를 확인하세요.");
        }
        return statuses;
    }

    private Long orderId(HttpServletRequest request) {
        String value = request.getParameter("orderId");
        if (value == null || value.isBlank()) {
            return null;
        }
        String message = "orderId는 양의 정수여야 합니다.";
        try {
            long id = Long.parseLong(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
        throw new IllegalArgumentException(message);
    }
}
