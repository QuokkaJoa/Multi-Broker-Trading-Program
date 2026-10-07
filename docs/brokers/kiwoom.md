# 키움증권
출처: https://openapi.kiwoom.com/intro/serviceInfo , https://github.com/Kiwoom-Securities/Kiwoom-REST-API (2026-10-07 확인)

- 앱키 발급 전에 허용 IP를 등록해야 한다 (최대 10개). 그 밖의 IP에서는 인증이 안 된다
- 접근 토큰 유효기간 24시간
- 실전과 모의투자의 앱키가 따로다. 모의투자는 따로 신청한다
- 실전·모의 각각 계좌를 20개까지 추가 등록할 수 있다. 계좌(위탁/ISA 등)와 HTS ID 연결 필요
- 모름: ISA 계좌의 REST 조회 지원 여부, 모의투자에 ISA가 있는지, 호출 한도, 주문 중복 방지 키
