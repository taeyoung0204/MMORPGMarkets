package market.web;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import market.auth.Auth;
import market.purchase.PurchaseService;

@WebServlet({"/api/purchase/quote", "/api/purchase"})
public class PurchaseServlet extends HttpServlet {
    private static final String QUOTE = "purchaseQuote";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        if (!request.getServletPath().equals("/api/purchase/quote")) {
            response.setHeader("Allow", "POST");
            Json.error(response, 405, "POST 요청을 사용하세요.");
            return;
        }
        long buyerId = Auth.user(request).memberId();
        long itemId = positiveLong(request.getParameter("itemId"), "itemId");
        long requestedBundles = positiveLong(request.getParameter("requestedBundles"), "requestedBundles");
        HttpSession session = request.getSession(false);
        try {
            PurchaseService.Estimate estimate = PurchaseService.estimate(buyerId, itemId, requestedBundles);
            session.setAttribute(QUOTE, estimate.quote());
            Json.send(response, 200, Map.of(
                    "quoteId", estimate.quote().quoteId(),
                    "itemId", itemId,
                    "requestedBundles", requestedBundles,
                    "availableBundles", estimate.availableBundles(),
                    "suggestedAvgPrice", estimate.suggestedAvgPrice(),
                    "quotedPayment", estimate.quotedPayment(),
                    "goldBalance", estimate.goldBalance()));
        } catch (PurchaseService.Failure e) {
            Json.error(response, e.status(), e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("구매 견적 조회 실패", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        if (!request.getServletPath().equals("/api/purchase")) {
            response.setHeader("Allow", "GET");
            Json.error(response, 405, "GET 요청을 사용하세요.");
            return;
        }
        JsonObject body = Json.read(request);
        String quoteId = uuid(body, "quoteId");
        String requestKey = uuid(body, "requestKey");
        JsonElement amount = body.get("confirmedAvgPrice");
        String value = amount != null && amount.isJsonPrimitive() && amount.getAsJsonPrimitive().isNumber()
                ? amount.getAsString() : null;
        long avgPrice = positiveLong(value, "confirmedAvgPrice");
        long buyerId = Auth.user(request).memberId();
        PurchaseService.Quote quote = (PurchaseService.Quote) request.getSession(false).getAttribute(QUOTE);
        try {
            Json.send(response, 200, PurchaseService.buy(buyerId, quote, quoteId, avgPrice, requestKey));
        } catch (PurchaseService.Failure e) {
            Json.error(response, e.status(), e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("구매 처리 실패", e);
        }
    }

    private long positiveLong(String value, String field) {
        try {
            long number = Long.parseLong(value);
            if (number > 0) {
                return number;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(field + "는 양의 정수여야 합니다.");
    }

    private String uuid(JsonObject body, String field) {
        String value = Json.requiredString(body, field);
        try {
            String normalized = UUID.fromString(value).toString();
            if (normalized.equalsIgnoreCase(value)) {
                return normalized;
            }
        } catch (IllegalArgumentException ignored) {
        }
        throw new IllegalArgumentException(field + "는 UUID 형식이어야 합니다.");
    }
}
