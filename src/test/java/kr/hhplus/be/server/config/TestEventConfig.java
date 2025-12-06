package kr.hhplus.be.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.application.event.DataPlatformEventListener;
import kr.hhplus.be.server.application.event.ReservationEventPublisher;
import kr.hhplus.be.server.domain.repository.FailedEventRepository;
import kr.hhplus.be.server.infrastructure.client.DataPlatformClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

@TestConfiguration
public class TestEventConfig {

    @Bean
    @Primary
    public ReservationEventPublisher testReservationEventPublisher() {
        return new ReservationEventPublisher();
    }

    @Bean
    @Primary
    public DataPlatformClient testDataPlatformClient() {
        return Mockito.mock(DataPlatformClient.class);
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, String> testKafkaTemplate() {
        // Mockito.mock(KafkaTemplate.class)가 raw type을 반환하므로 경고가 발생했던 지점입니다.
        KafkaTemplate<String, String> mockTemplate = Mockito.mock(KafkaTemplate.class);

        // send 메서드 호출 시 null 대신 CompletableFuture 반환 (NPE 방지)
        Mockito.when(mockTemplate.send(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        return mockTemplate;
    }

    @Bean
    @Primary
    public FailedEventRepository testFailedEventRepository() {
        return Mockito.mock(FailedEventRepository.class);
    }

    @Bean
    @Primary
    public ObjectMapper testObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @Primary
    public DataPlatformEventListener testDataPlatformEventListener(
            KafkaTemplate<String, String> kafkaTemplate,
            FailedEventRepository failedEventRepository,
            ObjectMapper objectMapper) {

        return new DataPlatformEventListener(kafkaTemplate, failedEventRepository, objectMapper);
    }
}