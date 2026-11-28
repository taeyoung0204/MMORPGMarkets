package market.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import market.auth.Auth;
import market.auth.PasswordUtil;
import market.db.Database;

@WebServlet({"/api/me", "/api/me/nickname", "/api/me/password", "/api/admin"})
public class MemberProfileServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            switch (request.getServletPath()) {
                case "/api/me" -> profile(response, Auth.user(request).memberId());
                case "/api/admin" -> adminMembers(request, response);
                default -> {
                    response.setHeader("Allow", "POST");
                    Json.error(response, 405, "POST 요청을 사용하세요.");
                }
            }
        } catch (SQLException e) {
            throw new ServletException("회원 정보 조회 실패", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String path = request.getServletPath();
        if (path.equals("/api/me") || path.equals("/api/admin")) {
            response.setHeader("Allow", "GET");
            Json.error(response, 405, "GET 요청을 사용하세요.");
            return;
        }
        JsonObject body = Json.read(request);
        long memberId = Auth.user(request).memberId();
        try {
            if (path.equals("/api/me/nickname")) {
                changeNickname(body, response, memberId);
            } else {
                changePassword(body, response, memberId);
            }
        } catch (SQLException | GeneralSecurityException e) {
            throw new ServletException("회원 정보 변경 실패", e);
        }
    }

    private void profile(HttpServletResponse response, long memberId) throws SQLException, IOException {
        String sql = """
                SELECT MemberID, LoginID, Nickname, GoldBalance,
                       TO_CHAR(JoinedAt, 'YYYY-MM-DD HH24:MI:SS') AS JoinedAt
                FROM MEMBER WHERE MemberID = ?
                """;
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memberId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    Json.error(response, 404, "회원을 찾을 수 없습니다.");
                    return;
                }
                Json.send(response, 200, Map.of("profile", Map.of(
                        "memberId", result.getLong("MemberID"),
                        "loginId", result.getString("LoginID"),
                        "nickname", result.getString("Nickname"),
                        "goldBalance", result.getBigDecimal("GoldBalance"),
                        "joinedAt", result.getString("JoinedAt"))));
            }
        }
    }

    private void adminMembers(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        String value = request.getParameter("minimumGold");
        BigDecimal minimumGold = null;
        String sql = """
                SELECT M.MemberID, M.Nickname, M.GoldBalance FROM MEMBER M
                WHERE M.GoldBalance > (SELECT AVG(M2.GoldBalance) FROM MEMBER M2)
                """;
        if (value != null && !value.isBlank()) {
            value = value.strip();
            if (!value.matches("[0-9]{1,38}")) {
                throw new IllegalArgumentException("최소 잔액은 0 이상의 정수여야 합니다.");
            }
            minimumGold = new BigDecimal(value);
            sql += " AND M.GoldBalance >= ?";
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (minimumGold != null) {
                statement.setBigDecimal(1, minimumGold);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(Map.of("memberId", result.getLong("MemberID"),
                            "nickname", result.getString("Nickname"),
                            "goldBalance", result.getBigDecimal("GoldBalance")));
                }
            }
        }
        Json.send(response, 200, Map.of("rows", rows,
                "filters", Collections.singletonMap("minimumGold", minimumGold)));
    }

    private void changeNickname(JsonObject body, HttpServletResponse response, long memberId)
            throws SQLException, IOException {
        String nickname = Json.requiredString(body, "nickname").strip();
        if (nickname.isEmpty() || nickname.codePointCount(0, nickname.length()) > 30) {
            throw new IllegalArgumentException("닉네임은 1~30글자여야 합니다.");
        }
        int changed;
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE MEMBER SET Nickname = ? WHERE MemberID = ?")) {
            statement.setString(1, nickname);
            statement.setLong(2, memberId);
            changed = statement.executeUpdate();
        }
        if (changed == 0) {
            Json.error(response, 404, "회원을 찾을 수 없습니다.");
            return;
        }
        Json.send(response, 200, Map.of("message", "닉네임을 변경했습니다."));
    }

    private void changePassword(JsonObject body, HttpServletResponse response, long memberId)
            throws SQLException, IOException, GeneralSecurityException {
        String oldPassword = Json.requiredString(body, "oldPassword");
        String newPassword = Json.requiredString(body, "newPassword");
        if (!newPassword.equals(Json.requiredString(body, "confirmPassword"))) {
            throw new IllegalArgumentException("새 비밀번호와 확인값이 일치하지 않습니다.");
        }
        int changed;
        try (Connection connection = Database.open()) {
            connection.setAutoCommit(false);
            try {
                String currentHash = passwordHash(connection, memberId);
                if (!PasswordUtil.verify(oldPassword, currentHash)) {
                    throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE MEMBER SET PasswordHash = ? WHERE MemberID = ? AND PasswordHash = ?")) {
                    statement.setString(1, PasswordUtil.hash(newPassword));
                    statement.setLong(2, memberId);
                    statement.setString(3, currentHash);
                    changed = statement.executeUpdate();
                }
                if (changed == 1) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
            } catch (SQLException | GeneralSecurityException | IllegalArgumentException e) {
                connection.rollback();
                throw e;
            }
        }
        if (changed == 0) {
            Json.error(response, 409, "비밀번호가 이미 변경되었습니다. 현재 비밀번호를 다시 확인하세요.");
            return;
        }
        Json.send(response, 200, Map.of("message", "비밀번호를 변경했습니다."));
    }

    private String passwordHash(Connection connection, long memberId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT PasswordHash FROM MEMBER WHERE MemberID = ?")) {
            statement.setLong(1, memberId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("PasswordHash") : null;
            }
        }
    }
}
