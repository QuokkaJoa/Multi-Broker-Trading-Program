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
- [x] 2. 토스 토큰 — 녹화 응답(공식 명세의 예시로 만든 것)으로: 토큰 발급, 만료 전까지 재사용, 401 `expired-token`·`token-revoked`이면 한 번만 다시 발급. 토큰은 메모리에만. HTTP는 Spring `RestClient` (새 의존성 없음)
- [x] 3. 토스 어댑터 조회 — `TossBroker`가 `Broker` contract 시험 통과: 계좌 목록(`AccountId`=`toss-`+끝 4자리, `accountSeq`는 어댑터 안에만), 보유(→ `Holding`), 예수금(buying-power KRW·USD → `Money` 둘)
- [x] 4. 토스 환율 — `ExchangeRates` 구현 (`midRate`). 1분 안에는 다시 묻지 않는다
- [x] 5. 오류를 알아보게 — 403 IP 차단, 401 키 틀림, 429 한도 초과를 각각 구분되는 예외로. 예외 메시지·로그에 토큰·키·계좌번호가 없음을 시험으로 확인. 통합 잔고 `failedBrokers`에 이유 종류가 남는다
- [x] 6. dryrun 설정 + networkTest (REST) — `application-dryrun.properties`(토스 어댑터 켜기), `./gradlew networkTest` 작업(`@Tag("network")`만 실행). 시험 내용: 실서버 응답의 필드 모양이 녹화와 같은지 (값은 비교 안 함). 실서버 응답은 git 제외 폴더에 저장
- [x] 7. 첫 화면 (스냅샷) — Thymeleaf + htmx (decisions/004). 원화 총합, 계좌별 예수금·평가금, 보유 종목 표(종목·수량·현재가·평가금·통화), 환율과 조회 시각, 실패한 증권사 표시. 30초마다 REST로 다시 조회(htmx). htmx는 파일로 넣는다 (외부 CDN 안 씀). fake로 띄워 확인
- [x] 8. 체결가 받기 — `brokers`에 `PriceStream` 약속 새로 (`Broker`는 그대로). 토스 구현은 JDK 내장 `java.net.http.WebSocket` (새 의존성 없음): 보유 종목 구독, 60초 PING, 끊기면 점점 늦게 재연결, 보유가 바뀌면 구독 배열 다시 보내기. fake 구현은 가짜 체결을 몇 초마다 낸다. 시험: 녹화 프레임(명세 예시)으로 ack·체결·rejected·끊김. networkTest에 "보유 종목 하나 구독 → ack와 체결 프레임 모양 확인" 추가
- [x] 9. 실시간 평가금 — service가 스냅샷과 체결가를 합친다: 체결가가 온 종목만 `수량 × 체결가`(통화 단위로 HALF_UP 반올림), 나머지는 스냅샷 값. 다음 스냅샷이 오면 스냅샷 값으로 맞춘다. 브라우저로는 SSE(Spring `SseEmitter`) + htmx sse 확장(파일로 넣음). 화면에 "실시간 추정 / 스냅샷 시각" 표시. fake로 띄워 숫자가 움직이는 것 확인
- [x] 10. 문서 마무리 — `docs/brokers/toss.md`에 새로 안 함정 추가, 사람 확인 결과를 진행 기록에 적고 ROADMAP 갱신

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
- [x] 프로젝트 폴더에 `.env` 만들기 (Claude는 못 엶): `toss.client-id=...`, `toss.client-secret=...` 두 줄. 그 뒤 `chmod 600 .env`
- [ ] 커밋 전 `git status`에 `.env`가 안 보이는지 한 번 확인. 실수로 올라가면 토스 WTS에서 키를 곧바로 재발급
- [x] 항목 6 뒤: dryrun으로 띄워 `.env` 값으로 토큰이 발급되는지 확인 (networkTest 통과로 확인)
- [x] 항목 6·8 뒤: `./gradlew networkTest` 실행 (dryrun 앱을 끈 상태에서 — 토큰이 하나뿐이라 서로 죽인다). 8은 장 중에 (체결이 와야 하므로)
- [ ] 항목 7 뒤: 장 마감 뒤 dryrun으로 띄워 토스 앱과 숫자 비교 (값이 멈춰 있을 때 비교해야 시점 차이가 없다)
- [x] 항목 9 뒤: 장 중에 dryrun으로 띄워 숫자가 토스 앱과 같이 움직이는지, 토스 앱·WTS를 같이 켜 둬도 연결이 서로 끊기지 않는지
- [~] (안 함, 사람이 정함 10/08) 오류 확인: 허용 IP에서 지금 IP를 잠깐 빼고 화면에 "IP 차단"이 뜨는지, 로그에 비밀값이 없는지

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
- (안 함, 사람이 정함 10/08) IP 차단 시 화면에 오류 표시, 로그 파일에 키·토큰·전체 계좌번호 없음 (`grep`으로 확인)

