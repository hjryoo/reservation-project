package kr.hhplus.be.server.config;

import kr.hhplus.be.server.application.event.DataPlatformEventListener;
import kr.hhplus.be.server.application.event.ReservationEventPublisher;
import kr.hhplus.be.server.infrastructure.client.ApiResponse;
import kr.hhplus.be.server.infrastructure.client.DataPlatformClient;
import kr.hhplus.be.server.infrastructure.client.DataPlatformPayload;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트용 이벤트 설정
 */
import kr.hhplus.be.server.application.event.DataPlatformEventListener;
import kr.hhplus.be.server.application.event.ReservationEventPublisher;
import kr.hhplus.be.server.infrastructure.client.DataPlatformClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트용 이벤트 설정
 */
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
        DataPlatformClient mock = Mockito.mock(DataPlatformClient.class);
        Mockito.doNothing().when(mock).sendReservationData(Mockito.any());
        return mock;
    }

    @Bean
    @Primary
    public DataPlatformEventListener testDataPlatformEventListener(
            DataPlatformClient dataPlatformClient) {
        return new DataPlatformEventListener(dataPlatformClient);
    }
}