# 토스증권
출처: https://developers.tossinvest.com/llms.txt → openapi.json, overview.md, faq.md (2026-10-07 확인, 명세 v1.2.19)

- 인증: OAuth 2.0 Client Credentials. 키는 WTS 설정 > Open API에서 직접 발급
- 토큰은 클라이언트당 1개만 유효하다. 새로 발급하면 이전 토큰이 바로 `401 token-revoked`. 앱 실행 중에 networkTest가 토큰을 새로 받으면 앱 토큰이 죽는다
- 허용 IP에서만 호출된다. 그 밖은 403. 공인 IP가 바뀌면 막힌다
- 모의투자·샌드박스 없음 → mock 모드에서 토스는 fake
- 계좌 목록은 종합매매(`BROKERAGE`)만 나온다. 계좌·보유·주문 API는 `X-Tossinvest-Account: {accountSeq}` 헤더 필요
- 호출 한도: ACCOUNT 초당 1회, ASSET 초당 5회. 응답 헤더 `X-RateLimit-*`로 확인
- 보유 조회 합계는 통화별(krw, usd)로 따로 온다. 원화 환산값은 없다 → `GET /api/v1/exchange-rate`로 환산
- 예수금 전용 API는 없다. `GET /api/v1/buying-power`(현금 기준 매수 가능 금액, KRW·USD)를 쓴다
- 성공 응답은 `{ "result": … }`로 감싸져 온다
- 장 운영·휴장: `GET /api/v1/market-calendar/KR|US`
- 주식 모으기 API 없음 (decisions/005)
- 주문 관련 (보류 중, 다시 넣을 때 참고): `clientOrderId`로 중복 방지. 국내는 정수 수량만. 미국 금액 주문(`orderAmount`, 달러)은 시장가·정규장~마감 1시간 전만. 1억 원 이상은 `confirmHighValueOrder` 필요 — 어댑터가 자동으로 true를 넣지 않는다
