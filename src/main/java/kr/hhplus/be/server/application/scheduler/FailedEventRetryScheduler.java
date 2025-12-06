package kr.hhplus.be.server.application.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.event.ReservationCompletedEvent;
import kr.hhplus.be.server.domain.model.FailedEvent;
import kr.hhplus.be.server.domain.repository.FailedEventRepository;
import kr.hhplus.be.server.infrastructure.client.DataPlatformClient;
import kr.hhplus.be.server.infrastructure.client.DataPlatformPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailedEventRetryScheduler {

    private final FailedEventRepository failedEventRepository;
    private final DataPlatformClient dataPlatformClient;
    private final ObjectMapper objectMapper;

    // 5분마다 실행
    @Scheduled(fixedDelay = 300000)
    public void retryFailedEvents() {
        // 재시도 10회 미만인 PENDING 상태 이벤트 조회
        List<FailedEvent> events = failedEventRepository.findByStatusAndRetryCountLessThan(
                FailedEvent.FailedEventStatus.PENDING_RETRY, 10
        );

        if (events.isEmpty()) return;

        log.info("[RetryScheduler] 재시도 대상 {}건 발견", events.size());

        for (FailedEvent failedEvent : events) {
            processRetry(failedEvent);
        }
    }

    // 개별 트랜잭션으로 처리 (하나 실패해도 나머지는 진행)
    @Transactional
    public void processRetry(FailedEvent failedEvent) {
        try {
            ReservationCompletedEvent event = objectMapper.readValue(
                    failedEvent.getPayload(), ReservationCompletedEvent.class
            );

            // 재전송 시도
            dataPlatformClient.sendReservationData(DataPlatformPayload.from(event));

            // 성공 처리
            failedEvent.markAsResolved();
            log.info("[RetryScheduler] 재시도 성공 - id: {}", failedEvent.getId());

        } catch (Exception e) {
            // 실패 처리: 횟수 증가
            failedEvent.incrementRetryCount();
            log.warn("[RetryScheduler] 재시도 실패 - id: {}, count: {}", failedEvent.getId(), failedEvent.getRetryCount());

            // 10회 초과 시 영구 실패 처리
            if (failedEvent.getRetryCount() >= 10) {
                failedEvent.markAsFailed();
                log.error("[RetryScheduler] 영구 실패 처리 - id: {}", failedEvent.getId());
            }
        }
    }
}
