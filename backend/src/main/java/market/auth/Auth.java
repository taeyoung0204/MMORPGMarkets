package market.auth;

import java.io.Serializable;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class Auth {
    public record User(long memberId, String loginId, boolean admin) implements Serializable {
    }

    private Auth() {
    }

    public static User user(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute("loginUser");
    }

    public static String csrfToken(HttpSession session) {
        String token = (String) session.getAttribute("csrfToken");
        if (token == null) {
            token = UUID.randomUUID().toString();
            session.setAttribute("csrfToken", token);
        }
        return token;
    }

    public static Long targetMember(HttpServletRequest request, boolean allowAll) {
        User user = user(request);
        if (!user.admin()) {
            return user.memberId();
        }
        String value = request.getParameter("memberId");
        if (value == null || value.isBlank()) {
            if (allowAll) {
                return null;
            }
            throw new IllegalArgumentException("조회할 memberId가 필요합니다.");
        }
        try {
            long memberId = Long.parseLong(value);
            if (memberId <= 0) {
                throw new IllegalArgumentException("memberId는 양의 정수여야 합니다.");
            }
            return memberId;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("memberId는 양의 정수여야 합니다.");
        }
    }
}
