package market.web;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import market.Config;
import market.auth.Auth;
import market.auth.PasswordUtil;
import market.db.Database;

@WebServlet("/api/*")
public class AuthServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if ("/session".equals(request.getPathInfo())) {
            sendSession(request, response);
        } else if ("/login".equals(request.getPathInfo()) || "/logout".equals(request.getPathInfo())) {
            Json.error(response, 405, "POST 요청을 사용하세요.");
        } else {
            Json.error(response, 404, "API를 찾을 수 없습니다.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            if ("/login".equals(request.getPathInfo())) {
                login(request, response);
            } else if ("/logout".equals(request.getPathInfo())) {
                Json.read(request);
                request.getSession(false).invalidate();
                Json.send(response, 200, Map.of("message", "로그아웃했습니다."));
            } else if ("/session".equals(request.getPathInfo())) {
                Json.error(response, 405, "GET 요청을 사용하세요.");
            } else {
                Json.error(response, 404, "API를 찾을 수 없습니다.");
            }
        } catch (SQLException | GeneralSecurityException e) {
            throw new ServletException("로그인 처리 실패", e);
        }
    }

    private void login(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException, GeneralSecurityException {
        JsonObject body = Json.read(request);
        String loginId = Json.requiredString(body, "loginId").trim();
        String password = Json.requiredString(body, "password");
        if (loginId.isEmpty() || loginId.codePointCount(0, loginId.length()) > 30) {
            throw new IllegalArgumentException("loginId는 1~30글자여야 합니다.");
        }
        Auth.User user = authenticate(loginId, password);
        if (user == null) {
            Json.error(response, 401, "ID 또는 비밀번호가 일치하지 않습니다.");
            return;
        }
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        request.getSession(true).setAttribute("loginUser", user);
        sendSession(request, response);
    }

    private Auth.User authenticate(String loginId, String password)
            throws IOException, SQLException, GeneralSecurityException {
        String adminLogin = Config.load().getProperty("app.adminLogin");
        if (adminLogin == null || adminLogin.isBlank()) {
            throw new SQLException("app.adminLogin 설정이 필요합니다.");
        }
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT MemberID, LoginID, PasswordHash FROM MEMBER WHERE LoginID = ?")) {
            statement.setString(1, loginId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !PasswordUtil.verify(password, rows.getString("PasswordHash"))) {
                    return null;
                }
                String storedLoginId = rows.getString("LoginID");
                return new Auth.User(rows.getLong("MemberID"), storedLoginId,
                        storedLoginId.equals(adminLogin));
            }
        }
    }

    private void sendSession(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loginUser", Auth.user(request));
        body.put("csrfToken", Auth.csrfToken(request.getSession(true)));
        Json.send(response, 200, body);
    }
}
