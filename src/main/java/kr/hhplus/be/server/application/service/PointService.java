package kr.hhplus.be.server.application.service;

import kr.hhplus.be.server.domain.entity.Point;
import kr.hhplus.be.server.domain.entity.PointHistory;
import kr.hhplus.be.server.domain.repository.PointRepository;
import kr.hhplus.be.server.domain.repository.PointHistoryRepository;
import kr.hhplus.be.server.infrastructure.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 포인트 서비스 (분산락 적용)
 *
 * 분산락 적용 이유:
 * - 동시 충전/사용 시 정확한 잔액 보장
 * - DB 비관적 락 제거로 성능 개선
 * - 포인트 히스토리와 잔액 업데이트의 원자성 보장
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    private final PointRepository pointRepository;
    private final PointHistoryRepository pointHistoryRepository;

    /**
     * 포인트 충전 (분산락 적용)
     *
     * 락 키: point:charge:{userId}
     * 락 범위: 잔액 조회 → 충전 → 저장 → 히스토리 기록
     *
     * 기존 비관적 락(findByUserIdWithLock) 제거
     */
    @DistributedLock(
            key = "'point:operation:' + #userId",  // ⭐ 통합된 락 키
            waitTime = 5L,
            leaseTime = 3L
    )
    @Transactional
    public Point chargePoint(Long userId, BigDecimal amount, String description) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }

        // 포인트 조회 (비관적 락 제거됨)
        Point point = pointRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Point newPoint = Point.create(userId);
                    return pointRepository.save(newPoint);
                });

        point.charge(amount);
        Point savedPoint = pointRepository.save(point);

        // 포인트 충전 이력 저장
        PointHistory history = PointHistory.charge(userId, amount, description);
        pointHistoryRepository.save(history);

        return savedPoint;
    }

    /**
     * 포인트 사용 (충전과 동일한 락 키 사용)
     *
     * 락 키: point:operation:{userId}  ⭐ chargePoint와 동일
     */
    @DistributedLock(
            key = "'point:operation:' + #userId",  // ⭐ 통합된 락 키
            waitTime = 5L,
            leaseTime = 3L
    )
    @Transactional
    public Point usePoint(Long userId, BigDecimal amount, String description) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("사용 금액은 0보다 커야 합니다.");
        }

        Point point = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("포인트 정보를 찾을 수 없습니다."));

        point.use(amount);
        Point savedPoint = pointRepository.save(point);

        // 포인트 사용 이력 저장
        PointHistory history = PointHistory.use(userId, amount, description);
        pointHistoryRepository.save(history);

        return savedPoint;
    }

    /**
     * 포인트 잔액 조회 (락 불필요 - 읽기 전용)
     */
    public Point getPointBalance(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }

        return pointRepository.findByUserId(userId)
                .orElseGet(() -> Point.create(userId));
    }

    /**
     * 포인트 사용 가능 여부 확인 (락 불필요 - 읽기 전용)
     */
    public boolean canUsePoints(Long userId, BigDecimal amount) {
        Point point = getPointBalance(userId);
        return point.hasEnoughBalance(amount);
    }

    /**
     * 포인트 이력 조회 (락 불필요 - 읽기 전용)
     */
    public List<PointHistory> getPointHistory(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }

        return pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}