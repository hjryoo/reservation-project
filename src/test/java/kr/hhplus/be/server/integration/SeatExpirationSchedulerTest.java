package kr.hhplus.be.server.integration;
import kr.hhplus.be.server.application.service.SeatExpirationService;
import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.model.SeatStatus;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // 클래스당 한 번만 인스턴스 생성
public class SeatExpirationSchedulerTest {

    @Autowired
    private SeatExpirationService seatExpirationService;

    @Autowired
    private SeatReservationRepository seatReservationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeAll
    void setUpAll() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        // 각 테스트 전에 DB 초기화 (별도 트랜잭션에서)
        transactionTemplate.execute(status -> {
            seatReservationRepository.findAll().forEach(seat -> {
                // 모든 좌석 삭제하지 않고 테스트용 ID만 사용
            });
            return null;
        });
    }

    @Test
    @DisplayName("만료된 좌석 예약 스케줄러 테스트")
    void expiredReservationSchedulerTest() {
        // Given: 별도 트랜잭션에서 테스트 데이터 생성
        Long[] savedIds = transactionTemplate.execute(status -> {
            Long concertId = 100L;

            // 1. 만료된 예약 (1분 전에 만료)
            SeatReservation expiredReservation = SeatReservation.createTemporaryReservation(
                    concertId, 1, 100L, 10000L);
            expiredReservation.forceExpire(LocalDateTime.now().minusMinutes(1));
            SeatReservation savedExpired = seatReservationRepository.save(expiredReservation);

            // 2. 유효한 예약 (5분 후 만료)
            SeatReservation validReservation = SeatReservation.createTemporaryReservation(
                    concertId, 2, 200L, 10000L);
            SeatReservation savedValid = seatReservationRepository.save(validReservation);

            // 3. 이미 확정된 예약
            SeatReservation confirmedReservation = SeatReservation.createConfirmedReservation(
                    concertId, 3, 300L, 10000L);
            SeatReservation savedConfirmed = seatReservationRepository.save(confirmedReservation);

            return new Long[]{savedExpired.getId(), savedValid.getId(), savedConfirmed.getId()};
        });

        // When: 스케줄러 수동 실행 (별도 트랜잭션에서)
        int releasedCount = transactionTemplate.execute(status ->
                seatExpirationService.expireReservationsManually()
        );

        // Then: 만료된 예약만 해제되어야 함
        assertThat(releasedCount).isGreaterThanOrEqualTo(1);

        // 상태 검증 - 별도 트랜잭션에서 조회
        transactionTemplate.execute(status -> {
            SeatReservation seat1 = seatReservationRepository.findById(savedIds[0]).orElseThrow();
            SeatReservation seat2 = seatReservationRepository.findById(savedIds[1]).orElseThrow();
            SeatReservation seat3 = seatReservationRepository.findById(savedIds[2]).orElseThrow();

            assertThat(seat1.getStatus()).isEqualTo(SeatStatus.AVAILABLE); // 만료 → 해제
            assertThat(seat2.getStatus()).isEqualTo(SeatStatus.RESERVED);  // 유효 → 유지
            assertThat(seat3.getStatus()).isEqualTo(SeatStatus.SOLD);      // 확정 → 유지
            return null;
        });
    }

    @Test
    @DisplayName("스케줄러 성능 테스트: 대량 만료 처리")
    void schedulerPerformanceTest() {
        // Given: 대량의 만료된 예약 생성 (별도 트랜잭션)
        int expiredCount = 100;
        transactionTemplate.execute(status -> {
            Long concertId = 999L;
            for (int i = 1; i <= expiredCount; i++) {
                SeatReservation expiredReservation = SeatReservation.createTemporaryReservation(
                        concertId, i, (long) i, 10000L);
                expiredReservation.forceExpire(LocalDateTime.now().minusMinutes(1));
                seatReservationRepository.save(expiredReservation);
            }
            return null;
        });

        // When: 스케줄러 실행 시간 측정
        long startTime = System.currentTimeMillis();
        int releasedCount = transactionTemplate.execute(status ->
                seatExpirationService.expireReservationsManually()
        );
        long executionTime = System.currentTimeMillis() - startTime;

        // Then: 모든 만료된 예약이 해제되어야 함
        assertThat(releasedCount).isGreaterThanOrEqualTo(expiredCount);
        assertThat(executionTime).isLessThan(10000);

        System.out.println("해제된 예약 수: " + releasedCount);
        System.out.println("실행 시간: " + executionTime + "ms");
    }

    @Test
    @DisplayName("스케줄러 - 중복 실행 시 멱등성 테스트")
    void schedulerIdempotencyTest() {
        // Given: 만료된 예약 1개 생성 (별도 트랜잭션)
        Long savedId = transactionTemplate.execute(status -> {
            Long concertId = 200L;
            SeatReservation expiredReservation = SeatReservation.createTemporaryReservation(
                    concertId, 10, 500L, 10000L);
            expiredReservation.forceExpire(LocalDateTime.now().minusMinutes(1));
            return seatReservationRepository.save(expiredReservation).getId();
        });

        // When: 스케줄러를 2번 연속 실행
        int firstRun = transactionTemplate.execute(status ->
                seatExpirationService.expireReservationsManually()
        );
        int secondRun = transactionTemplate.execute(status ->
                seatExpirationService.expireReservationsManually()
        );

        // Then: 첫 실행에서만 해제되고, 두 번째 실행에서는 0개 해제
        assertThat(firstRun).isGreaterThanOrEqualTo(1);
        assertThat(secondRun).isEqualTo(0);

        // 최종 상태 확인
        transactionTemplate.execute(status -> {
            SeatReservation finalSeat = seatReservationRepository.findById(savedId).orElseThrow();
            assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            return null;
        });
    }

    @Test
    @DisplayName("스케줄러 - 정확히 만료 시간 경계 테스트")
    void schedulerExpiryBoundaryTest() {
        // Given: 경계 시간 테스트 데이터 생성
        Long savedNotExpiredId = transactionTemplate.execute(status -> {
            Long concertId = 300L;
            LocalDateTime now = LocalDateTime.now();

            // 1. 1초 전 만료 (해제되어야 함)
            SeatReservation justExpired = SeatReservation.createTemporaryReservation(
                    concertId, 1, 100L, 10000L);
            justExpired.forceExpire(now.minusSeconds(1));
            seatReservationRepository.save(justExpired);

            // 2. 1초 후 만료 (해제되지 않아야 함)
            SeatReservation notYetExpired = SeatReservation.createTemporaryReservation(
                    concertId, 2, 200L, 10000L);
            notYetExpired.forceExpire(now.plusSeconds(1));
            return seatReservationRepository.save(notYetExpired).getId();
        });

        // When: 스케줄러 실행
        transactionTemplate.execute(status -> {
            seatExpirationService.expireReservationsManually();
            return null;
        });

        // Then: 만료되지 않은 예약은 유지되어야 함
        transactionTemplate.execute(status -> {
            SeatReservation stillReserved = seatReservationRepository.findById(savedNotExpiredId).orElseThrow();
            assertThat(stillReserved.getStatus()).isEqualTo(SeatStatus.RESERVED);
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        // 테스트 후 정리 (선택사항)
        transactionTemplate.execute(status -> {
            // 필요시 테스트 데이터 정리
            return null;
        });
    }
}