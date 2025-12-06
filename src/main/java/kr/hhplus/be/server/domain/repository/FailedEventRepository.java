package kr.hhplus.be.server.domain.repository;

import kr.hhplus.be.server.domain.model.FailedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FailedEventRepository extends JpaRepository<FailedEvent, Long> {
    List<FailedEvent> findByStatusAndRetryCountLessThan(
            FailedEvent.FailedEventStatus status,
            Integer maxRetryCount
    );
}

