---
paths:
  - "src/main/java/**/brokers/**"
  - "src/test/java/**/brokers/**"
---
# 증권사 어댑터 규칙
- 어댑터는 `Broker` 약속만 구현한다. 약속을 바꿔야 할 것 같으면 멈추고 묻는다 (한 증권사 사정으로 약속을 휘게 하지 않는다)
- 인증·토큰 갱신·호출 한도·응답 변환은 어댑터 안에서 끝낸다
- 증권사가 주문 고유번호(clientOrderId 등)를 지원하면 반드시 쓴다
- 응답 녹화는 비밀값(토큰·계좌번호)을 지운 뒤에만 `src/test/resources/recordings/`에 저장한다
- 새로 알게 된 함정은 `docs/brokers/<증권사>.md`에 한 줄로 남긴다
