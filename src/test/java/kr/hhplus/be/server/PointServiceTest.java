package kr.hhplus.be.server;

import kr.hhplus.be.server.application.service.PointService;
import kr.hhplus.be.server.domain.entity.Point;
import kr.hhplus.be.server.domain.entity.PointHistory;
import kr.hhplus.be.server.domain.repository.PointRepository;
import kr.hhplus.be.server.domain.repository.PointHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private PointRepository pointRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @InjectMocks
    private PointService pointService;

    private Point testPoint;

    @BeforeEach
    void setUp() {
        testPoint = Point.create(1L);
    }

    @Test
    @DisplayName("포인트를 충전할 수 있다")
    void chargePoint() {
        // Given
        Long userId = 1L;
        BigDecimal amount = new BigDecimal("10000");
        String description = "테스트 충전";

        // ⭐ 중요: findByUserId로 변경 (findByUserIdWithLock 제거)
        when(pointRepository.findByUserId(userId))
                .thenReturn(Optional.of(testPoint));
        when(pointRepository.save(any(Point.class)))
                .thenReturn(testPoint);
        when(pointHistoryRepository.save(any(PointHistory.class)))
                .thenReturn(mock(PointHistory.class));

        // When
        Point result = pointService.chargePoint(userId, amount, description);

        // Then
        assertThat(result.getBalance())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(amount);

        verify(pointRepository).findByUserId(userId);
        verify(pointRepository).save(testPoint);
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("포인트가 없는 사용자는 새로운 포인트를 생성한다")
    void chargePoint_NewUser() {
        // Given
        Long userId = 2L;
        BigDecimal amount = new BigDecimal("5000");
        String description = "첫 충전";
        Point newPoint = Point.create(userId);

        when(pointRepository.findByUserId(userId))
                .thenReturn(Optional.empty());
        when(pointRepository.save(any(Point.class)))
                .thenReturn(newPoint);
        when(pointHistoryRepository.save(any(PointHistory.class)))
                .thenReturn(mock(PointHistory.class));

        // When
        Point result = pointService.chargePoint(userId, amount, description);

        // Then
        verify(pointRepository, times(2)).save(any(Point.class)); // 생성 + 충전
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("충전 금액이 0 이하면 예외가 발생한다")
    void chargePoint_InvalidAmount() {
        // Given
        Long userId = 1L;
        BigDecimal amount = BigDecimal.ZERO;
        String description = "잘못된 충전";

        // When & Then
        assertThatThrownBy(() -> pointService.chargePoint(userId, amount, description))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("충전 금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("포인트를 사용할 수 있다")
    void usePoint() {
        // Given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("10000");
        BigDecimal useAmount = new BigDecimal("3000");
        String description = "테스트 사용";

        testPoint.charge(chargeAmount);

        when(pointRepository.findByUserId(userId))
                .thenReturn(Optional.of(testPoint));
        when(pointRepository.save(any(Point.class)))
                .thenReturn(testPoint);
        when(pointHistoryRepository.save(any(PointHistory.class)))
                .thenReturn(mock(PointHistory.class));

        // When
        Point result = pointService.usePoint(userId, useAmount, description);

        // Then
        assertThat(result.getBalance())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(chargeAmount.subtract(useAmount));

        verify(pointRepository).findByUserId(userId);
        verify(pointRepository).save(testPoint);
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("잔액이 부족하면 포인트 사용 시 예외가 발생한다")
    void usePoint_InsufficientBalance() {
        // Given
        Long userId = 1L;
        BigDecimal useAmount = new BigDecimal("10000");
        String description = "잔액 부족 테스트";

        when(pointRepository.findByUserId(userId))
                .thenReturn(Optional.of(testPoint));

        // When & Then
        assertThatThrownBy(() -> pointService.usePoint(userId, useAmount, description))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("잔액이 부족합니다");
    }

    @Test
    @DisplayName("포인트 잔액을 조회할 수 있다")
    void getPointBalance() {
        // Given
        Long userId = 1L;
        BigDecimal amount = new BigDecimal("5000");
        testPoint.charge(amount);

        when(pointRepository.findByUserId(userId))
                .thenReturn(Optional.of(testPoint));

        // When
        Point result = pointService.getPointBalance(userId);

        // Then
        assertThat(result.getBalance())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(amount);

        verify(pointRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("포인트가 없는 사용자는 잔액 0으로 조회된다")
    void getPointBalance_NoPoint() {
        // Given
        Long userId = 999L;

        when(pointRepository.findByUserId(userId))
                .thenReturn(Optional.empty());

        // When
        Point result = pointService.getPointBalance(userId);

        // Then
        assertThat(result.getBalance())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(BigDecimal.ZERO);
        assertThat(result.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("포인트 사용 가능 여부를 확인할 수 있다")
    void canUsePoints() {
        // Given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("10000");
        BigDecimal useAmount = new BigDecimal("5000");

        testPoint.charge(chargeAmount);

        when(pointRepository.findByUserId(userId))
                .thenReturn(Optional.of(testPoint));

        // When
        boolean canUse = pointService.canUsePoints(userId, useAmount);

        // Then
        assertThat(canUse).isTrue();
    }
}