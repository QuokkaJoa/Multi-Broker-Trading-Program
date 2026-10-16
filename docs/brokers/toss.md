# 토스증권
출처: https://developers.tossinvest.com/llms.txt → openapi.json, overview.md, faq.md (2026-10-07 확인, 명세 v1.2.21)

- 인증: OAuth 2.0 Client Credentials. 키는 WTS 설정 > Open API에서 직접 발급
- 토큰은 클라이언트당 1개만 유효하다. 새로 발급하면 이전 토큰이 바로 `401 token-revoked`. 앱 실행 중에 networkTest가 토큰을 새로 받으면 앱 토큰이 죽는다
- 401 중 `expired-token`·`token-revoked`만 재발급으로 풀린다. `invalid-token`·`login-user-not-found`는 다시 받아도 안 풀리므로 재시도하지 않는다 (`TossTokens`)
- 허용 IP에서만 호출된다. 그 밖은 403. 공인 IP가 바뀌면 막힌다
- 모의투자·샌드박스 없음 → mock 모드에서 토스는 fake
- 계좌 목록은 종합매매(`BROKERAGE`)만 나온다. 계좌·보유·주문 API는 `X-Tossinvest-Account: {accountSeq}` 헤더 필요
- 호출 한도: ACCOUNT 초당 1회, ASSET 초당 5회. 응답 헤더 `X-RateLimit-*`로 확인
  - 잔고 조회 한 번이 ACCOUNT를 여러 번 부른다. 1초 안에 잔고를 두 번 물으면 `RATE_LIMITED` (10/15, 서버 준비 확인을 `/api/balance`로 해서 생김 → 정적 파일로 확인)
- 보유 조회 합계는 통화별(krw, usd)로 따로 온다. 원화 환산값은 없다 → `GET /api/v1/exchange-rate`로 환산
- 예수금 전용 API는 없다. `GET /api/v1/buying-power`(현금 기준 매수 가능 금액, KRW·USD)를 쓴다
- 성공 응답은 `{ "result": … }`로 감싸져 온다
- 장 운영·휴장: `GET /api/v1/market-calendar/KR|US`
- 주식 모으기 API 없음 (decisions/005)
- 주문 관련 (보류 중, 다시 넣을 때 참고): `clientOrderId`로 중복 방지. 국내는 정수 수량만. 미국 금액 주문(`orderAmount`, 달러)은 시장가·정규장~마감 1시간 전만. 1억 원 이상은 `confirmHighValueOrder` 필요 — 어댑터가 자동으로 true를 넣지 않는다
- 예수금 API 이름(`buying-power`, `cashBuyingPower`)에 buy가 들어가 주문 메서드 금지 구조 시험에 걸린다 → 어댑터에서 `cash`로 바꿔 읽는다
- 토큰 발급(`/oauth2/token`) 오류는 다른 API와 모양이 다르다: OAuth2 형식 `{"error":"access_denied"}`(403 IP 차단)·`{"error":"invalid_client"}`(401 키 틀림). 다른 API는 `{"error":{"code":"ip-not-allowed"}}`
- 웹소켓(asyncapi.json v1.2.2): 인증은 연결할 때 한 번. 연결 중 토큰이 만료돼도 끊기지 않는다. 다시 붙기 전에 옛 연결을 먼저 닫는다 (안 그러면 연결 2개 한도로 서로 밀어낸다)
- 웹소켓: 서버 데이터는 180초 끊김 타이머를 되돌리지 않는다. 받기만 해도 60초마다 텍스트 `PING`을 보내야 한다
- 웹소켓: `rejected`된 항목은 고치기 전엔 다시 선언해도 또 거부된다 → 다음 선언에서 뺀다. 체결 구독의 거부 이유는 `stock-not-found`·`symbol-market-mismatch` 둘뿐이라 (asyncapi v1.2.2) 앱을 켜 둔 동안 거부 목록을 비우지 않는다. 구독 직후 첫 값은 안 오고 다음 체결부터 온다
- 웹소켓: JDK `WebSocket`은 한 프레임을 여러 조각(`last=false`)으로 넘길 수 있다. 조각을 모아 `last=true`에서 JSON으로 읽는다
- 웹소켓: 국내 체결은 NXT 애프터(20:00)가 끝나면 다음 날 아침까지 안 온다. 저녁에 체결을 확인하려면 미국 종목을 고른다 (networkTest가 달러 종목을 먼저 고름)
- 웹소켓: 토스 앱·WTS를 같이 켜 둬도 우리 연결과 서로 끊지 않았다 (2026-10-08 dryrun, 15분 동안 재연결 0번)
- 예수금: `cashBuyingPower`는 KRW·USD 모두 토스 웹 "총 주문 가능 금액"의 원화·달러와 같다. KRW 값에 달러 환산분은 안 섞인다 (2026-10-08 dryrun)
- 환율: 토스 웹 자산 화면은 달러를 지금 `midRate`가 아니라 토스 시세의 "어제보다" 기준값(전일 환율)으로 원화 환산한다. 자산 화면 역산 = 시세 표·환율 상세(`/indices/exchange-rate`)의 "현재 − 어제보다" (10/15). 10/15에는 10/14 15:31 구간 `midRate`, 10/16에는 10/15 저녁(20:00~21:07 사이) 값이라 잡는 시각이 날마다 다르다. 규칙을 못 찾아 우리는 지금 `midRate`를 쓴다 (2단계 결정 기록 10/16). 환율이 크게 움직인 날은 토스 자산 화면과 0.5% 넘게 다를 수 있다
  - (10/08 기록) 그래서 날마다 0.1~0.4% 차이가 났다. 0.14% 높게 보인 것도 같은 이유
- 환율 `dateTime`: `validFrom`이 가장 가까운 1분 구간을 주는 것으로 보인다. 구간 시작 초가 날마다 다르다(:04·:38·:42·:56). 주말에는 한 값으로 멈춘다 (10/15, openapi.json v1.2.24)
- 토스 웹 총액은 구성 값보다 몇 초 늦게 갱신될 때가 있다. 비교할 때는 몇 초 뒤 다시 읽는다
