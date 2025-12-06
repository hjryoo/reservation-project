package kr.hhplus.be.server.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.event.ReservationCompletedEvent;
import kr.hhplus.be.server.domain.model.FailedEvent;
import kr.hhplus.be.server.domain.repository.FailedEventRepository;
import kr.hhplus.be.server.infrastructure.client.DataPlatformClient;
import kr.hhplus.be.server.infrastructure.client.DataPlatformPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 데이터 플랫폼 이벤트 리스너
 *
 * 비동기로 데이터 플랫폼에 예약 정보 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlatformEventListener {

    private final DataPlatformClient dataPlatformClient;
    private final FailedEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;

    @Async("eventAsyncExecutor") // 별도 스레드에서 실행
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(
            retryFor = { Exception.class }, // 모든 예외에 대해 재시도 (필요시 구체화)
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void handleReservationCompleted(ReservationCompletedEvent event) {
        log.info("[DataPlatform] 전송 시작 - reservationId: {}", event.getReservationId());

        DataPlatformPayload payload = DataPlatformPayload.from(event);
        dataPlatformClient.sendReservationData(payload);

        log.info("[DataPlatform] 전송 성공 - reservationId: {}", event.getReservationId());
    }

    @Recover
    public void recover(Exception e, ReservationCompletedEvent event) {
        log.error("[DataPlatform] 3회 재시도 실패 - reservationId: {}, error: {}",
                event.getReservationId(), e.getMessage());

        try {
            String payloadJson = objectMapper.writeValueAsString(event);

            FailedEvent failedEvent = FailedEvent.create(
                    "ReservationCompleted",
                    event.getReservationId(),
                    payloadJson,
                    e.getMessage()
            );

            failedEventRepository.save(failedEvent);
            log.info("[FailedEvent] 실패 이벤트 저장 완료 - reservationId: {}", event.getReservationId());

        } catch (Exception saveEx) {
            log.error("[CRITICAL] 실패 이벤트 저장마저 실패 - reservationId: {}", event.getReservationId(), saveEx);
        }
    }
}