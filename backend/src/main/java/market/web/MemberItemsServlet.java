package market.web;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import market.auth.Auth;
import market.db.Database;

@WebServlet({"/api/me/inventory", "/api/favorites", "/api/favorites/add", "/api/favorites/remove"})
public class MemberItemsServlet extends HttpServlet {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String path = request.getServletPath();
        if (path.equals("/api/favorites/add") || path.equals("/api/favorites/remove")) {
            response.setHeader("Allow", "POST");
            Json.error(response, 405, "POST 요청을 사용하세요.");
            return;
        }
        long memberId = (Long) request.getAttribute("targetMemberId");
        try {
            if (path.equals("/api/me/inventory")) {
                inventory(request, response, memberId);
            } else {
                favorites(request, response, memberId);
            }
        } catch (SQLException e) {
            throw new ServletException("보유·관심 목록 조회 실패", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String path = request.getServletPath();
        boolean add = path.equals("/api/favorites/add");
        if (!add && !path.equals("/api/favorites/remove")) {
            response.setHeader("Allow", "GET");
            Json.error(response, 405, "GET 요청을 사용하세요.");
            return;
        }
        long itemId = itemId(Json.read(request).get("itemId"));
        long memberId = Auth.user(request).memberId();
        try {
            int updated = changeFavorite(memberId, itemId, add);
            if (add && updated == 0) {
                Json.error(response, 404, "품목을 찾을 수 없습니다.");
                return;
            }
            String message = add ? "관심 품목으로 등록했습니다."
                    : updated == 0 ? "이미 관심 목록에 없는 품목입니다." : "관심 목록에서 삭제했습니다.";
            Json.send(response, 200, Map.of("message", message));
        } catch (SQLException e) {
            if (add && e.getErrorCode() == 1) {
                Json.error(response, 409, "이미 관심 품목으로 등록되어 있습니다.");
                return;
            }
            throw new ServletException("관심 목록 변경 실패", e);
        }
    }

    private void inventory(HttpServletRequest request, HttpServletResponse response, long memberId)
            throws SQLException, IOException {
        String view = view(request, "notFavorited");
        boolean notFavorited = view.equals("notFavorited");
        String sql = "SELECT I.ItemID, I.ItemName" + (notFavorited ? "" : ", H.OwnedCount") + """
                 FROM HOLDING H JOIN ITEM I ON I.ItemID = H.ItemID
                 WHERE H.MemberID = ? AND H.OwnedCount > 0
                """;
        if (notFavorited) {
            sql += """
                    MINUS
                    SELECT I.ItemID, I.ItemName
                    FROM FAVORITE F JOIN ITEM I ON I.ItemID = F.ItemID
                    WHERE F.MemberID = ?
                    """;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memberId);
            if (notFavorited) {
                statement.setLong(2, memberId);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long itemId = result.getLong("ItemID");
                    String name = result.getString("ItemName");
                    rows.add(notFavorited ? Map.of("itemId", itemId, "itemName", name)
                            : Map.of("itemId", itemId, "itemName", name,
                                    "ownedCount", result.getLong("OwnedCount")));
                }
            }
        }
        Json.send(response, 200, Map.of("rows", rows, "filters", Map.of("view", view, "memberId", memberId)));
    }

    private void favorites(HttpServletRequest request, HttpServletResponse response, long memberId)
            throws SQLException, IOException {
        String view = view(request, "available");
        String asOf = view.equals("available") ? LocalDateTime.now(KST).format(DATE_TIME) : null;
        List<Map<String, Object>> rows;
        try (Connection connection = Database.open()) {
            rows = findFavorites(connection, memberId, asOf);
        }
        Map<String, Object> filters = asOf == null ? Map.of("view", view, "memberId", memberId)
                : Map.of("view", view, "memberId", memberId, "asOf", asOf);
        Json.send(response, 200, Map.of("rows", rows, "filters", filters));
    }

    private List<Map<String, Object>> findFavorites(Connection connection, long memberId, String asOf)
            throws SQLException {
        String sql = """
                SELECT I.ItemID, I.ItemName, I.Category,
                       TO_CHAR(F.AddedAt, 'YYYY-MM-DD HH24:MI:SS') AS AddedAt
                FROM FAVORITE F JOIN ITEM I ON I.ItemID = F.ItemID
                WHERE F.MemberID = ?
                """;
        if (asOf != null) {
            sql += """
                      AND EXISTS (
                          SELECT 1 FROM LISTING L
                          WHERE L.ItemID = F.ItemID AND L.SellerID <> F.MemberID
                            AND L.ListingStatus = '판매중'
                            AND L.ListedAt <= TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')
                            AND L.ListedAt + 365 > TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')
                            AND L.ListedBundles > NVL((
                                SELECT SUM(E.FilledBundles) FROM EXECUTION_DETAIL E
                                WHERE E.ListingID = L.ListingID
                            ), 0)
                      )
                    """;
        }
        sql += " ORDER BY F.AddedAt DESC, F.ItemID";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memberId);
            if (asOf != null) {
                statement.setString(2, asOf);
                statement.setString(3, asOf);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(Map.of("itemId", result.getLong("ItemID"),
                            "itemName", result.getString("ItemName"),
                            "category", result.getString("Category"),
                            "addedAt", result.getString("AddedAt")));
                }
            }
        }
        return rows;
    }

    private int changeFavorite(long memberId, long itemId, boolean add) throws SQLException {
        String sql = add ? """
                INSERT INTO FAVORITE (MemberID, ItemID, AddedAt)
                SELECT ?, ItemID, CAST(SYSTIMESTAMP AT TIME ZONE 'Asia/Seoul' AS DATE)
                FROM ITEM WHERE ItemID = ?
                """ : "DELETE FROM FAVORITE WHERE MemberID = ? AND ItemID = ?";
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memberId);
            statement.setLong(2, itemId);
            return statement.executeUpdate();
        }
    }

    private String view(HttpServletRequest request, String alternate) {
        String value = request.getParameter("view");
        if (value == null || value.isBlank()) {
            return "all";
        }
        if (value.equals("all") || value.equals(alternate)) {
            return value;
        }
        throw new IllegalArgumentException("view는 all 또는 " + alternate + "이어야 합니다.");
    }

    private long itemId(JsonElement value) {
        String message = "itemId는 양의 정수여야 합니다.";
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(message);
        }
        try {
            long id = Long.parseLong(value.getAsString());
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
        throw new IllegalArgumentException(message);
    }
}
