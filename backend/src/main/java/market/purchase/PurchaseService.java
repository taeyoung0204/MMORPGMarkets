package market.purchase;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import market.db.Database;
import market.purchase.PurchaseStore.Offer;

public final class PurchaseService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    public record Quote(String quoteId, long buyerId, long itemId, long requestedBundles) implements Serializable {
    }

    public record Estimate(Quote quote, long availableBundles, long suggestedAvgPrice, long quotedPayment,
                           long goldBalance) {
    }

    public record Result(long orderId, String orderStatus, long requestedBundles, long filledBundles,
                         long unfilledBundles, long maxPayment, long actualPayment, long refund, String message) {
    }

    public static final class Failure extends RuntimeException {
        private final int status;

        Failure(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private record Fill(Offer offer, long bundles, long payment) {
    }

    private PurchaseService() {
    }

    public static Estimate estimate(long buyerId, long itemId, long requestedBundles) throws SQLException {
        try (Connection connection = Database.open()) {
            if (PurchaseStore.bundleSize(connection, itemId, false) == 0) {
                throw new Failure(404, "품목을 찾을 수 없습니다.");
            }
            List<Offer> offers = PurchaseStore.offers(connection, itemId, buyerId, now());
            long availableBundles = 0;
            long remaining = requestedBundles;
            long quotedPayment = 0;
            for (Offer offer : offers) {
                availableBundles = Math.addExact(availableBundles, offer.remainingBundles());
                long taken = Math.min(remaining, offer.remainingBundles());
                quotedPayment = Math.addExact(quotedPayment, Math.multiplyExact(taken, offer.bundlePrice()));
                remaining -= taken;
            }
            if (availableBundles == 0) {
                throw new Failure(409, "매물 없음");
            }
            if (remaining > 0) {
                throw new IllegalArgumentException("구매 가능 수량은 " + availableBundles + "묶음입니다.");
            }
            long suggestedAvgPrice = (quotedPayment - 1) / requestedBundles + 1;
            Quote quote = new Quote(UUID.randomUUID().toString(), buyerId, itemId, requestedBundles);
            return new Estimate(quote, availableBundles, suggestedAvgPrice, quotedPayment,
                    PurchaseStore.goldBalance(connection, buyerId));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("견적 수량 또는 금액이 너무 큽니다.");
        }
    }

    public static Result buy(long buyerId, Quote quote, String quoteId, long confirmedAvgPrice, String requestKey)
            throws SQLException {
        try (Connection connection = Database.open()) {
            connection.setAutoCommit(false);
            try {
                Result saved = findPurchase(connection, buyerId, quoteId, confirmedAvgPrice, requestKey);
                if (saved != null) {
                    return saved;
                }
                if (quote == null || quote.buyerId() != buyerId || !quote.quoteId().equals(quoteId)) {
                    throw new Failure(409, "구매 견적을 다시 조회하세요.");
                }

                int bundleSize = PurchaseStore.bundleSize(connection, quote.itemId(), true);
                if (bundleSize == 0) {
                    throw new Failure(404, "품목을 찾을 수 없습니다.");
                }
                long balance = PurchaseStore.lockMembers(connection, quote.itemId(), buyerId);
                saved = findPurchase(connection, buyerId, quoteId, confirmedAvgPrice, requestKey);
                if (saved != null) {
                    connection.rollback();
                    return saved;
                }

                Result result = execute(connection, quote, confirmedAvgPrice, requestKey, bundleSize, balance);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static Result execute(Connection connection, Quote quote, long avgPrice, String requestKey,
                                  int bundleSize, long balance) throws SQLException {
        long requestedBundles = quote.requestedBundles();
        long maxPayment = maxPayment(avgPrice, requestedBundles);
        if (balance < maxPayment) {
            throw new Failure(409, "최대 지불 금액만큼의 골드가 필요합니다.");
        }
        String asOf = now();
        List<Offer> offers = PurchaseStore.offers(connection, quote.itemId(), quote.buyerId(), asOf);
        List<Fill> fills = new ArrayList<>();
        long filled = 0;
        long paid = 0;
        for (Offer offer : offers) {
            long taken = Math.min(requestedBundles - filled, offer.remainingBundles());
            if (offer.bundlePrice() > avgPrice) {
                long slack = avgPrice * filled - paid;
                taken = Math.min(taken, slack / (offer.bundlePrice() - avgPrice));
            }
            if (taken == 0) {
                break;
            }
            long payment = taken * offer.bundlePrice();
            fills.add(new Fill(offer, taken, payment));
            filled += taken;
            paid += payment;
        }
        String status = filled == requestedBundles ? "전량체결" : filled == 0 ? "미체결" : "부분체결";
        long orderId = PurchaseStore.insertOrder(connection, quote.buyerId(), quote.itemId(), requestedBundles,
                avgPrice, asOf, status);
        saveRequest(connection, requestKey, quote.quoteId(), orderId);

        PurchaseStore.changeGold(connection, quote.buyerId(), -maxPayment);
        int executionNo = 1;
        for (Fill fill : fills) {
            Offer offer = fill.offer();
            long units = Math.multiplyExact(fill.bundles(), bundleSize);
            PurchaseStore.insertExecution(connection, orderId, executionNo++, offer, fill.bundles(), asOf);
            PurchaseStore.moveHolding(connection, quote.buyerId(), quote.itemId(), offer.sellerId(), units);
            PurchaseStore.changeGold(connection, offer.sellerId(), fill.payment() - fill.payment() / 20);
            if (fill.bundles() == offer.remainingBundles()) {
                PurchaseStore.completeListing(connection, offer.listingId(), asOf);
                PurchaseStore.changeGold(connection, offer.sellerId(), offer.depositAmount());
            }
        }
        long refund = maxPayment - paid;
        if (refund > 0) {
            PurchaseStore.changeGold(connection, quote.buyerId(), refund);
        }
        return result(orderId, status, requestedBundles, filled, maxPayment, paid);
    }

    private static void saveRequest(Connection connection, String requestKey, String quoteId, long orderId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO PURCHASE_REQUEST (RequestKey, QuoteID, OrderID) VALUES (?, ?, ?)")) {
            statement.setString(1, requestKey);
            statement.setString(2, quoteId);
            statement.setLong(3, orderId);
            statement.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1) {
                throw new Failure(409, "이미 처리된 구매 요청입니다.");
            }
            throw e;
        }
    }

    private static Result findPurchase(Connection connection, long buyerId, String quoteId, long avgPrice,
                                       String requestKey) throws SQLException {
        String sql = """
                SELECT R.RequestKey, R.QuoteID, O.OrderID, O.BuyerID, O.OrderStatus,
                       O.RequestedBundles, O.ConfirmedAvgPrice,
                       (SELECT NVL(SUM(E.FilledBundles), 0) FROM EXECUTION_DETAIL E
                        WHERE E.OrderID = O.OrderID) AS FilledBundles,
                       (SELECT NVL(SUM(E.FilledBundles * E.BundlePrice), 0) FROM EXECUTION_DETAIL E
                        WHERE E.OrderID = O.OrderID) AS ActualPayment
                FROM PURCHASE_REQUEST R JOIN PURCHASE_ORDER O ON O.OrderID = R.OrderID
                WHERE R.RequestKey = ? OR R.QuoteID = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requestKey);
            statement.setString(2, quoteId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return null;
                }
                if (row.getLong("BuyerID") != buyerId || row.getLong("ConfirmedAvgPrice") != avgPrice
                        || !requestKey.equals(row.getString("RequestKey")) || !quoteId.equals(row.getString("QuoteID"))) {
                    throw new Failure(409, "이미 처리된 구매 요청입니다.");
                }
                long requested = row.getLong("RequestedBundles");
                return result(row.getLong("OrderID"), row.getString("OrderStatus"), requested,
                        row.getLong("FilledBundles"), maxPayment(avgPrice, requested), row.getLong("ActualPayment"));
            }
        }
    }

    private static Result result(long orderId, String status, long requested, long filled, long maximum, long paid) {
        String message = switch (status) {
            case "전량체결" -> "요청한 수량을 구매했습니다.";
            case "부분체결" -> "구매 가능한 수량만 체결했습니다.";
            default -> "체결 가능한 매물이 없습니다.";
        };
        return new Result(orderId, status, requested, filled, requested - filled, maximum, paid, maximum - paid,
                message);
    }

    private static long maxPayment(long avgPrice, long requestedBundles) {
        try {
            return Math.multiplyExact(avgPrice, requestedBundles);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("최대 지불 금액이 너무 큽니다.");
        }
    }

    private static String now() {
        return LocalDateTime.now(KST).format(DATE_TIME);
    }
}