## 아직 모르는 것 (dryrun에서 확인)
- `cashBuyingPower`가 앱의 "예수금"과 같은지. 다르면(정산 대기 금액·미체결 주문 때문) 무엇을 보여줄지 다시 정한다 → 10/08: 토스 웹 "총 주문 가능 금액"과 같음
- 원화 매수 가능 금액에 달러가 환산돼 들어가 있는지 (들어가 있으면 합산이 두 번 세어진다) → 10/08: 안 들어가 있음
- `marketValue.amount`가 정확히 `quantity × lastPrice`인지. 아니면 실시간 추정값이 스냅샷 때마다 튄다
- 보유 조회의 `lastPrice`가 NXT 체결도 반영하는지 (아니면 장 밖 시간에 웹소켓 값과 다르다)
- 토스 앱·WTS도 웹소켓 연결 2개 한도를 같이 쓰는지 (같이 쓰면 서로 끊는다) → 10/08: 같이 켜도 끊김 없음
- 토큰 `expires_in`이 실제로 얼마인지 (토큰이 바뀌어도 웹소켓은 다시 연결할 필요 없음 — 명세: 인증은 연결할 때 한 번)
- 맥이 잠들었다 깨면 토큰 만료·웹소켓 끊김에서 매끄럽게 돌아오는지

## 진행 기록
- 2026-10-07 항목 1
  - 처음엔 키체인으로 만들었다가(시험 2개 실패 → 구현), 사람이 `.env`로 바꾸기로 해서 지웠다 (decisions/006)
  - `EnvFileSecretStoreTest`(SecretStore contract 상속 + 등호 든 값, 공백·CRLF, 파일 없음)와 `GitIgnoreTest`를 먼저 씀 → 빈 구현으로 6개 실패 확인 → 구현·`.gitignore` 추가 뒤 통과
  - 훅 시험에 `.env` 4가지 추가 → 3개 실패 확인 → 훅 고친 뒤 전부 통과
  - `./gradlew test` 전부 통과
  - `.env.example` 견본은 권한 설정(`Read(./.env.*)`)이 Claude의 쓰기도 막아 만들지 않았다. 형식은 decisions/006에 있다
- 2026-10-07 항목 2
  - 공식 명세(openapi.json v1.2.21)로 확인: 토큰 응답은 공통 envelope 없이 OAuth2 표준(`access_token`, `expires_in` 86400 예시). API의 401 본문은 `{"error":{"code":...}}`
  - 녹화 4개(토큰, 401 expired-token·token-revoked·invalid-token)를 명세 예시 값 그대로 `src/test/resources/recordings/toss/`에 둠
  - `TossTokensTest` 6개를 먼저 씀 → 빈 구현으로 6개 실패 확인 → `TossTokens` 구현 뒤 통과
  - 만료 1분 전부터 새로 발급. 재발급은 expired-token·token-revoked일 때만 한 번. invalid-token 등 다른 401은 그대로 던짐
  - 두 스레드가 같이 401을 받으면 먼저 온 쪽만 재발급 (토큰이 1개라 겹쳐 받으면 서로 죽임)
  - `./gradlew test` 전부 통과 (53개)
- 2026-10-07 항목 3
  - 녹화 4개(accounts, holdings, buying-power KRW·USD)를 명세 예시 값 그대로 둠
  - `TossBrokerTest`(Broker contract 상속 + 계좌 이름·보유·예수금 변환)를 먼저 씀 → 빈 구현으로 5개 실패 확인 → 구현 뒤 통과
  - 계좌 API 시험은 `X-Tossinvest-Account` 헤더가 있어야만 응답하게 해서 accountSeq 전달도 확인
  - 구조 시험 `어디에도_주문_메서드가_없다`가 `buyingPower`·`cashBuyingPower` 이름(조회 전용)에 걸림 → 사람이 정한 대로 규칙은 두고 이름만 `cashOf`·`cash`로 바꿈 (결정 기록)
  - `./gradlew test` 전부 통과 (60개)
