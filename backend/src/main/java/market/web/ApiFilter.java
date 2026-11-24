package market.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import market.auth.Auth;

@WebFilter("/api/*")
public class ApiFilter implements Filter {
    private static final Map<String, Boolean> MEMBER_QUERIES = Map.of(
            "/api/me/inventory", false, "/api/favorites", false, "/api/market/item", true,
            "/api/me/sales", true, "/api/me/orders", true);

    @Override
    public void doFilter(ServletRequest incoming, ServletResponse outgoing, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) incoming;
        HttpServletResponse response = (HttpServletResponse) outgoing;
        request.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        String path = request.getServletPath();
        if (request.getPathInfo() != null) {
            path += request.getPathInfo();
        }
        try {
            Auth.User user = Auth.user(request);
            boolean publicPath = path.equals("/api/session") || path.equals("/api/login");
            if (!publicPath && user == null) {
                Json.error(response, 401, "로그인이 필요합니다.");
                return;
            }
            if ((path.equals("/api/admin") || path.startsWith("/api/admin/")) && !user.admin()) {
                Json.error(response, 403, "관리자 권한이 필요합니다.");
                return;
            }
            if (!request.getMethod().equals("GET") && !request.getMethod().equals("POST")) {
                Json.error(response, 405, "GET 또는 POST 요청을 사용하세요.");
                return;
            }
            if (request.getMethod().equals("POST")) {
                HttpSession session = request.getSession(false);
                String expected = session == null ? null : (String) session.getAttribute("csrfToken");
                String supplied = request.getHeader("X-CSRF-Token");
                if (expected == null || supplied == null || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                    Json.error(response, 403, "CSRF 토큰을 확인하세요.");
                    return;
                }
            }
            Boolean allowAll = MEMBER_QUERIES.get(path);
            if (request.getMethod().equals("GET") && allowAll != null) {
                request.setAttribute("targetMemberId", Auth.targetMember(request, allowAll));
            }
            chain.doFilter(request, response);
        } catch (IllegalArgumentException e) {
            Json.error(response, 400, e.getMessage());
        } catch (IOException | ServletException | RuntimeException e) {
            request.getServletContext().log("API 처리 실패", e);
            if (!response.isCommitted()) {
                Json.error(response, 500, "요청 처리 중 오류가 발생했습니다.");
            }
        }
    }
}
