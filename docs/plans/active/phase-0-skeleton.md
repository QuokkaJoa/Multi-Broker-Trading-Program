# 0단계: 뼈대

## 목표
실제 증권사 없이, 가짜 증권사로 "계좌 조회 → 원화 합산 결과"가 끝까지 돈다.

## 범위
- [x] Gradle 프로젝트, Java·Spring Boot 버전 결정 → decisions/001에 기록
- [x] core: 계좌(`AccountId`)·보유·통화·금액 (돈은 BigDecimal, 통화를 항상 같이)
- [x] brokers: Broker 약속(조회만: 계좌 목록, 예수금, 보유, 환율) + FakeBroker 둘 (원화 계좌, 원화+달러 계좌)
- [x] service: 통합 잔고 — 계좌별 원화·달러 합계, 달러는 환율로 원화 환산
- [x] contract 시험 (FakeBroker 통과), 구조 시험 (ArchUnit — ARCHITECTURE.md의 경계 전부)
- [x] SecretStore 약속 + 시험용 가짜 (키체인 연결은 1단계)
- [x] 실행 모드 틀: fake 프로필, 기본값 fake, `server.address=127.0.0.1` (mock·dryrun 설정은 1단계로 미룸, 아래 진행 기록)
- [x] Gradle 시험 출력은 실패한 시험만 자세히 보이게 설정
- [x] 훅 확인: Claude가 fake 아닌 프로필이나 networkTest를 실행하면 막힌다

## 하지 않음
화면, 실제 증권사 연결, 슬랙, 주문 전부

## 사람이 확인할 것
- 키움 포털에서 ISA 계좌 REST 조회 지원 여부, 모의투자 ISA 여부
- 집 공인 IP가 고정인지
- 훅의 빈틈 두 가지 (막을지 결정 필요)
  - `application.properties`의 `spring.profiles.default`를 바꾼 뒤 `./gradlew bootRun`을 하면 못 막는다 (명령어 글자만 보기 때문)
  - 반대로 명령어 안에 해당 단어가 글자로만 있어도 막는다 (예: 이 문서를 고치는 셸 명령)

## 진행 기록
- 2026-10-07: 기능마다 시험을 먼저 써서 실패를 보고(red) 구현해 통과(green)시켰다
  - Money 3개 실패 → 통과
  - Broker contract 2개 실패(빈 가짜) → 통과
  - 통합 잔고 3개 실패 → 통과
  - SecretStore contract 2개 실패 → 통과
  - 앱 기동(기본 프로필·주소·연결) 3개 + Host 거부 3개 실패 → 통과
  - 구조 시험: 일부러 어긴 임시 코드(service→FakeBroker, web→Broker, Broker 구현에 `buy()`, `System.getenv`)로 4개 모두 실패 확인 → 임시 코드 지우고 통과
- `./gradlew test` 31개 전부 통과
- fake 실행 확인: `GET /api/balance`가 계좌 2개, 원화 합계 5,644,016원을 돌려줌. Host가 `evil.example`이면 403, 다른 네트워크 주소(en0)로는 연결 안 됨
- 훅 확인: `--spring.profiles.active=live`, `SPRING_PROFILES_ACTIVE=dryrun`, `./gradlew networkTest` 모두 차단됨
- 미룸: `networkTest` 작업과 mock·dryrun 설정 파일은 1단계에서 실제 어댑터와 함께 만든다. 지금 mock·dryrun으로 띄우면 증권사 빈이 없어 시작에 실패한다

## 결정 기록
- 환율은 `Broker`가 아니라 따로 `ExchangeRates` 약속으로 뺐다. 토스만 환율을 주기 때문에 키움 어댑터가 억지로 구현하지 않게 하려고
- `FakeBroker` 하나에 계좌 하나. 가짜 둘은 같은 클래스에 데이터만 다르다 (`krwOnly`, `krwAndUsd`)
- `AccountId`는 계좌번호가 아니라 이름이다 (예: `toss-1234`). 실제 번호는 어댑터 안에만 둔다. 숫자가 4개를 넘으면 `AccountId`를 만들 때 거부한다 (하이픈으로 끊어도 전체 개수를 센다)
- 보유 평가금은 직접 계산하지 않고 증권사가 준 값을 쓴다 (앱 숫자와 원 단위까지 맞추려고)
- 달러 합계는 모든 계좌를 더한 뒤 한 번만 환산하고, 원 단위로 반올림(HALF_UP)한다. 계좌마다 반올림하면 오차가 쌓인다
- `SecretStore`는 core에 둔다 (brokers와 나중의 슬랙 알림(service)이 둘 다 쓰므로). 시험용 가짜는 test 쪽에만 있다
- 화면은 아직 없지만 결과를 실행해 보이려고 web에 JSON 하나(`GET /api/balance`)만 두었다. 그래서 Host 검사 필터도 지금 넣었다
- 구조 시험에 "System.getenv 금지"를 넣었다. 비밀값이 환경변수로 새는 길을 막는다
- 리뷰 뒤 보강 (2026-10-07)
  - 구조 시험: 주문 이름 메서드를 `Broker` 구현만이 아니라 tradinghub 전체에서 막는다. 비밀값 규칙에 `System.getProperty`·`Environment`·`@Value`·`@ConfigurationProperties`도 넣었다. 일부러 어긴 임시 코드(도우미 클래스의 `placeOrder()`, 생성자 매개변수의 `@Value`, `Environment` 필드, `System.getProperty`)로 4개 모두 잡히는 것을 확인하고 지웠다
  - `Money`는 자릿수가 달라도 같은 금액이면 같다 (`1000.5` = `1000.50`)
  - 통합 잔고는 증권사 하나가 실패해도 나머지를 보여주고 `failedBrokers`에 이름을 남긴다. 로그에는 예외 종류만 남긴다 (메시지에 비밀값이 섞일 수 있어서)
  - 환율은 달러 자산이 있을 때만 묻는다. `ExchangeRates`는 없어도 앱이 뜬다. 달러가 있는데 환율 출처가 없으면 예외
  - Host 검사는 `getServerName()`이 아니라 Host 헤더를 직접 본다. 헤더가 없으면 403
