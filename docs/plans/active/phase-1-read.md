# 1단계: 토스 조회 + 실시간 첫 화면

## 목표
토스 종합매매 계좌의 예수금·보유를 dryrun으로 실제 조회해, 내 맥 브라우저 첫 화면에 원화 합산으로 보여준다.
평가금은 웹소켓 체결가로 실시간 갱신한다.
숫자는 토스 앱과 맞는다 (원화 원 단위, 달러 환산 0.5% 이내 — product.md 성공 기준).

## 확인한 사실 (공식 문서, 2026-10-07, REST 명세 v1.2.21, 웹소켓 명세 v1.2.2)
출처: https://developers.tossinvest.com/llms.txt → openapi.json, asyncapi.json, overview.md, faq.md
- 토큰: `POST /oauth2/token` (form-urlencoded, `client_id`·`client_secret`). 응답에 `expires_in`(초). refresh token 없음 → 만료되면 다시 발급. 클라이언트당 1개만 유효
- 계좌: `GET /api/v1/accounts` → `accountNo`(계좌번호)·`accountSeq`(호출용 키). 다른 API는 `X-Tossinvest-Account: {accountSeq}` 헤더. 한도 ACCOUNT 초당 1회
- 보유: `GET /api/v1/holdings` → 종목마다 `currency`, `quantity`, `lastPrice`, `marketValue.amount`(평가금)·`amountAfterCost`(세금·수수료 뺀 평가금). 금액은 모두 문자열. 한도 ASSET 초당 5회
- 예수금: 전용 API 없음. `GET /api/v1/buying-power?currency=KRW|USD` → `cashBuyingPower`(현금 매수 가능 금액). 한도 ORDER_INFO 초당 6회 (09:00~09:10은 3회)
- 환율: `GET /api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW` → `rate`(매수 환율), `midRate`(매매기준율). 1분마다 바뀜. 한도 MARKET_INFO 초당 3회
- 오류: 401 `expired-token`·`token-revoked`, 403 `ip-not-allowed`(토큰 발급은 `access_denied`), 429 `rate-limit-exceeded`
- 웹소켓: `wss://openapi-ws.tossinvest.com/ws/v1`, `Authorization: Bearer` 헤더, 허용 IP 같음
  - 구독은 JSON 배열 하나가 구독 전체 (새 배열이 이전 것을 통째로 대체). 체결은 `trade:kr`·`trade:us` + 종목 코드
  - 체결 프레임: `{"type":"message","topic":"trade:us:AAPL","data":{"price","volume","timestamp","currency"}}`. 구독 직후 첫 값은 안 온다 (다음 체결부터)
  - 프레임이 유실될 수 있다 (최신 값 우선, 순번 없음)
  - 국내는 KRX 정규장 + NXT 프리·애프터 체결이 섞여 온다. 미국은 프리·정규·애프터·데이마켓 전부
  - 180초 동안 내가 아무것도 안 보내면 서버가 끊는다 → 60초마다 `PING`
  - 계정당 동시 연결 2개. 넘으면 가장 오래된 연결이 끊긴다. 선언 초당 5회, 연결당 구독 100건
- 문서 명세가 v1.2.19 → v1.2.21로 올라 있음. `docs/brokers/toss.md` 출처 줄은 고쳤다

## 범위
한 항목 = 대화 한 번. 위에서부터 순서대로.

