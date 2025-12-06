package kr.hhplus.be.server.application.event;
import kr.hhplus.be.server.domain.event.ReservationCompletedEvent;
import kr.hhplus.be.server.infrastructure.client.DataPlatformClient;
import kr.hhplus.be.server.infrastructure.client.DataPlatformPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 데이터 플랫폼 이벤트 리스너
 *
 * 비동기로 데이터 플랫폼에 예약 정보 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlatformEventListener implements ReservationEventListener {

    private final DataPlatformClient dataPlatformClient;

    @Override
    @Async("eventAsyncExecutor")
    public void onReservationCompleted(ReservationCompletedEvent event) {
        try {
            log.info("[데이터 플랫폼] 예약 정보 전송 시작 - reservationId: {}",
                    event.getReservationId());

            DataPlatformPayload payload = DataPlatformPayload.from(event);
            dataPlatformClient.sendReservationData(payload);

            log.info("[데이터 플랫폼] 예약 정보 전송 성공 - reservationId: {}",
                    event.getReservationId());

        } catch (Exception e) {
            log.error("[데이터 플랫폼] 예약 정보 전송 실패 - reservationId: {}, error: {}",
                    event.getReservationId(), e.getMessage(), e);
        }
    }
}