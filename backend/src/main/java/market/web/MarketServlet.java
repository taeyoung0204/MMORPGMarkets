package market.web;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
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

@WebServlet({"/api/market", "/api/market/item", "/api/market/prices"})
public class MarketServlet extends HttpServlet {
    private static final Set<String> CATEGORIES = Set.of(
            "강화 재료", "각인서", "전투 용품", "생활", "모험의 서");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_INPUT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm").withResolverStyle(ResolverStyle.STRICT);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            switch (request.getServletPath()) {
                case "/api/market" -> search(request, response);
                case "/api/market/item" -> listings(request, response);
                case "/api/market/prices" -> prices(request, response);
            }
        } catch (SQLException e) {
            throw new ServletException("거래소 조회 실패", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Allow", "GET");
        Json.error(response, 405, "GET 요청을 사용하세요.");
    }

    private void search(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        String category = request.getParameter("category");
        if (category == null || category.isBlank()) {
            category = "강화 재료";
        }
        if (!CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("거래소 분류를 확인하세요.");
        }
        String name = request.getParameter("name");
        name = name == null ? "" : name.strip();
        String sql = "SELECT ItemID, ItemName, Category, BundleSize FROM ITEM WHERE Category = ?";
        if (!name.isEmpty()) {
            sql += " AND INSTR(ItemName, ?) > 0";
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category);
            if (!name.isEmpty()) {
                statement.setString(2, name);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(Map.of("itemId", result.getLong("ItemID"),
                            "itemName", result.getString("ItemName"),
                            "category", result.getString("Category"),
                            "bundleSize", result.getInt("BundleSize")));
                }
            }
        }
        Json.send(response, 200, Map.of("rows", rows, "filters", Map.of("category", category, "name", name)));
    }

    private void listings(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        Long itemId = itemId(request);
        if (itemId == null) {
            throw new IllegalArgumentException("itemId가 필요합니다.");
        }
        long memberId = (Long) request.getAttribute("targetMemberId");
        String asOf = LocalDateTime.now(KST).format(DATE_TIME);
        Map<String, Object> item;
        List<Map<String, Object>> rows;
        try (Connection connection = Database.open()) {
            item = findItem(connection, itemId);
            if (item == null) {
                Json.error(response, 404, "품목을 찾을 수 없습니다.");
                return;
            }
            rows = findListings(connection, itemId, memberId, asOf);
        }
        Json.send(response, 200, Map.of("item", item, "rows", rows,
                "filters", Map.of("itemId", itemId, "memberId", memberId, "asOf", asOf)));
    }

    private List<Map<String, Object>> findListings(Connection connection, long itemId, long memberId, String asOf)
            throws SQLException {
        String sql = """
                SELECT L.ListingID, M.Nickname AS SellerNickname, L.BundlePrice,
                       L.ListedBundles - NVL(F.FilledBundles, 0) AS RemainingBundles,
                       TO_CHAR(L.ListedAt, 'YYYY-MM-DD HH24:MI:SS') AS ListedAt
                FROM LISTING L JOIN MEMBER M ON M.MemberID = L.SellerID
                LEFT JOIN (
                    SELECT ListingID, SUM(FilledBundles) AS FilledBundles
                    FROM EXECUTION_DETAIL GROUP BY ListingID
                ) F ON F.ListingID = L.ListingID
                WHERE L.ItemID = ? AND L.SellerID <> ? AND L.ListingStatus = '판매중'
                  AND L.ListedAt <= TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')
                  AND L.ListedAt + 1 > TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')
                  AND L.ListedBundles > NVL(F.FilledBundles, 0)
                ORDER BY L.BundlePrice, L.ListedAt
                """;
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, itemId);
            statement.setLong(2, memberId);
            statement.setString(3, asOf);
            statement.setString(4, asOf);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(Map.of("listingId", result.getLong("ListingID"),
                            "sellerNickname", result.getString("SellerNickname"),
                            "bundlePrice", result.getLong("BundlePrice"),
                            "remainingBundles", result.getLong("RemainingBundles"),
                            "listedAt", result.getString("ListedAt")));
                }
            }
        }
        return rows;
    }

    private void prices(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        Long itemId = itemId(request);
        LocalDateTime from = parseDate(request.getParameter("from"), "from");
        LocalDateTime until = parseDate(request.getParameter("until"), "until");
        if (from != null && until != null && !from.isBefore(until)) {
            throw new IllegalArgumentException("시작시각은 종료시각보다 빨라야 합니다.");
        }
        String sql = """
                SELECT I.ItemID, I.ItemName, TO_CHAR(E.ExecutedAt, 'YYYY-MM-DD') AS TradeDate,
                       FLOOR(SUM(E.FilledBundles * E.BundlePrice) / SUM(E.FilledBundles)) AS AveragePrice,
                       SUM(E.FilledBundles) AS SoldBundles
                FROM ITEM I JOIN PURCHASE_ORDER O ON O.ItemID = I.ItemID
                JOIN EXECUTION_DETAIL E ON E.OrderID = O.OrderID
                WHERE 1 = 1
                """;
        if (itemId != null) {
            sql += " AND I.ItemID = ?";
        }
        if (from != null) {
            sql += " AND E.ExecutedAt >= TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')";
        }
        if (until != null) {
            sql += " AND E.ExecutedAt < TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')";
        }
        sql += " GROUP BY I.ItemID, I.ItemName, TO_CHAR(E.ExecutedAt, 'YYYY-MM-DD')"
                + " ORDER BY TradeDate DESC, I.ItemID";

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = Database.open()) {
            if (itemId != null && findItem(connection, itemId) == null) {
                Json.error(response, 404, "품목을 찾을 수 없습니다.");
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                if (itemId != null) {
                    statement.setLong(index++, itemId);
                }
                if (from != null) {
                    statement.setString(index++, DATE_TIME.format(from));
                }
                if (until != null) {
                    statement.setString(index, DATE_TIME.format(until));
                }
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        rows.add(Map.of("itemId", result.getLong("ItemID"),
                                "itemName", result.getString("ItemName"),
                                "tradeDate", result.getString("TradeDate"),
                                "averagePrice", result.getLong("AveragePrice"),
                                "soldBundles", result.getLong("SoldBundles")));
                    }
                }
            }
        }
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("itemId", itemId);
        filters.put("from", from == null ? null : DATE_INPUT.format(from));
        filters.put("until", until == null ? null : DATE_INPUT.format(until));
        Json.send(response, 200, Map.of("rows", rows, "filters", filters));
    }

    private Map<String, Object> findItem(Connection connection, long itemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT ItemID, ItemName, BundleSize FROM ITEM WHERE ItemID = ?")) {
            statement.setLong(1, itemId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Map.of("itemId", result.getLong("ItemID"),
                        "itemName", result.getString("ItemName"),
                        "bundleSize", result.getInt("BundleSize")) : null;
            }
        }
    }

    private Long itemId(HttpServletRequest request) {
        String value = request.getParameter("itemId");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("itemId는 양의 정수여야 합니다.");
        }
        throw new IllegalArgumentException("itemId는 양의 정수여야 합니다.");
    }

    private LocalDateTime parseDate(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String message = name + "은 yyyy-MM-ddTHH:mm 형식이어야 합니다.";
        if (value.length() != 16) {
            throw new IllegalArgumentException(message);
        }
        try {
            LocalDateTime date = LocalDateTime.parse(value, DATE_INPUT);
            if (date.getYear() > 0) {
                return date;
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(message);
        }
        throw new IllegalArgumentException(message);
    }
}