- 2026-10-07 항목 4
  - 공식 명세(openapi.json v1.2.21)로 응답 모양 확인. 녹화 `exchange-rate.json`은 명세 예시 값 그대로
  - `TossExchangeRatesTest` 3개(midRate 사용, 59초 안 재사용, 1분 뒤 재조회)를 먼저 씀 → 빈 구현으로 3개 실패 확인 → `TossExchangeRates` 구현 뒤 통과
  - 응답의 `validUntil`은 쓰지 않고 받은 시각부터 1분 동안 재사용. 앱 연결(빈 등록)은 항목 6에서
  - `./gradlew test` 전부 통과 (63개)
- 2026-10-07 항목 5
  - 공식 문서로 오류 코드 확인: API는 403 `ip-not-allowed`, 429 `rate-limit-exceeded`(+`edge-rate-limit-exceeded`). 토큰 발급은 OAuth2 형식이라 403 `{"error":"access_denied"}`, 키 틀림은 401 `{"error":"invalid_client"}`
  - 녹화 4개 추가. `error-ip-not-allowed.json`은 openapi.json에 예시가 없어 overview.md 오류 표의 코드·문구로 만듦 (README에 적음)
  - `TossErrorsTest` 6개와 `BalanceServiceTest` 1개를 먼저 씀 → 7개 실패 확인 → 구현 뒤 통과
  - 공통 예외 `brokers.BrokerException`(이유: `IP_BLOCKED`·`BAD_KEY`·`RATE_LIMITED`·`OTHER`). 토스 호출은 모두 `TossTokens.withToken`을 지나므로 거기서 한 번만 바꾼다
  - 예외에 원인(`HttpClientErrorException`)을 붙이지 않는다. 원인 메시지에 응답 본문이 통째로 들어가기 때문. 메시지는 이유·상태·코드만
  - `failedBrokers`는 문자열 목록에서 `FailedBroker(broker, reason)` 목록으로 바뀜. 로그는 `TossBroker 조회 실패: IP_BLOCKED (예외 종류)`
  - 그 밖의 401(`invalid-token`, 재발급 뒤에도 `token-revoked` 등)과 다른 403(`forbidden`)은 `OTHER`로 둔다
  - `./gradlew test` 전부 통과 (70개)
- 2026-10-07 항목 6
  - 공식 명세(openapi.json v1.2.21 `servers`)로 주소 확인: `https://openapi.tossinvest.com`
  - `JsonFieldsTest` 3개(값이 달라도 통과, 빠진 필드 알려줌, 실서버 배열이 비면 그 안은 건너뜀)와 `TossConfigTest` 2개를 먼저 씀 → 4개 실패 확인 → 구현 뒤 통과
  - `TossConfig`(@Profile dryrun)가 토큰·어댑터·환율을 등록. 비밀값은 프로젝트 폴더 `.env` (실행 위치 기준 상대 경로라 프로젝트 폴더에서 실행해야 한다)
  - 함정: Spring Boot 4는 `RestClient.Builder` 빈을 별도 모듈(spring-boot-restclient)로 뺐다. 처음 시험이 빌더를 직접 넣어줘서 가려졌다 → 시험에서 빼 실패 확인 → `RestClient.builder()`로 직접 만듦 (새 의존성 없음)
  - `./gradlew networkTest`: `@Tag("network")`만 실행, 매번 다시 돈다. `TossNetworkTest`가 계좌·보유·예수금(원·달러)·환율 응답을 `build/network/toss/`에 저장하고 녹화의 필드가 모두 있는지 본다. 토큰 응답은 저장하지 않는다
  - `application-dryrun.properties`는 주석만 (설정할 값이 없음)
  - Claude는 dryrun·networkTest를 실행하지 못한다 (훅). 사람 확인 남음
  - `./gradlew test` 전부 통과 (75개)
  - 사람 확인: dryrun 앱이 오류 없이 뜸. `./gradlew networkTest` (REST) 5개 통과 → `.env` 값으로 토큰 발급되고, 실서버 응답 모양이 녹화와 같음
  - 남은 항목: 1단계 10개 중 4개 (7·8·9·10)
