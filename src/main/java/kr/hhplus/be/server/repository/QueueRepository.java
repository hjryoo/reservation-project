package kr.hhplus.be.server.repository;

import kr.hhplus.be.server.domain.QueueStatus;
import kr.hhplus.be.server.domain.QueueToken;

import java.util.List;
import java.util.Optional;

public interface QueueRepository
{
    QueueToken save(QueueToken queueToken);
    Optional<QueueToken> findByToken(String token);
    Optional<QueueToken> findByUserId(String userId);
    List<QueueToken> findByStatusOrderByCreatedAt(QueueStatus status);
    long countByStatus(QueueStatus status);
    long countByStatusAndCreatedAtBefore(QueueStatus status, java.time.LocalDateTime createdAt);
    List<QueueToken> findExpiredTokens();
    void delete(QueueToken queueToken);
}
