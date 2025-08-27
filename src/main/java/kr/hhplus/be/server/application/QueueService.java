package kr.hhplus.be.server.application;

import kr.hhplus.be.server.domain.QueueStatus;
import kr.hhplus.be.server.domain.QueueToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import kr.hhplus.be.server.repository.QueueRepository;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class QueueService {

    private final QueueRepository queueRepository;
    private static final int MAX_ACTIVE_USERS = 100; // 최대 동시 활성 사용자 수

    public QueueService(QueueRepository queueRepository) {
        this.queueRepository = queueRepository;
    }

    /**
     * 대기열 토큰 발급
     */
    public QueueToken issueToken(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }

        // 기존 토큰이 있는지 확인
        Optional<QueueToken> existingToken = queueRepository.findByUserId(userId);
        if (existingToken.isPresent()) {
            QueueToken token = existingToken.get();
            if (!token.isExpired()) {
                // 기존 토큰이 유효하면 위치 업데이트 후 반환
                updateTokenPosition(token);
                return token;
            } else {
                // 만료된 토큰 삭제
                queueRepository.delete(token);
            }
        }

        // 현재 활성 사용자 수 확인
        long activeUserCount = queueRepository.countByStatus(QueueStatus.ACTIVE);

        if (activeUserCount < MAX_ACTIVE_USERS) {
            // 바로 활성화
            return queueRepository.save(QueueToken.createActiveToken(userId));
        } else {
            // 대기열에 추가
            long waitingCount = queueRepository.countByStatus(QueueStatus.WAITING);
            return queueRepository.save(QueueToken.createWaitingToken(userId, waitingCount + 1));
        }
    }

    /**
     * 대기열 상태 조회
     */
    @Transactional(readOnly = true)
    public QueueToken getTokenStatus(String token) {
        QueueToken queueToken = queueRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다."));

        if (queueToken.isExpired()) {
            throw new IllegalStateException("만료된 토큰입니다.");
        }

        // 대기 중인 토큰의 위치 업데이트
        if (queueToken.getStatus() == QueueStatus.WAITING) {
            updateTokenPosition(queueToken);
        }

        return queueToken;
    }

    /**
     * 토큰 검증 (다른 API에서 사용)
     */
    @Transactional(readOnly = true)
    public void validateActiveToken(String token) {
        QueueToken queueToken = queueRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다."));

        if (!queueToken.isActive()) {
            throw new IllegalStateException("활성화되지 않은 토큰입니다. 대기열에서 순서를 기다려주세요.");
        }
    }

    /**
     * 토큰 만료 처리 (결제 완료 후 호출)
     */
    public void expireToken(String token) {
        QueueToken queueToken = queueRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다."));

        queueToken.expire();
        queueRepository.save(queueToken);

        // 대기 중인 사용자를 활성화
        activateWaitingUsers();
    }

    /**
     * 대기 중인 사용자들의 위치 업데이트
     */
    private void updateTokenPosition(QueueToken token) {
        if (token.getStatus() != QueueStatus.WAITING) {
            return;
        }

        // 자신보다 앞에 있는 대기자 수 계산
        long position = queueRepository.countByStatusAndCreatedAtBefore(
                QueueStatus.WAITING, token.getCreatedAt()) + 1;

        token.updatePosition(position);
        queueRepository.save(token);
    }

    /**
     * 대기 중인 사용자를 활성화 (스케줄러에서 호출)
     */
    public void activateWaitingUsers() {
        long activeUserCount = queueRepository.countByStatus(QueueStatus.ACTIVE);
        long availableSlots = MAX_ACTIVE_USERS - activeUserCount;

        if (availableSlots > 0) {
            List<QueueToken> waitingTokens = queueRepository.findByStatusOrderByCreatedAt(QueueStatus.WAITING);

            waitingTokens.stream()
                    .limit(availableSlots)
                    .forEach(token -> {
                        token.activate();
                        queueRepository.save(token);
                    });
        }
    }

    /**
     * 만료된 토큰들 정리 (스케줄러에서 호출)
     */
    public void cleanupExpiredTokens() {
        List<QueueToken> expiredTokens = queueRepository.findExpiredTokens();
        expiredTokens.forEach(queueRepository::delete);

        // 정리 후 대기자 활성화
        activateWaitingUsers();
    }
}
