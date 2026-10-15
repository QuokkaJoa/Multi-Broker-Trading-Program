package tradinghub.service;

/** 알림 약속. 보내기만 한다 (슬랙에서 명령 받기는 하지 않는다, 2단계 계획서). 내용에 금액·계좌·키를 넣지 않는다. */
public interface Notifier {

    /** 실패해도 예외를 던지지 않는다 (알림 때문에 조회가 멈추지 않게). */
    void send(String text);
}
