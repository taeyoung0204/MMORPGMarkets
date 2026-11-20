WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK;

CREATE TABLE MEMBER (
    MemberID       NUMBER NOT NULL,
    LoginID        VARCHAR2(30 CHAR) NOT NULL,
    PasswordHash   VARCHAR2(200 CHAR) NOT NULL,
    Nickname       VARCHAR2(30 CHAR) NOT NULL,
    GoldBalance    NUMBER NOT NULL,
    JoinedAt       DATE NOT NULL,
    PRIMARY KEY (MemberID),
    UNIQUE (LoginID),
    CHECK (GoldBalance >= 0 AND GoldBalance = TRUNC(GoldBalance))
);

CREATE TABLE ITEM (
    ItemID         NUMBER NOT NULL,
    ItemName       VARCHAR2(100 CHAR) NOT NULL,
    Category       VARCHAR2(30 CHAR) NOT NULL,
    BundleSize     NUMBER NOT NULL,
    PRIMARY KEY (ItemID),
    CHECK (BundleSize IN (1, 100))
);

CREATE TABLE HOLDING (
    MemberID       NUMBER NOT NULL,
    ItemID         NUMBER NOT NULL,
    OwnedCount     NUMBER NOT NULL,
    PRIMARY KEY (MemberID, ItemID),
    CHECK (OwnedCount >= 0 AND OwnedCount = TRUNC(OwnedCount))
);

CREATE TABLE FAVORITE (
    MemberID       NUMBER NOT NULL,
    ItemID         NUMBER NOT NULL,
    AddedAt        DATE NOT NULL,
    PRIMARY KEY (MemberID, ItemID)
);

CREATE TABLE LISTING (
    ListingID      NUMBER NOT NULL,
    SellerID       NUMBER NOT NULL,
    ItemID         NUMBER NOT NULL,
    BundlePrice    NUMBER NOT NULL,
    ListedBundles  NUMBER NOT NULL,
    ListedAt       DATE NOT NULL,
    ListingStatus  VARCHAR2(20 CHAR) NOT NULL,
    DepositAmount  NUMBER NOT NULL,
    DepositStatus  VARCHAR2(20 CHAR) NOT NULL,
    DepositSettledAt DATE,
    PRIMARY KEY (ListingID),
    CHECK (BundlePrice > 0 AND BundlePrice = TRUNC(BundlePrice)),
    CHECK (ListedBundles > 0 AND ListedBundles = TRUNC(ListedBundles)),
    CHECK (ListingStatus IN ('판매중', '판매완료', '취소', '만료')),
    CHECK (DepositAmount = FLOOR(BundlePrice * ListedBundles * 0.05)),
    CHECK (DepositStatus IN ('예치중', '전액반환완료', '취소정산완료')),
    CHECK (
        (ListingStatus = '판매중' AND DepositStatus = '예치중' AND DepositSettledAt IS NULL)
        OR (ListingStatus IN ('판매완료', '만료') AND DepositStatus = '전액반환완료' AND DepositSettledAt IS NOT NULL)
        OR (ListingStatus = '취소' AND DepositStatus = '취소정산완료' AND DepositSettledAt IS NOT NULL)
    ),
    CHECK (DepositSettledAt IS NULL OR DepositSettledAt >= ListedAt),
    CHECK (ListingStatus <> '만료' OR DepositSettledAt >= ListedAt + 1),
    CHECK (ListingStatus NOT IN ('판매완료', '취소') OR DepositSettledAt < ListedAt + 1)
);

CREATE TABLE PURCHASE_ORDER (
    OrderID            NUMBER NOT NULL,
    BuyerID            NUMBER NOT NULL,
    ItemID             NUMBER NOT NULL,
    RequestedBundles   NUMBER NOT NULL,
    ConfirmedAvgPrice  NUMBER NOT NULL,
    RequestedAt        DATE NOT NULL,
    OrderStatus        VARCHAR2(20 CHAR) NOT NULL,
    PRIMARY KEY (OrderID),
    CHECK (RequestedBundles > 0 AND RequestedBundles = TRUNC(RequestedBundles)),
    CHECK (ConfirmedAvgPrice > 0 AND ConfirmedAvgPrice = TRUNC(ConfirmedAvgPrice)),
    -- 이 데이터셋은 처리가 끝난 주문의 스냅샷만 저장합니다.
    CHECK (OrderStatus IN ('전량체결', '부분체결', '미체결'))
);

CREATE TABLE EXECUTION_DETAIL (
    OrderID        NUMBER NOT NULL,
    ExecutionNo    NUMBER NOT NULL,
    ListingID      NUMBER NOT NULL,
    FilledBundles  NUMBER NOT NULL,
    BundlePrice    NUMBER NOT NULL,
    ExecutedAt     DATE NOT NULL,
    PRIMARY KEY (OrderID, ExecutionNo),
    CHECK (ExecutionNo > 0 AND ExecutionNo = TRUNC(ExecutionNo)),
    CHECK (FilledBundles > 0 AND FilledBundles = TRUNC(FilledBundles)),
    CHECK (BundlePrice > 0 AND BundlePrice = TRUNC(BundlePrice))
);

ALTER TABLE HOLDING ADD FOREIGN KEY (MemberID) REFERENCES MEMBER(MemberID);
ALTER TABLE HOLDING ADD FOREIGN KEY (ItemID) REFERENCES ITEM(ItemID);
ALTER TABLE FAVORITE ADD FOREIGN KEY (MemberID) REFERENCES MEMBER(MemberID);
ALTER TABLE FAVORITE ADD FOREIGN KEY (ItemID) REFERENCES ITEM(ItemID);
ALTER TABLE LISTING ADD FOREIGN KEY (SellerID) REFERENCES MEMBER(MemberID);
ALTER TABLE LISTING ADD FOREIGN KEY (ItemID) REFERENCES ITEM(ItemID);
ALTER TABLE PURCHASE_ORDER ADD FOREIGN KEY (BuyerID) REFERENCES MEMBER(MemberID);
ALTER TABLE PURCHASE_ORDER ADD FOREIGN KEY (ItemID) REFERENCES ITEM(ItemID);
ALTER TABLE EXECUTION_DETAIL ADD FOREIGN KEY (OrderID) REFERENCES PURCHASE_ORDER(OrderID);
ALTER TABLE EXECUTION_DETAIL ADD FOREIGN KEY (ListingID) REFERENCES LISTING(ListingID);