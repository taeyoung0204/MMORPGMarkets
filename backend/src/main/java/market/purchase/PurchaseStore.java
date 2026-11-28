package market.purchase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

final class PurchaseStore {
    record Offer(long listingId, long sellerId, long bundlePrice, long remainingBundles, long depositAmount) {
    }

    private PurchaseStore() {
    }

    static int bundleSize(Connection connection, long itemId, boolean lock) throws SQLException {
        String sql = "SELECT BundleSize FROM ITEM WHERE ItemID = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, itemId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("BundleSize") : 0;
            }
        }
    }

    static List<Offer> offers(Connection connection, long itemId, long buyerId, String asOf)
            throws SQLException {
        String sql = """
                SELECT L.ListingID, L.SellerID, L.BundlePrice, L.DepositAmount,
                       L.ListedBundles - NVL(F.FilledBundles, 0) AS RemainingBundles
                FROM LISTING L
                LEFT JOIN (
                    SELECT ListingID, SUM(FilledBundles) AS FilledBundles
                    FROM EXECUTION_DETAIL GROUP BY ListingID
                ) F ON F.ListingID = L.ListingID
                WHERE L.ItemID = ? AND L.SellerID <> ? AND L.ListingStatus = '판매중'
                  AND L.ListedAt <= TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')
                  AND L.ListedAt + 365 > TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')
                  AND L.ListedBundles > NVL(F.FilledBundles, 0)
                ORDER BY L.BundlePrice, L.ListedAt
                """;
        List<Offer> offers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, itemId);
            statement.setLong(2, buyerId);
            statement.setString(3, asOf);
            statement.setString(4, asOf);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    offers.add(new Offer(result.getLong("ListingID"), result.getLong("SellerID"),
                            result.getLong("BundlePrice"), result.getLong("RemainingBundles"),
                            result.getLong("DepositAmount")));
                }
            }
        }
        return offers;
    }

    static long lockMembers(Connection connection, long itemId, long buyerId) throws SQLException {
        TreeSet<Long> memberIds = new TreeSet<>();
        memberIds.add(buyerId);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT DISTINCT SellerID FROM LISTING WHERE ItemID = ? AND ListingStatus = '판매중'")) {
            statement.setLong(1, itemId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    memberIds.add(result.getLong("SellerID"));
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT MemberID FROM MEMBER WHERE MemberID = ? FOR UPDATE")) {
            for (long memberId : memberIds) {
                statement.setLong(1, memberId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("구매 처리 대상 회원을 찾을 수 없습니다.");
                    }
                }
            }
        }
        return goldBalance(connection, buyerId);
    }

    static long goldBalance(Connection connection, long buyerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT GoldBalance FROM MEMBER WHERE MemberID = ?")) {
            statement.setLong(1, buyerId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("구매 회원을 찾을 수 없습니다.");
                }
                return result.getLong("GoldBalance");
            }
        }
    }

    static void changeGold(Connection connection, long memberId, long delta) throws SQLException {
        String sql = """
                UPDATE MEMBER SET GoldBalance = GoldBalance + ?
                WHERE MemberID = ? AND GoldBalance + ? >= 0
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, delta);
            statement.setLong(2, memberId);
            statement.setLong(3, delta);
            requireOne(statement.executeUpdate(), "재화 변경을 확인할 수 없습니다.");
        }
    }

    static void moveHolding(Connection connection, long buyerId, long itemId, long sellerId, long units)
            throws SQLException {
        if (units <= 0) {
            throw new SQLException("이동할 아이템 개수는 양수여야 합니다.");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE HOLDING SET OwnedCount = OwnedCount - ?
                WHERE MemberID = ? AND ItemID = ? AND OwnedCount >= ?
                """)) {
            statement.setLong(1, units);
            statement.setLong(2, sellerId);
            statement.setLong(3, itemId);
            statement.setLong(4, units);
            requireOne(statement.executeUpdate(), "판매자 보유 수량을 확인할 수 없습니다.");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                MERGE INTO HOLDING H
                USING (SELECT ? AS MemberID, ? AS ItemID FROM DUAL) S
                ON (H.MemberID = S.MemberID AND H.ItemID = S.ItemID)
                WHEN MATCHED THEN UPDATE SET OwnedCount = H.OwnedCount + ?
                WHEN NOT MATCHED THEN INSERT (MemberID, ItemID, OwnedCount)
                    VALUES (S.MemberID, S.ItemID, ?)
                """)) {
            statement.setLong(1, buyerId);
            statement.setLong(2, itemId);
            statement.setLong(3, units);
            statement.setLong(4, units);
            requireOne(statement.executeUpdate(), "구매자 보유 수량 변경을 확인할 수 없습니다.");
        }
    }

    static void completeListing(Connection connection, long listingId, String asOf) throws SQLException {
        String sql = """
                UPDATE LISTING L
                SET ListingStatus = '판매완료', DepositStatus = '전액반환완료',
                    DepositSettledAt = TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')
                WHERE L.ListingID = ? AND L.ListingStatus = '판매중'
                  AND L.DepositStatus = '예치중' AND L.DepositSettledAt IS NULL
                  AND L.ListedAt <= TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')
                  AND L.ListedAt + 365 > TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')
                  AND L.ListedBundles = NVL((
                      SELECT SUM(E.FilledBundles) FROM EXECUTION_DETAIL E
                      WHERE E.ListingID = L.ListingID
                  ), 0)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, asOf);
            statement.setLong(2, listingId);
            statement.setString(3, asOf);
            statement.setString(4, asOf);
            requireOne(statement.executeUpdate(), "판매완료 및 보증금 반환 상태를 확인할 수 없습니다.");
        }
    }

    static long insertOrder(Connection connection, long buyerId, long itemId, long requestedBundles,
            long confirmedAvgPrice, String asOf, String status) throws SQLException {
        long orderId;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT PURCHASE_ORDER_SEQ.NEXTVAL FROM DUAL");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("구매 주문번호를 생성할 수 없습니다.");
            }
            orderId = result.getLong(1);
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO PURCHASE_ORDER
                    (OrderID, BuyerID, ItemID, RequestedBundles, ConfirmedAvgPrice, RequestedAt, OrderStatus)
                VALUES (?, ?, ?, ?, ?, TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS'), ?)
                """)) {
            statement.setLong(1, orderId);
            statement.setLong(2, buyerId);
            statement.setLong(3, itemId);
            statement.setLong(4, requestedBundles);
            statement.setLong(5, confirmedAvgPrice);
            statement.setString(6, asOf);
            statement.setString(7, status);
            requireOne(statement.executeUpdate(), "구매 주문 저장을 확인할 수 없습니다.");
        }
        return orderId;
    }

    static void insertExecution(Connection connection, long orderId, int executionNo, Offer offer,
            long filledBundles, String asOf) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO EXECUTION_DETAIL
                    (OrderID, ExecutionNo, ListingID, FilledBundles, BundlePrice, ExecutedAt)
                VALUES (?, ?, ?, ?, ?, TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS'))
                """)) {
            statement.setLong(1, orderId);
            statement.setInt(2, executionNo);
            statement.setLong(3, offer.listingId());
            statement.setLong(4, filledBundles);
            statement.setLong(5, offer.bundlePrice());
            statement.setString(6, asOf);
            requireOne(statement.executeUpdate(), "체결상세 저장을 확인할 수 없습니다.");
        }
    }

    private static void requireOne(int updated, String message) throws SQLException {
        if (updated != 1) {
            throw new SQLException(message);
        }
    }
}
