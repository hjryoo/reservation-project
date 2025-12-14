package kr.hhplus.be.server.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.model.ReservationHistory;
import kr.hhplus.be.server.domain.repository.ReservationHistoryRepository;
import kr.hhplus.be.server.infrastructure.client.DataPlatformPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationDataConsumer {

    private final ReservationHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "concert.reservation.completed",
            groupId = "hhplus-reservation-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void listen(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String reservationIdKey = record.key();
        String jsonPayload = record.value();

        log.info("[Kafka Consumer] 메시지 수신 - key: {}, offset: {}", reservationIdKey, record.offset());

        try {
            DataPlatformPayload payload = objectMapper.readValue(jsonPayload, DataPlatformPayload.class);
            Long reservationId = payload.reservationId();

            // 멱등성 체크
            if (historyRepository.existsByReservationId(reservationId)) {
                log.warn("[Kafka Consumer] 중복 메시지 - reservationId: {}. Skip.", reservationId);
                acknowledgment.acknowledge();
                return;
            }

            // DB 저장
            ReservationHistory history = new ReservationHistory(
                    payload.reservationId(),
                    payload.userId(),
                    payload.concertId(),
                    payload.amount(),
                    payload.completedAt()
            );
            historyRepository.save(history);

            log.info("[Kafka Consumer] DB 저장 완료 - reservationId: {}", reservationId);

            // 수동 커밋
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("[Kafka Consumer] 처리 실패 - key: {}", reservationIdKey, e);
            throw new RuntimeException("Consumer Failed", e);
        }
    }
}