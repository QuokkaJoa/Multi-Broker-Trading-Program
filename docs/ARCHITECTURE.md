# 구조

## 큰 그림
본체는 증권사를 모른다. 증권사마다 통역사(어댑터)가 하나씩 있고, 모두 같은 약속(`Broker` 인터페이스)을 지킨다. 1차의 `Broker`는 조회만 한다.

## 층
web → service → brokers → core
- core: 계좌·보유·통화(KRW/USD)·금액. 다른 층을 모른다
- brokers: `Broker` 약속 + 증권사별 어댑터(toss, kiwoom) + fake. 인증·토큰·호출 한도·허용 IP 오류·응답 모양 차이는 여기서 끝난다. 환율도 여기서 받는다
- service: 통합 잔고(원화 합산), 알림
- web: 화면. 계산·판단 없이 service 결과를 보여주기만 한다

## 어기면 안 되는 경계 (구조 시험이 검사)
- `core`, `service`, `web`은 증권사 어댑터 클래스(toss, kiwoom 패키지)를 참조하지 않는다. 계좌는 증권사를 모르는 `AccountId`로 다룬다
- `web`은 `brokers`를 직접 부르지 않는다
- `Broker` 약속에는 주문 메서드가 없다 (decisions/005)
- 비밀값은 `SecretStore` 하나로만 읽는다. fake·시험은 가짜 SecretStore를 쓴다

## 실행 모드
fake → mock → dryrun 순으로 실제에 가까워진다. Claude는 fake만 실행한다 (`.claude/hooks/block-live.sh`). live는 주문이 생길 때 다시 설계한다.
시작할 때 프로필이 없으면 fake로 뜬다 (`spring.profiles.default=fake`).

## 웹
- `server.address=127.0.0.1`. Spring 기본값은 모든 네트워크라서 반드시 적는다
- Host 헤더가 127.0.0.1·localhost가 아니면 거부한다 (다른 웹사이트가 내 잔고를 읽는 DNS rebinding 방지)

## 시험
- contract: 모든 어댑터가 같은 시험을 통과한다 (fake 포함)
- architecture: 위 경계를 ArchUnit으로 검사한다
- network: 실서버 응답과 녹화 응답 비교. 사람만 실행한다