- [x] 1. .env SecretStore — 프로젝트 폴더 `.env`의 `이름=값`을 읽는 `EnvFileSecretStore` (decisions/006). 없는 이름·파일이면 이름만 담은 `IllegalStateException`. `.gitignore`에 `.env` (시험이 지킴), Claude는 `.env`를 열지 못함 (훅)
- [ ] 2. 토스 토큰 — 녹화 응답(공식 명세의 예시로 만든 것)으로: 토큰 발급, 만료 전까지 재사용, 401 `expired-token`·`token-revoked`이면 한 번만 다시 발급. 토큰은 메모리에만. HTTP는 Spring `RestClient` (새 의존성 없음)
- [ ] 3. 토스 어댑터 조회 — `TossBroker`가 `Broker` contract 시험 통과: 계좌 목록(`AccountId`=`toss-`+끝 4자리, `accountSeq`는 어댑터 안에만), 보유(→ `Holding`), 예수금(buying-power KRW·USD → `Money` 둘)
- [ ] 4. 토스 환율 — `ExchangeRates` 구현 (`midRate`). 1분 안에는 다시 묻지 않는다
- [ ] 5. 오류를 알아보게 — 403 IP 차단, 401 키 틀림, 429 한도 초과를 각각 구분되는 예외로. 예외 메시지·로그에 토큰·키·계좌번호가 없음을 시험으로 확인. 통합 잔고 `failedBrokers`에 이유 종류가 남는다
- [ ] 6. dryrun 설정 + networkTest (REST) — `application-dryrun.properties`(토스 어댑터 켜기), `./gradlew networkTest` 작업(`@Tag("network")`만 실행). 시험 내용: 실서버 응답의 필드 모양이 녹화와 같은지 (값은 비교 안 함). 실서버 응답은 git 제외 폴더에 저장
- [ ] 7. 첫 화면 (스냅샷) — Thymeleaf + htmx (decisions/004). 원화 총합, 계좌별 예수금·평가금, 보유 종목 표(종목·수량·현재가·평가금·통화), 환율과 조회 시각, 실패한 증권사 표시. 30초마다 REST로 다시 조회(htmx). htmx는 파일로 넣는다 (외부 CDN 안 씀). fake로 띄워 확인
- [ ] 8. 체결가 받기 — `brokers`에 `PriceStream` 약속 새로 (`Broker`는 그대로). 토스 구현은 JDK 내장 `java.net.http.WebSocket` (새 의존성 없음): 보유 종목 구독, 60초 PING, 끊기면 점점 늦게 재연결, 보유가 바뀌면 구독 배열 다시 보내기. fake 구현은 가짜 체결을 몇 초마다 낸다. 시험: 녹화 프레임(명세 예시)으로 ack·체결·rejected·끊김. networkTest에 "보유 종목 하나 구독 → ack와 체결 프레임 모양 확인" 추가
- [ ] 9. 실시간 평가금 — service가 스냅샷과 체결가를 합친다: 체결가가 온 종목만 `수량 × 체결가`(통화 단위로 HALF_UP 반올림), 나머지는 스냅샷 값. 다음 스냅샷이 오면 스냅샷 값으로 맞춘다. 브라우저로는 SSE(Spring `SseEmitter`) + htmx sse 확장(파일로 넣음). 화면에 "실시간 추정 / 스냅샷 시각" 표시. fake로 띄워 숫자가 움직이는 것 확인
- [ ] 10. 문서 마무리 — `docs/brokers/toss.md`에 새로 안 함정 추가, 사람 확인 결과를 진행 기록에 적고 ROADMAP 갱신

## 하지 않을 것
- 주문 전부 (decisions/005). 토스 주문·조건주문 API, 웹소켓 `personal:order`(주문 이벤트) 채널은 쓰지 않는다
- 웹소켓 호가(`orderbook`) 채널
- 환율 실시간 (1분마다 바뀌므로 REST로 충분)
- 비공식(내부) API — 예수금이 앱과 안 맞을 때 다시 이야기한다 (결정 기록)
- 키움, mock 프로필, 두 번째 증권사로 공통 약속 검증 → 2단계
- 슬랙 알림 → 2단계
- 차트·손익률 화면 (필요해지면 그때)
- 토큰을 파일에 저장 (메모리에만)
- live 모드

## 공개 저장소라서 지킬 것
저장소가 공개다. git에 올라가는 것(코드·녹화·계획서·커밋 메시지)에 다음을 넣지 않는다
- 실제 금액·보유 종목·수량, 계좌번호 끝 4자리, 공인 IP
- dryrun 화면 캡처
- 진행 기록에는 "일치 / 불일치, 차이 %"만 적는다

## 사람이 할 일
- [x] 토스 WTS → 설정 > Open API에서 `client_id`·`client_secret` 발급
- [x] 같은 화면 아래 "허용 IP 관리"에 집 공인 IP 등록. IP가 바뀌면 다시 등록
- [ ] 프로젝트 폴더에 `.env` 만들기 (Claude는 못 엶): `toss.client-id=...`, `toss.client-secret=...` 두 줄. 그 뒤 `chmod 600 .env`
- [ ] 커밋 전 `git status`에 `.env`가 안 보이는지 한 번 확인. 실수로 올라가면 토스 WTS에서 키를 곧바로 재발급
- [ ] 항목 6 뒤: dryrun으로 띄워 `.env` 값으로 토큰이 발급되는지 확인
- [ ] 항목 6·8 뒤: `./gradlew networkTest` 실행 (dryrun 앱을 끈 상태에서 — 토큰이 하나뿐이라 서로 죽인다). 8은 장 중에 (체결이 와야 하므로)
- [ ] 항목 7 뒤: 장 마감 뒤 dryrun으로 띄워 토스 앱과 숫자 비교 (값이 멈춰 있을 때 비교해야 시점 차이가 없다)
- [ ] 항목 9 뒤: 장 중에 dryrun으로 띄워 숫자가 토스 앱과 같이 움직이는지, 토스 앱·WTS를 같이 켜 둬도 연결이 서로 끊기지 않는지
- [ ] 오류 확인: 허용 IP에서 지금 IP를 잠깐 빼고 화면에 "IP 차단"이 뜨는지, 로그에 비밀값이 없는지

