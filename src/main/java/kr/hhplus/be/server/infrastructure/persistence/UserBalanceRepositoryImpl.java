package kr.hhplus.be.server.infrastructure.persistence;

import kr.hhplus.be.server.domain.model.UserBalance;
import kr.hhplus.be.server.domain.model.BalanceHistory;
import kr.hhplus.be.server.domain.model.TransactionType;
import kr.hhplus.be.server.domain.repository.UserBalanceRepository;
import kr.hhplus.be.server.infrastructure.persistence.entity.UserBalanceEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.BalanceHistoryEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class UserBalanceRepositoryImpl implements UserBalanceRepository {

    private final UserBalanceJpaRepository balanceJpaRepository;
    private final BalanceHistoryJpaRepository historyJpaRepository;

    @Override
    @Transactional
    public UserBalance save(UserBalance balance) {
        if (balance.getId() != null) {
            // 기존 엔티티 업데이트
            UserBalanceEntity entity = balanceJpaRepository.findById(balance.getId())
                    .orElseThrow(() -> new IllegalArgumentException("잔액 정보를 찾을 수 없습니다: " + balance.getId()));

            entity.setBalance(balance.getBalance());
            entity.setLastUpdatedAt(balance.getLastUpdatedAt());

            UserBalanceEntity saved = balanceJpaRepository.saveAndFlush(entity);
            return toBalanceDomain(saved);
        } else {
            // 신규 엔티티 생성
            UserBalanceEntity entity = new UserBalanceEntity(
                    balance.getUserId(),
                    balance.getBalance(),
                    balance.getLastUpdatedAt()
            );
            UserBalanceEntity saved = balanceJpaRepository.saveAndFlush(entity);
            return toBalanceDomain(saved);
        }
    }

    @Override
    public Optional<UserBalance> findByUserId(Long userId) {
        return balanceJpaRepository.findByUserId(userId)
                .map(this::toBalanceDomain);
    }

    @Override
    @Transactional
    public Optional<UserBalance> findByUserIdWithLock(Long userId) {
        return balanceJpaRepository.findByUserIdWithLock(userId)
                .map(this::toBalanceDomain);
    }

    @Override
    @Transactional
    public BalanceHistory saveHistory(BalanceHistory history) {
        BalanceHistoryEntity entity = toHistoryEntity(history);
        BalanceHistoryEntity saved = historyJpaRepository.save(entity);
        return toHistoryDomain(saved);
    }

    @Override
    public List<BalanceHistory> findHistoriesByUserId(Long userId) {
        return historyJpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toHistoryDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<BalanceHistory> findHistoriesByUserIdAndType(Long userId, TransactionType type) {
        return historyJpaRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type)
                .stream()
                .map(this::toHistoryDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey) {
        return historyJpaRepository.existsByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    @Override
    public Optional<BalanceHistory> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey) {
        return historyJpaRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(this::toHistoryDomain);
    }

    @Override
    public List<UserBalance> findUsersWithBalanceGreaterThan(Long amount) {
        return balanceJpaRepository.findUsersWithBalanceGreaterThanEqual(amount)
                .stream()
                .map(this::toBalanceDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Long getTotalSystemBalance() {
        return balanceJpaRepository.sumAllBalances();
    }

    @Override
    @Transactional
    public boolean deductBalanceConditionally(Long userId, Long amount) {
        LocalDateTime now = LocalDateTime.now();
        int updatedRows = balanceJpaRepository.deductBalanceConditionally(userId, amount, now);
        return updatedRows > 0;
    }

    @Override
    @Transactional
    public boolean chargeBalanceConditionally(Long userId, Long amount) {
        LocalDateTime now = LocalDateTime.now();
        int updatedRows = balanceJpaRepository.chargeBalanceConditionally(userId, amount, now);
        return updatedRows > 0;
    }

    @Override
    @Transactional
    public boolean deductBalanceWithOptimisticLock(Long userId, Long amount, Long expectedVersion) {
        LocalDateTime now = LocalDateTime.now();
        int updatedRows = balanceJpaRepository.deductBalanceWithOptimisticLock(userId, amount, expectedVersion, now);
        return updatedRows > 0;
    }

    @Override
    @Transactional
    public boolean createInitialBalanceIfNotExists(Long userId, Long initialBalance) {
        // 이미 존재하는지 먼저 확인
        Optional<UserBalanceEntity> existing = balanceJpaRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return false; // 이미 존재함
        }

        try {
            UserBalanceEntity entity = new UserBalanceEntity(userId, initialBalance, LocalDateTime.now());
            balanceJpaRepository.saveAndFlush(entity);
            return true;
        } catch (DataIntegrityViolationException e) {
            // 동시 생성으로 인한 중복 키 오류
            return false;
        }
    }

    // Entity → Domain 변환 (UserBalance)
    private UserBalance toBalanceDomain(UserBalanceEntity entity) {
        UserBalance balance = UserBalance.of(
                entity.getUserId(),
                entity.getBalance(),
                entity.getLastUpdatedAt(),
                entity.getVersion()
        );
        balance.assignId(entity.getId());
        return balance;
    }

    // Entity → Domain 변환 (BalanceHistory)
    private BalanceHistory toHistoryDomain(BalanceHistoryEntity entity) {
        BalanceHistory history;

        if (entity.getType() == TransactionType.CHARGE) {
            history = BalanceHistory.createChargeHistory(
                    entity.getUserId(),
                    entity.getAmount(),
                    entity.getBalanceBefore(),
                    entity.getBalanceAfter(),
                    entity.getIdempotencyKey(),
                    entity.getDescription()
            );
        } else {
            history = BalanceHistory.createDeductHistory(
                    entity.getUserId(),
                    entity.getAmount(),
                    entity.getBalanceBefore(),
                    entity.getBalanceAfter(),
                    entity.getIdempotencyKey(),
                    entity.getDescription()
            );
        }

        history.assignId(entity.getId());
        return history;
    }

    // Domain → Entity 변환 (BalanceHistory)
    private BalanceHistoryEntity toHistoryEntity(BalanceHistory domain) {
        return new BalanceHistoryEntity(
                domain.getUserId(),
                domain.getType(),
                domain.getAmount(),
                domain.getBalanceBefore(),
                domain.getBalanceAfter(),
                domain.getDescription(),
                domain.getIdempotencyKey(),
                domain.getCreatedAt()
        );
    }
}