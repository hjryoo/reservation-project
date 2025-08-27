package kr.hhplus.be.server.repository;

import kr.hhplus.be.server.domain.QueueStatus;
import kr.hhplus.be.server.domain.QueueToken;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class InMemoryQueueRepository implements QueueRepository {
    private final Map<Long, QueueToken> tokenStorage = new ConcurrentHashMap<>();
    private final Map<String, Long> tokenIndex = new ConcurrentHashMap<>(); // token -> id 매핑
    private final Map<String, Long> userIndex = new ConcurrentHashMap<>(); // userId -> id 매핑
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public QueueToken save(QueueToken queueToken) {
        if (queueToken.getId() == null) {
            // 새로운 토큰 생성
            Long id = idGenerator.getAndIncrement();
            queueToken.setId(id);
        }

        tokenStorage.put(queueToken.getId(), queueToken);
        tokenIndex.put(queueToken.getToken(), queueToken.getId());
        userIndex.put(queueToken.getUserId(), queueToken.getId());

        return queueToken;
    }

    @Override
    public Optional<QueueToken> findByToken(String token) {
        Long id = tokenIndex.get(token);
        return id != null ? Optional.ofNullable(tokenStorage.get(id)) : Optional.empty();
    }

    @Override
    public Optional<QueueToken> findByUserId(String userId) {
        Long id = userIndex.get(userId);
        return id != null ? Optional.ofNullable(tokenStorage.get(id)) : Optional.empty();
    }

    @Override
    public List<QueueToken> findByStatusOrderByCreatedAt(QueueStatus status) {
        return tokenStorage.values().stream()
                .filter(token -> token.getStatus() == status)
                .sorted(Comparator.comparing(QueueToken::getCreatedAt))
                .collect(Collectors.toList());
    }

    @Override
    public long countByStatus(QueueStatus status) {
        return tokenStorage.values().stream()
                .filter(token -> token.getStatus() == status)
                .count();
    }

    @Override
    public long countByStatusAndCreatedAtBefore(QueueStatus status, LocalDateTime createdAt) {
        return tokenStorage.values().stream()
                .filter(token -> token.getStatus() == status &&
                        token.getCreatedAt().isBefore(createdAt))
                .count();
    }

    @Override
    public List<QueueToken> findExpiredTokens() {
        return tokenStorage.values().stream()
                .filter(QueueToken::isExpired)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(QueueToken queueToken) {
        tokenStorage.remove(queueToken.getId());
        tokenIndex.remove(queueToken.getToken());
        userIndex.remove(queueToken.getUserId());
    }
}
