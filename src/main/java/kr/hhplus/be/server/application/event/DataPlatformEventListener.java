package kr.hhplus.be.server.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.event.ReservationCompletedEvent;
import kr.hhplus.be.server.domain.model.FailedEvent;
import kr.hhplus.be.server.domain.repository.FailedEventRepository;
import kr.hhplus.be.server.infrastructure.client.DataPlatformPayload;
import kr.hhplus.be.server.infrastructure.monitoring.KafkaMetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlatformEventListener {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final FailedEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;
    private final KafkaMetricsCollector metricsCollector;

    private static final String TOPIC_RESERVATION = "concert.reservation.completed";

    // 기존 Retryable/Recover는 Kafka 내부 재시도 설정 및 Async Callback으로 대체 가능하므로 제거하거나
    // 비즈니스 로직 레벨의 재시도가 필요하면 유지합니다. 여기서는 Kafka 전송 자체의 비동기 처리에 집중합니다.
    @Async("eventAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReservationCompleted(ReservationCompletedEvent event) {
        try {
            log.info("[Kafka Producer] 예약 정보 발행 시작 - reservationId: {}", event.getReservationId());

            DataPlatformPayload payload = DataPlatformPayload.from(event);
            String jsonPayload = objectMapper.writeValueAsString(payload);
            String key = String.valueOf(event.getReservationId()); // 순서 보장을 위한 Key

            // CompletableFuture 반환 (Spring Kafka 3.x 이상)
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(TOPIC_RESERVATION, key, jsonPayload);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    // 성공
                    log.info("[Kafka Producer] 발행 성공 - topic: {}, partition: {}, offset: {}",
                            TOPIC_RESERVATION,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    metricsCollector.recordPublishSuccess(TOPIC_RESERVATION);
                } else {
                    // 실패
                    log.error("[Kafka Producer] 발행 실패 - reservationId: {}", event.getReservationId(), ex);
                    metricsCollector.recordPublishFailure(TOPIC_RESERVATION);
                    saveFailedEvent(event, jsonPayload, ex.getMessage());
                }
            });

        } catch (JsonProcessingException e) {
            log.error("[Kafka Producer] JSON 변환 오류 - reservationId: {}", event.getReservationId(), e);
            saveFailedEvent(event, "JSON_ERROR", e.getMessage());
        } catch (Exception e) {
            log.error("[Kafka Producer] 시스템 오류 - reservationId: {}", event.getReservationId(), e);
            saveFailedEvent(event, "SYSTEM_ERROR", e.getMessage());
        }
    }

    private void saveFailedEvent(ReservationCompletedEvent event, String payload, String errorMsg) {
        try {
            FailedEvent failedEvent = FailedEvent.create(
                    "ReservationCompleted",
                    event.getReservationId(),
                    payload,
                    errorMsg
            );
            failedEventRepository.save(failedEvent);
            log.info("[FailedEvent] DB 백업 완료 - reservationId: {}", event.getReservationId());
        } catch (Exception e) {
            log.error("[CRITICAL] DB 백업 실패 - reservationId: {}", event.getReservationId(), e);
        }
    }
}