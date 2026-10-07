package tradinghub.core;

/**
 * 비밀값(앱키·시크릿·계좌번호·슬랙 주소)을 읽는 유일한 통로.
 * 실제 구현은 맥 키체인 (1단계). 값은 출력·로그·예외 메시지에 넣지 않는다.
 */
public interface SecretStore {

    /** 없으면 IllegalStateException. */
    String get(String name);
}
