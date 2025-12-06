package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.application.event.DataPlatformEventListener;
import kr.hhplus.be.server.domain.event.ReservationCompletedEvent;
import kr.hhplus.be.server.domain.repository.FailedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean; // [변경]

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = { "listeners=PLAINTEXT://localhost:9092", "port=9092" },
        topics = { "concert.reservation.completed" }
)
class KafkaIntegrationTest {

    @Autowired
    private DataPlatformEventListener dataPlatformEventListener;

    @MockitoBean // [변경] Spring Boot 3.4 대응
    private FailedEventRepository failedEventRepository;

    @Test
    @DisplayName("Kafka 연동 테스트 - 예약 완료 이벤트가 발생하면 Kafka로 메시지가 발행되어야 한다")
    void shouldPublishMessageToKafka() throws InterruptedException {
        // given
        // ReservationCompletedEvent 클래스에 @Builder가 추가되어야 동작함
        ReservationCompletedEvent event = ReservationCompletedEvent.builder()
                .reservationId(12345L)
                .concertId(1L)
                .userId(100L)
                .seatNumber(50)
                .amount(99000L)
                .concertTitle("아이유 콘서트")
                .transactionId("tx-12345")
                .completedAt(LocalDateTime.now())
                .build();

        // when
        dataPlatformEventListener.handleReservationCompleted(event);

        // then
        Thread.sleep(2000);

        // Recover 로직(save)이 실행되지 않았으면 성공
        verify(failedEventRepository, timeout(100).times(0)).save(any());
    }
}