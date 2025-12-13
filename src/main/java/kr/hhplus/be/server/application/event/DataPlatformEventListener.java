package kr.hhplus.be.server.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.event.ReservationCompletedEvent;
import kr.hhplus.be.server.domain.model.FailedEvent;
import kr.hhplus.be.server.domain.repository.FailedEventRepository;
import kr.hhplus.be.server.infrastructure.client.DataPlatformPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlatformEventListener {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final FailedEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;

    // 토픽 이름 상수 정의
    private static final String TOPIC_RESERVATION = "concert.reservation.completed";

    @Async("eventAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(
            retryFor = { Exception.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void handleReservationCompleted(ReservationCompletedEvent event) {
        try {
            log.info("[Kafka Producer] 예약 정보 발행 시작 - reservationId: {}", event.getReservationId());

            // 1. Payload 생성 (기존 로직 활용)
            DataPlatformPayload payload = DataPlatformPayload.from(event);

            // 2. JSON 변환
            String jsonPayload = objectMapper.writeValueAsString(payload);

            // 3. Kafka 발행 (Key: reservationId, Value: JSON)
            // Key를 지정해야 동일 예약에 대한 이벤트가 동일 파티션으로 가서 순서가 보장됨
            kafkaTemplate.send(TOPIC_RESERVATION, String.valueOf(event.getReservationId()), jsonPayload);

            log.info("[Kafka Producer] 발행 성공 - topic: {}, reservationId: {}", TOPIC_RESERVATION, event.getReservationId());

        } catch (JsonProcessingException e) {
            log.error("[Kafka Producer] JSON 변환 오류 - reservationId: {}", event.getReservationId(), e);
            throw new RuntimeException("JSON conversion failed", e);
        } catch (Exception e) {
            log.error("[Kafka Producer] 발행 실패 - reservationId: {}", event.getReservationId(), e);
            throw e; // Retryable 발동을 위해 예외 던짐
        }
    }

    // 3회 실패 시 DB에 저장 (Fallback)
    @Recover
    public void recover(Exception e, ReservationCompletedEvent event) {
        log.error("[Kafka Producer] 최종 전송 실패 (Recover) - reservationId: {}", event.getReservationId());

        try {
            String payloadJson = objectMapper.writeValueAsString(DataPlatformPayload.from(event));

            FailedEvent failedEvent = FailedEvent.create(
                    "ReservationCompleted",
                    event.getReservationId(),
                    payloadJson,
                    e.getMessage()
            );

            failedEventRepository.save(failedEvent);
            log.info("[FailedEvent] DB 백업 완료 - reservationId: {}", event.getReservationId());

        } catch (Exception saveEx) {
            log.error("[CRITICAL] DB 백업 실패 - reservationId: {}", event.getReservationId(), saveEx);
        }
    }
}