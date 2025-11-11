package kr.hhplus.be.server.config;

import kr.hhplus.be.server.application.event.DataPlatformEventListener;
import kr.hhplus.be.server.application.event.ReservationEventListener;
import kr.hhplus.be.server.application.event.ReservationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

import java.util.List;

/**
 * 이벤트 리스너 등록 설정
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class EventConfig {

    private final ReservationEventPublisher eventPublisher;
    private final List<ReservationEventListener> listeners;  // 구체 타입 대신 인터페이스 리스트

    @PostConstruct
    public void registerListeners() {
        for (ReservationEventListener listener : listeners) {
            eventPublisher.subscribe(listener);
            log.info("이벤트 리스너 등록 완료 - {}", listener.getClass().getSimpleName());
        }
    }
}