# 0단계: 뼈대

## 목표
실제 증권사 없이, 가짜 증권사로 "계좌 조회 → 원화 합산 결과"가 끝까지 돈다.

## 범위
- [ ] Gradle 프로젝트, Java·Spring Boot 버전 결정 → decisions/001에 기록
- [ ] core: 계좌(`AccountId`)·보유·통화·금액 (돈은 BigDecimal, 통화를 항상 같이)
- [ ] brokers: Broker 약속(조회만: 계좌 목록, 예수금, 보유, 환율) + FakeBroker 둘 (원화 계좌, 원화+달러 계좌)
- [ ] service: 통합 잔고 — 계좌별 원화·달러 합계, 달러는 환율로 원화 환산
- [ ] contract 시험 (FakeBroker 통과), 구조 시험 (ArchUnit — ARCHITECTURE.md의 경계 전부)
- [ ] SecretStore 약속 + 시험용 가짜 (키체인 연결은 1단계)
- [ ] 실행 모드 틀: fake·mock·dryrun 프로필, 기본값 fake, `server.address=127.0.0.1`
- [ ] Gradle 시험 출력은 실패한 시험만 자세히 보이게 설정
- [ ] 훅 확인: Claude가 fake 아닌 프로필이나 networkTest를 실행하면 막힌다

## 하지 않음
화면, 실제 증권사 연결, 슬랙, 주문 전부

## 사람이 확인할 것
- 키움 포털에서 ISA 계좌 REST 조회 지원 여부, 모의투자 ISA 여부
- 집 공인 IP가 고정인지

## 진행 기록

## 결정 기록