- 2026-10-07 항목 7
  - 보유 표에 현재가가 필요해 `Holding`에 `price`(증권사가 준 현재가) 추가. 토스는 `lastPrice`, fake는 평가금 ÷ 수량으로 맞춘 값
  - `TossBrokerTest` 현재가 확인과 `PageTest` 3개(총합·계좌·보유·환율·조회 시각, 실패 증권사 "IP 차단", 30초 htmx·내 파일)를 먼저 씀 → 4개 실패 확인 → 구현 뒤 통과
  - `spring-boot-starter-thymeleaf` 추가 (decisions/004). htmx 2.0.11을 unpkg에서 받아 `static/htmx-2.0.11.min.js`로 넣음 (화면은 외부 CDN을 안 부름)
  - 30초 재조회는 `hx-get="/"` + `hx-select="#balance"`: 주소 하나로 첫 화면과 갱신을 같이 쓴다
  - 실패 이유 문구는 화면(템플릿)에서 정한다. 함정: 훅이 Bash 명령 속 `.env` 글자를 막아, 화면 문구는 "비밀값 파일"로 씀
  - fake로 띄워 확인: 계좌 2개, 원화 총합 5,644,016원, 보유 표, 환율·조회 시각. Host가 다르면 화면·htmx 파일 모두 403
  - `./gradlew test` 전부 통과 (78개)
  - 남은 항목: 1단계 10개 중 3개 (8·9·10)
- 2026-10-07 항목 8
  - 공식 명세(asyncapi.json v1.2.2)로 확인: 인증은 연결할 때 한 번이라 연결 중 토큰이 만료돼도 끊기지 않는다. 다시 연결하기 전에 옛 연결을 먼저 닫는다. `rejected`된 항목은 고치기 전엔 다시 선언해도 또 거부된다
  - 녹화 5개(`ws-ack`·`ws-ack-rejected`·`ws-trade`·`ws-server-shutdown`·`ws-pong`)는 명세 예시 프레임 그대로
  - `TossPriceStreamTest` 11개와 `FakePriceStreamTest` 2개를 먼저 씀 → 빈 구현으로 13개 실패 확인 → 구현 뒤 통과. 빈 등록 시험 2개도 실패 확인 → 연결 뒤 통과
  - `brokers.PriceStream`: `watch(보유 목록)`(부를 때마다 구독 전체를 바꿈) + `onTrade(받을 곳)`. 보유의 통화로 시장을 고른다 (KRW → `trade:kr`, USD → `trade:us`)
  - `TossPriceStream`은 연결 방법을 바깥에서 받는다 → 시험은 가짜 소켓에 녹화 프레임을 넣는다 (반으로 나뉜 프레임도 합쳐 읽는지 같이 봄). 실제 연결은 `TossConfig`에서 JDK `HttpClient` 웹소켓
  - 볼 종목이 생겨야 연결한다. 같은 구독이면 다시 보내지 않는다. 거부된 종목은 다음 선언에서 뺀다
  - 재연결 대기: 1초부터 두 배씩, 최대 1분, 0~20% 흔들기. 1분 넘게 붙어 있다 끊긴 경우만 1초로 돌아간다 (연결 2개 한도를 토스 앱과 다툴 때 앱을 1초마다 밀어내지 않게)
  - 웹소켓 연결 401(토큰 죽음)은 따로 재발급하지 않는다. 30초 REST 조회가 같은 `TossTokens`로 새 토큰을 받고, 다음 재연결이 그것을 쓴다
  - 로그에는 HTTP 상태·이유·예외 종류만 (예외 메시지는 안 남김)
  - `FakePriceStream`: 3초마다 보는 종목마다 직전 가격 ±0.5% (원화는 원 단위, 달러는 센트). 아직 아무도 `watch`를 부르지 않아 화면은 그대로 (항목 9에서 연결)
  - networkTest에 `실시간_체결` 추가: 보유 첫 종목 구독 → ack(10초)·체결(2분) 프레임 모양을 녹화와 비교, `build/network/toss/`에 저장. 장 중에 사람이 돌린다
  - `./gradlew test` 전부 통과 (92개)
  - 사람 확인 (22:22): networkTest REST 5개 통과, 웹소켓 연결·ack 모양 통과. 체결은 2분 안에 안 옴 → 국내 종목을 골랐는데 NXT 애프터(20:00)도 끝난 뒤라서. 시험이 달러 종목을 먼저 고르게 바꿈
  - 사람 확인 (다시 실행): networkTest 6개 전부 통과 (REST 5개 + 웹소켓 ack·체결 프레임 모양)
  - 남은 항목: 1단계 10개 중 2개 (9·10)
