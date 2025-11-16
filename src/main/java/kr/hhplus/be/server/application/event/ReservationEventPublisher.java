package kr.hhplus.be.server.application.event;

import kr.hhplus.be.server.domain.event.ReservationCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 예약 이벤트 발행자
 *
 * 역할:
 * 1. 리스너 등록 및 관리
 * 2. 이벤트 발행 및 전파
 */
@Slf4j
@Component
public class ReservationEventPublisher {

    private final Set<ReservationEventListener> listeners =
            Collections.synchronizedSet(new HashSet<>());

    /**
     * 리스너 등록
     */
    public void subscribe(ReservationEventListener listener) {
        listeners.add(listener);
        log.debug("리스너 등록 - listener: {}", listener.getClass().getSimpleName());
    }

    /**
     * 리스너 해제
     */
    public void unsubscribe(ReservationEventListener listener) {
        listeners.remove(listener);
        log.debug("리스너 해제 - listener: {}", listener.getClass().getSimpleName());
    }

    /**
     * 이벤트 발행
     *
     * 모든 등록된 리스너에게 이벤트 전파
     * 한 리스너의 실패가 다른 리스너에 영향을 주지 않음
     */
    public void publish(ReservationCompletedEvent event) {
        log.info("예약 완료 이벤트 발행 - reservationId: {}, 리스너 수: {}",
                event.getReservationId(), listeners.size());

        for (ReservationEventListener listener : listeners) {
            try {
                listener.onReservationCompleted(event);
            } catch (Exception e) {
                log.error("리스너 실행 중 예외 발생 - listener: {}, reservationId: {}",
                        listener.getClass().getSimpleName(), event.getReservationId(), e);
            }
        }
    }
}
