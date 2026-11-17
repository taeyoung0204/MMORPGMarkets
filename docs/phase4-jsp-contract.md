# 게이머즈 Phase 4 · JSP 화면 연동 규격

조회 폼은 GET, 로그인·로그아웃과 데이터 변경 폼은 POST로 보낸다. Servlet이 조회 결과를 JSP에 전달한다. 아래 URL을 링크나 폼에 쓸 때는 앱의 컨텍스트 경로를 앞에 붙인다.

## JSP 공통 속성

| 이름 | 전달하는 값 |
| --- | --- |
| `rows` | 조회 결과 목록. 각 행의 필드는 `${row.itemName}`처럼 접근한다. 결과가 없으면 빈 목록을 전달한다. |
| `filters` | 현재 적용된 검색값과 보기 옵션. 예를 들어 `${filters.category}`로 현재 분류를 읽는다. |
| `message`, `error` | 성공 또는 오류 안내 문구. 안내가 필요한 경우에만 전달한다. |
| `sessionScope.loginUser` | 로그인 회원의 `memberId`, `loginId`, `admin`. |
| `csrfToken` | POST 폼에 넣을 공통 숨김 필드 값. 필드명도 `csrfToken`이다. |

ID·수량·골드는 정수로 전달한다. 날짜는 `yyyy-MM-dd`, 등록일시 등은 `yyyy-MM-dd HH:mm:ss` 문자열로 전달한다. 기준 시간대는 KST다.

## 조회 화면

| 화면 · GET URL | 입력 필드 | JSP 결과 |
| --- | --- | --- |
| 거래소 검색 · `/market` (Q01) | `category` 기본값 `강화 재료`, `name` 선택 | `rows`의 필드 `itemId`, `itemName`, `category`, `bundleSize` |
| 매물 상세 · `/market/item` (Q08) | `itemId` 필수 | `item`의 필드 `itemId`, `itemName`, `bundleSize`. `rows`의 필드 `listingId`, `sellerNickname`, `bundlePrice`, `remainingBundles`, `listedAt` |
| 시세 · `/market/prices` (Q09) | `itemId`, `from`, `until` 모두 선택 | `rows`의 필드 `itemId`, `itemName`, `tradeDate`, `averagePrice`, `soldBundles` |
| 관심 목록 · `/favorites` (전체 / Q05) | `view=all` 기본값, `view=available` 구매 가능 | 두 보기 모두 `rows`의 필드 `itemId`, `itemName`, `category`, `addedAt` |
| 내 보유 · `/me/inventory` (Q02 / Q10) | `view=all` 기본값, `view=notFavorited` 관심 미등록 | `rows`의 필드 `itemId`, `itemName`. `all`에서는 `ownedCount`도 전달한다. |
| 판매 집계 · `/me/sales` (Q03) | 일반 회원은 별도 입력 없음 | `rows`의 필드 `memberId`, `nickname`, `soldBundles`, `grossSales`, `totalFee`, `netSales` |
| 주문 상태 · `/me/orders` (Q06) | `view=status` 기본값, `status` 복수 선택 | `rows`의 필드 `orderId`, `buyerId`, `itemId`, `requestedBundles`, `confirmedAvgPrice`, `orderStatus` |
| 주문별 체결 · `/me/orders` (Q07) | `view=summary`, `orderId` 선택 | `rows`의 필드 `orderId`, `buyerNickname`, `itemName`, `orderStatus`, `confirmedAvgPrice`, `requestedBundles`, `filledBundles`, `unfilledBundles`, `maxPayment`, `actualPayment`, `refund` |
| 내 정보 · `/me` | 없음 | `profile`의 필드 `memberId`, `loginId`, `nickname`, `goldBalance`, `joinedAt` |
| 관리자 · `/admin` (Q04) | `minimumGold` 선택 | `rows`의 필드 `memberId`, `nickname`, `goldBalance` |

- 분류는 `강화 재료`, `각인서`, `전투 용품`, `생활`, `모험의 서` 중 하나다.
- 주문 상태는 `전량체결`, `부분체결`, `미체결`이다. 복수 선택 시 같은 이름의 `status` 필드를 반복해서 보내고, 생략하면 `부분체결`과 `미체결`을 조회한다. 주문을 선택하면 `/me/orders?view=summary&orderId=…`로 이동한다.
- 시세의 `from`, `until`은 `datetime-local` 값으로 보낸다. 시작 시각은 포함하고 종료 시각은 제외한다.
- 매물 상세에서 `rows`가 비면 **매물 없음**, 시세에서 비면 **거래 기록 없음**을 표시한다.
- 일반 회원의 조회 대상은 로그인 세션에서 정한다. 관리자는 Q02·Q03·Q05·Q06·Q07·Q08·Q10의 URL에 `memberId`를 붙여 대상 회원을 지정할 수 있다. Q03·Q06·Q07에서는 관리자가 `memberId`를 생략하면 전체를 조회한다.

## 로그인·변경 폼

로그인 화면은 `GET /login`에서 연다. 모든 POST 폼에 `csrfToken` 숨김 필드를 포함한다.

| POST URL | 폼 필드 | 처리 후 이동 |
| --- | --- | --- |
| `/login` | `loginId`, `password` | 성공 시 `/market`. 실패 시 로그인 화면에 `error` 표시 |
| `/logout` | 공통 숨김 필드만 | `/login` |
| `/favorites/add`, `/favorites/remove` | `itemId` | `/favorites` |
| `/me/nickname` | `nickname` | `/me` |
| `/me/password` | `oldPassword`, `newPassword`, `confirmPassword` | `/me` |

관심 목록·회원 정보 변경 후 이동한 화면에서 `message` 또는 `error`를 한 번 표시한다.

첫 통합 대상은 로그인과 거래소 검색이다. 검색 화면은 결과 행이 있는 경우와 빈 목록인 경우를 같은 필드로 준비한다.