- 2026-10-07 항목 9
  - `LiveBalanceServiceTest` 7개와 `PageTest` 2개를 먼저 씀(기존 1개는 문구 변경) → 빈 구현으로 10개 실패 확인 → 구현 뒤 통과
  - `service.LiveBalanceService`: 스냅샷이 30초 넘었을 때만 증권사에 다시 묻는다 (화면이 열려 있을 때만 API를 씀). 스냅샷을 받으면 체결가를 버리고 보유 전체를 `watch`
  - 체결가가 온 종목만 `수량 × 체결가` (원은 원 단위, 달러는 센트, HALF_UP). 통화가 다른 체결은 버린다. 같은 종목이 여러 계좌에 있으면 모두 바꾼다. 합계 계산은 `BalanceService.combine`·`AccountBalance.of`로 떼어내 같이 쓴다. 환율은 스냅샷 때 값을 다시 쓴다
  - `/events`(SseEmitter)는 체결이 왔다는 신호(`changed`)만 보낸다. 화면은 `sse:changed throttle:1s`로 같은 주소를 다시 불러 `#balance`만 바꾼다 (화면 그리는 곳이 하나). 장 마감 때를 위해 `every 30s`도 남김
  - htmx sse 확장 2.2.4를 unpkg에서 받아 `static/htmx-ext-sse-2.2.4.min.js`로 넣음. SSE 연결은 바뀌지 않는 바깥 `div`에 둔다 (안 그러면 갱신마다 다시 연결)
  - 함정: SSE는 첫 글자를 쓰기 전엔 응답 머리가 안 나간다 → 연결하자마자 주석 `:connected`를 보냄. 데이터가 빈 이벤트는 브라우저가 버린다 → 시각을 데이터로 넣음
  - 함정: Spring Boot 기본 graceful shutdown이 열린 SSE를 30초 기다린다 (시험이 35초 걸림) → `server.shutdown=immediate` (조회만 하므로 마무리할 일 없음)
  - 화면: "실시간 추정 (체결 HH:mm:ss) · 스냅샷 HH:mm:ss". 체결이 없으면 스냅샷 시각만
  - fake로 띄워 확인: 새로고침 없이 총합·현재가·평가금이 몇 초마다 바뀜, 30초 뒤 스냅샷 시각이 바뀜. Host가 다르면 화면·`/events` 모두 403
  - fake 증권사는 늘 같은 스냅샷을 주므로 fake에서는 30초마다 숫자가 처음 값으로 잠깐 돌아간다 (실서버는 스냅샷도 움직여서 해당 없음)
  - `./gradlew test` 전부 통과 (101개)
  - 남은 항목: 1단계 10개 중 1개 (10)
- 2026-10-08 항목 9 뒤 dryrun (장 중, 미국 정규장 02:00대). Claude가 토스증권 웹과 우리 화면을 2초 간격으로 읽어 비교
  - 실시간 추정: 움직임 (체결 시각이 스냅샷 뒤로 계속 바뀜)
  - 웹소켓: 02:01~02:15 끊김·재연결·오류 로그 0줄. 토스 앱·WTS를 같이 켜 둬도 앱 시세 멈춤 없음 (사람 확인)
  - 국내주식 평가금 합계: 일치 (원 단위까지)
  - 예수금: 원화·달러 모두 토스 웹 "총 주문 가능 금액"의 원화·달러와 일치. 원화 값에 달러가 섞여 있지 않음 (두 번 세지 않음)
  - 원화 총합: 우리가 0.12% 적음. 원인은 환율 하나뿐: 토스 웹은 달러를 약 0.14% 높은 환율로 환산한다 (달러 예수금·해외주식 둘 다 같은 환율로 역산됨). 이 환율은 API `midRate`도 `rate`(매수, midRate + 약 0.5원)도 아니고, 환전 창의 원→달러 환율과도 다르다. 같은 환율을 넣으면 차이는 0.001% 아래(체결 시점 차이)
  - 성공 기준(달러 환산 ±0.5%) 안. 환율은 `midRate` 그대로 둔다
  - 처음 읽은 토스 총액은 구성 합계와 0.25% 달랐다가 몇 초 뒤 합계와 같아짐 → 토스 웹 총액이 잠깐 늦게 갱신된 것으로 봄
