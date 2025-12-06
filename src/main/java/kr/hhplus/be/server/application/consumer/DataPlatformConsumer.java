package kr.hhplus.be.server.application.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DataPlatformConsumer {

    @KafkaListener(topics = "concert.reservation.completed", groupId = "data-platform-group")
    public void listen(String message) {
        log.info("=========================================================");
        log.info("[Kafka Consumer] 데이터 플랫폼에서 메시지 수신 성공!");
        log.info("Message: {}", message);
        log.info("=========================================================");
    }
}