## 완료 조건 (실제로 실행해서 보여줄 것)
Claude가 보여줄 것
- `./gradlew test` 전부 통과 출력 (구조 시험 포함)
- fake 모드로 띄운 첫 화면 캡처: 계좌 2개, 원화 총합, 보유 표
- fake 모드에서 가짜 체결에 따라 평가금·총합이 바뀌는 화면 (전후 캡처 또는 GIF)
- fake 모드에서 Host 헤더가 다르면 화면·SSE 모두 403

사람이 확인하고 진행 기록에 적을 것 (숫자는 적지 않고 결과만)
- `./gradlew networkTest` 통과 (REST, 웹소켓)
- 장 마감 뒤 dryrun: 토스 원화 보유 평가금 합계 = 토스 앱 (원 단위까지)
- 장 마감 뒤 dryrun: 달러 종목 원화 환산 = 토스 앱 ±0.5%
- 예수금(원·달러)이 토스 앱의 어느 숫자와 같은지
- 장 중 dryrun: 평가금이 체결에 따라 움직이고, 다음 스냅샷 때 크게 튀지 않는다
- IP 차단 시 화면에 오류 표시, 로그 파일에 키·토큰·전체 계좌번호 없음 (`grep`으로 확인)

## 아직 모르는 것 (dryrun에서 확인)
- `cashBuyingPower`가 앱의 "예수금"과 같은지. 다르면(정산 대기 금액·미체결 주문 때문) 무엇을 보여줄지 다시 정한다
- 원화 매수 가능 금액에 달러가 환산돼 들어가 있는지 (들어가 있으면 합산이 두 번 세어진다)
- `marketValue.amount`가 정확히 `quantity × lastPrice`인지. 아니면 실시간 추정값이 스냅샷 때마다 튄다
- 보유 조회의 `lastPrice`가 NXT 체결도 반영하는지 (아니면 장 밖 시간에 웹소켓 값과 다르다)
- 토스 앱·WTS도 웹소켓 연결 2개 한도를 같이 쓰는지 (같이 쓰면 서로 끊는다)
- 토큰 `expires_in`이 실제로 얼마인지, 토큰이 바뀌면 웹소켓도 다시 연결해야 하는지
- 맥이 잠들었다 깨면 토큰 만료·웹소켓 끊김에서 매끄럽게 돌아오는지

## 진행 기록
- 2026-10-07 항목 1
  - 처음엔 키체인으로 만들었다가(시험 2개 실패 → 구현), 사람이 `.env`로 바꾸기로 해서 지웠다 (decisions/006)
  - `EnvFileSecretStoreTest`(SecretStore contract 상속 + 등호 든 값, 공백·CRLF, 파일 없음)와 `GitIgnoreTest`를 먼저 씀 → 빈 구현으로 6개 실패 확인 → 구현·`.gitignore` 추가 뒤 통과
  - 훅 시험에 `.env` 4가지 추가 → 3개 실패 확인 → 훅 고친 뒤 전부 통과
  - `./gradlew test` 전부 통과
  - `.env.example` 견본은 권한 설정(`Read(./.env.*)`)이 Claude의 쓰기도 막아 만들지 않았다. 형식은 decisions/006에 있다

## 결정 기록
- 2026-10-07 (사람이 정함)
  - 보유 평가금은 `marketValue.amount`(세금·수수료 빼기 전). dryrun에서 앱과 다르면 다시 본다
  - 달러 환산은 `midRate`(매매기준율). dryrun에서 앱과 비교해 0.5%를 넘으면 다시 본다
  - 비밀값 이름은 `<증권사>.<키>` (예: `toss.client-id`). 2단계도 같은 규칙 (`kiwoom.*`, `slack.webhook`)
  - 실서버 응답은 git 제외 폴더에만 둔다. git의 녹화는 공식 명세 예시로 만든 가짜 값만
  - 예수금은 공식 buying-power로 먼저 만들고, dryrun에서 앱과 비교한 뒤 비공식 API를 쓸지 정한다 (약관 위험·세션 쿠키라는 새 비밀값·예고 없이 깨짐)
  - 화면은 웹소켓 체결가로 실시간. 그래서 0단계 결정 "평가금은 증권사가 준 값"을 바꾼다: 스냅샷(30초마다 REST)은 증권사 값, 그 사이는 `수량 × 체결가` 추정. 앱과 맞추는 비교는 장 마감 뒤 스냅샷으로 한다
  - 비밀값은 키체인 대신 프로젝트 폴더의 `.env` (decisions/006). 공개 저장소의 커밋 사고 위험을 알고 정함
  - `.env`는 읽을 때마다 다시 읽는다 (고치면 재시작 없이 반영). 값의 앞뒤 공백은 뗀다
  - `EnvFileSecretStore`를 앱에 연결(빈 등록)하는 일은 dryrun 설정(항목 6)에서 한다. fake는 비밀값이 필요 없다
  - 체결가는 `Broker`가 아니라 새 약속 `PriceStream`으로 받는다. `Broker`는 조회 약속 그대로 두고, 키움이 실시간을 못 주면 억지로 구현하지 않게 (`ExchangeRates`와 같은 이유)