- 2026-10-08 항목 10
  - `docs/brokers/toss.md`에 새 함정 6개: 조각 프레임, 저녁엔 국내 체결 없음(NXT 20:00), 앱·WTS와 연결 공존, 예수금 = 웹 "총 주문 가능 금액", 토스 웹 환율 약 0.14% 높음, 토스 웹 총액 갱신 지연. Spring 쪽 함정(RestClient.Builder, SSE 종료 대기)은 토스 것이 아니라 위 진행 기록에만 둔다
  - "아직 모르는 것"에 10/08 dryrun으로 답이 나온 3개 표시
  - ROADMAP에 1단계 남은 사람 확인 2개 적음. 둘이 끝나면 계획서를 plans/done으로 옮긴다
  - 코드 변경 없음. `./gradlew test` 전부 통과
  - 남은 항목: 1단계 10개 중 0개. 남은 사람 확인: 10/10(토) 장 마감 비교(원화 원 단위, 달러 ±0.5%, 다음 스냅샷 튐). 예약해 둠
- 2026-10-08 완료 뒤 리뷰 보강 (코드 리뷰에서 나온 3개)
  - 시험 4개를 먼저 씀 → 4개 실패 확인 → 고친 뒤 통과
  - 웹소켓 연결이 답 없이 걸리면 영원히 다시 연결하지 않던 문제: `TossConfig.opener`에 연결 제한 10초 (`connectTimeout`). 시험은 TCP만 받고 답하지 않는 로컬 서버
  - 잠금을 쥔 채 보내기 완료를 끝없이 기다리던 문제 (화면 요청까지 멈춤): 보내기 제한 5초, 넘으면 끊긴 연결로 보고 다시 연결
  - 환율 조회 실패가 화면 전체 오류(500)가 되던 문제: 증권사 실패처럼 `failedBrokers`에 남기고 달러는 원화 총합에서 뺀다. 체결가로 다시 계산할 때도 같다
  - `./gradlew test` 전부 통과 (105개)
  - 사소한 것 2개: 볼 종목이 없을 때 끊기면 다시 연결하지 않는다 (시험 실패 확인 → 고침). 구독 거부 목록을 안 비우는 것은 명세상 거부 이유가 영구적이라 그대로 두고 `toss.md`에 근거를 적음
  - `./gradlew test` 전부 통과 (106개)

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
- 2026-10-07 항목 3 (사람이 정함)
  - 주문 메서드 금지 구조 시험은 넓게 둔다. 조회 API 이름에 buy·order 등이 있으면 규칙에 예외를 두지 않고 어댑터 쪽 이름을 바꾼다 (JSON 필드는 `@JsonProperty`로 읽음)
  - 토스 계좌는 `BROKERAGE`만 받는다 (명세상 지금은 이것만 지원, 다른 값은 건너뜀)
- 2026-10-07 항목 7 뒤 (사람이 정함)
  - 장 마감 뒤 숫자 비교는 Claude가 한다: dryrun 화면을 띄우고, Claude in Chrome으로 토스증권 웹(사람이 미리 로그인)의 숫자를 읽어 비교. 진행 기록에는 일치/불일치·차이 %만
  - 그래서 훅을 좁게 연다: dryrun `bootRun` 한 모양(한 줄, 로그 파일 돌리기만)만 통과. mock·live·networkTest·다른 dryrun 명령은 그대로 막음 (훅 시험 추가)
  - 비교는 10/10(토) 10:00 한 번. 국내·미국 모두 값이 멈춰 있어 시점 차이가 없다
- 2026-10-08 항목 10 뒤 (사람이 정함)
  - IP 차단 실제 확인(허용 IP에서 빼 보기)은 하지 않는다. 오류 구분·비밀값이 메시지·로그에 없음은 항목 5 시험(`TossErrorsTest`)으로 확인한 것으로 갈음
  - 장 마감 비교는 10/10(토)에 예약해 둠. 결과를 기다리지 않고 계획서를 plans/done으로 옮기고 ROADMAP에 완료 표시 (결과는 이 계획서에 적는다)
