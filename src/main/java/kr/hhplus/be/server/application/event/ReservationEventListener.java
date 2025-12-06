package kr.hhplus.be.server.application.event;

import kr.hhplus.be.server.domain.event.ReservationCompletedEvent;

/**
 * 예약 이벤트 리스너 인터페이스
 */
public interface ReservationEventListener {

    /**
     * 예약 완료 이벤트 처리
     */
    void onReservationCompleted(ReservationCompletedEvent event);
}
