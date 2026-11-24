# 게이머즈 Phase 4 · React–JSON API 연동 규격

React가 요청을 보내고 Java Servlet이 JSON으로 응답한다. 아래 표의 API 경로 앞에는 `/mmorpg-markets/api`를 붙인다. 예를 들어 검색 API의 전체 경로는 `/mmorpg-markets/api/market`이다. React의 화면 주소와 이동 방식은 프론트 담당자가 정한다.

## 요청·응답 공통 규칙

- 조회는 GET 쿼리 파라미터, 로그인·로그아웃과 변경은 POST로 보낸다. POST 본문은 `Content-Type: application/json`으로 보내고 JSON 응답도 같은 타입과 UTF-8을 사용한다.
- ID·수량·골드는 JSON 정수, `admin`은 boolean이다. 날짜는 `yyyy-MM-dd`, 일시는 `yyyy-MM-dd HH:mm:ss` 문자열이며 KST 기준이다.
- 입력 오류나 실패 응답에는 `error` 문자열, 변경 성공 응답에는 `message` 문자열을 넣는다. React가 안내를 표시하고 화면 이동 또는 재조회를 처리한다.

| JSON 필드 | 값 |
| --- | --- |
| `rows` | 조회 결과 배열. 결과가 없으면 `[]` |
| `filters` | 목록 조회에 실제 적용한 검색값과 보기 옵션 |
| `item` | 매물 상세 화면의 품목 정보 |
| `profile` | 내 정보 |
| `loginUser` | 로그인 회원의 `memberId`, `loginId`, `admin`. 비로그인 세션 조회에서는 `null` |
| `csrfToken` | 세션 조회와 로그인 성공 시 받는 변경 요청용 토큰 |
| `message`, `error` | 안내 문자열. 필요한 경우에만 전달 |

## 세션·로그인

Tomcat 세션 쿠키로 로그인 상태를 유지한다. React는 같은 출처의 `/mmorpg-markets/api/...` 상대 경로로 호출한다. 개발 중에는 해당 경로를 Vite 프록시로 Tomcat에 그대로 전달한다.

| 요청 | 입력 | 성공 응답 |
| --- | --- | --- |
| `GET /session` | 없음 | `loginUser`, `csrfToken`. 비로그인도 200과 `loginUser: null` 반환 |
| `POST /login` | `loginId`, `password` | `loginUser`, `csrfToken` |
| `POST /logout` | `{}` | `message` |

앱을 열거나 새로고침하면 `/session`으로 로그인 상태와 토큰을 읽는다. 모든 POST 요청은 받은 토큰을 `X-CSRF-Token` 헤더에 넣는다. 로그인 성공 시 반환된 토큰을 이후 요청에 사용한다. 로그아웃하면 서버 세션을 종료하고, React의 회원·토큰 상태를 비운다. 다음 로그인 전에 `/session`을 다시 조회한다.

`/session`과 `/login`은 비로그인 상태에서 호출할 수 있다. 나머지 API는 로그인한 회원이 사용한다. 회원 비밀번호와 해시는 응답에 포함하지 않는다.

## 조회 API

아래의 목록 응답에는 `rows`와 `filters`를 넣는다. `/market/item`에는 `item`도 넣고, `/me`는 `profile`을 반환한다.

| GET 경로 · 기능 | 입력 | 결과 필드 |
| --- | --- | --- |
| `/market` · 거래소 검색 (Q01) | `category` 기본값 `강화 재료`, `name` 선택 | `rows`의 `itemId`, `itemName`, `category`, `bundleSize` |
| `/market/item` · 매물 상세 (Q08) | `itemId` 필수, 관리자만 `memberId` 선택 | `item`의 `itemId`, `itemName`, `bundleSize`. `rows`의 `listingId`, `sellerNickname`, `bundlePrice`, `remainingBundles`, `listedAt` |
| `/market/prices` · 시세 (Q09) | `itemId`, `from`, `until` 모두 선택 | `rows`의 `itemId`, `itemName`, `tradeDate`, `averagePrice`, `soldBundles` |
| `/favorites` · 관심 목록 (전체 / Q05) | `view=all` 기본값, `view=available` 구매 가능 | 두 보기 모두 `rows`의 `itemId`, `itemName`, `category`, `addedAt` |
| `/me/inventory` · 내 보유 (Q02 / Q10) | `view=all` 기본값, `view=notFavorited` 관심 미등록 | `rows`의 `itemId`, `itemName`. `all`에서는 `ownedCount`도 전달 |
| `/me/sales` · 판매 집계 (Q03) | 일반 회원은 별도 입력 없음 | `rows`의 `memberId`, `nickname`, `soldBundles`, `grossSales`, `totalFee`, `netSales` |
| `/me/orders` · 주문 상태 (Q06) | `view=status` 기본값, `status` 복수 선택 | `rows`의 `orderId`, `buyerId`, `itemId`, `requestedBundles`, `confirmedAvgPrice`, `orderStatus` |
| `/me/orders` · 주문별 체결 (Q07) | `view=summary`, `orderId` 선택 | `rows`의 `orderId`, `buyerNickname`, `itemName`, `orderStatus`, `confirmedAvgPrice`, `requestedBundles`, `filledBundles`, `unfilledBundles`, `maxPayment`, `actualPayment`, `refund` |
| `/me` · 내 정보 | 없음 | `profile`의 `memberId`, `loginId`, `nickname`, `goldBalance`, `joinedAt` |
| `/admin` · 관리자 (Q04) | `minimumGold` 선택 | `rows`의 `memberId`, `nickname`, `goldBalance` |

- 분류는 `강화 재료`, `각인서`, `전투 용품`, `생활`, `모험의 서`다.
- 주문 상태는 `전량체결`, `부분체결`, `미체결`이다. 복수 선택은 `?view=status&status=부분체결&status=미체결`처럼 보내고, `status` 생략 시 `부분체결`과 `미체결`을 조회한다.
- 시세의 `from`, `until`은 `datetime-local` 값인 `yyyy-MM-ddTHH:mm`으로 보낸다. 시작시각은 포함하고 종료시각은 제외한다.
- 일반 회원의 조회 대상은 서버가 로그인 세션에서 정한다. 관리자는 Q02·Q03·Q05·Q06·Q07·Q08·Q10에서 `memberId`로 대상 회원을 지정한다. Q03·Q06·Q07·Q08은 관리자가 `memberId`를 생략하면 전체를 조회한다. 다른 대상 회원별 조회에서는 관리자의 `memberId`가 필수다.
- Q08은 조회 대상 회원의 매물을 제외한다. 관리자가 `memberId`를 생략하면 해당 품목의 유효 매물 전체를 반환하고 `filters.memberId`는 `null`이다. 구매 견적·체결의 자기 매물 제외 기준은 실제 로그인한 구매자다.
- Q05·Q08은 서버의 조회 기준시각을 `filters.asOf`로 반환한다. 형식은 `yyyy-MM-dd HH:mm:ss`다. 화면에 기준시각을 표시한다.
- 매물 유효기간은 등록시각부터 365일이다. Q05·Q08과 구매 견적·체결은 등록시각이 지났거나 같은 시점부터 만료시각 직전까지의 매물만 대상으로 한다.
- 매물 상세의 `rows`가 비면 **매물 없음**, 시세의 `rows`가 비면 **거래 기록 없음**을 표시한다. 나머지 빈 목록은 **조회 결과 없음**으로 표시한다.

검색 응답 예시

```json
{
  "rows": [
    {
      "itemId": 6884308,
      "itemName": "아비도스 목재",
      "category": "생활",
      "bundleSize": 100
    }
  ],
  "filters": { "category": "생활", "name": "목재" }
}
```

## 변경 API

각 요청의 JSON 본문에는 아래 필드를 넣고, 성공 시 `message`를 받는다. 회원 정보의 변경 대상은 로그인한 본인이다.

| POST 경로 | JSON 본문 필드 | 성공 후 React 동작 |
| --- | --- | --- |
| `/favorites/add`, `/favorites/remove` | `itemId` | 관심 목록 재조회 |
| `/me/nickname` | `nickname` | 내 정보 재조회 |
| `/me/password` | `oldPassword`, `newPassword`, `confirmPassword` | 결과 안내, 비밀번호 입력값 비우기 |

예를 들어 관심 추가 본문은 `{"itemId":6884308}`이다. 요청 중에는 변경 버튼을 중복 누르지 않도록 처리한다. 실패하면 `error`를 표시하고 필요한 데이터를 다시 조회한다.

## HTTP 상태

| 상태 | 의미 |
| --- | --- |
| 200 | 정상 조회·변경 완료. 빈 목록도 200 |
| 400 | 필수 입력 누락, 잘못된 값, 현재 비밀번호 불일치 등 |
| 401 | 로그인 실패 또는 보호된 API의 비로그인·세션 만료 |
| 403 | 관리자 권한 없음 또는 CSRF 토큰 오류 |
| 404 | 지정한 품목 등이 존재하지 않음 |
| 409 | 중복 관심등록 또는 다른 요청과의 갱신 충돌 |
| 500 | 서버·DB 처리 실패 |

실패 응답 예시는 `{"error":"현재 비밀번호가 일치하지 않습니다."}`다. React는 HTTP 상태와 `error`를 확인한다. 관심 삭제 대상이 이미 없으면 200과 해당 안내 `message`를 반환한다.

화면 구성과 작업 순서는 프론트 담당자가 정한다. 각자 합의한 입력·결과 필드로 개발하고, 정한 통합 시점에 예시 JSON을 실제 API 응답으로 전환한다.
