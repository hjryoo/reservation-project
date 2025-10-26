package kr.hhplus.be.server.integration;
import kr.hhplus.be.server.application.service.SeatReservationService;
import kr.hhplus.be.server.config.RedisTestContainerConfig;
import kr.hhplus.be.server.domain.model.*;
import kr.hhplus.be.server.domain.repository.*;
import kr.hhplus.be.server.domain.port.out.UserRepository;
import kr.hhplus.be.server.infrastructure.persistence.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Redis 분산락을 적용한 좌석 예약 동시성 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(RedisTestContainerConfig.class)
public class DistributedLockSeatReservationTest {

    @Autowired private SeatReservationService seatReservationService;
    @Autowired private UserRepository userRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private ConcertDateRepository concertDateRepository;
    @Autowired private SeatReservationRepository seatReservationRepository;

    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private ConcertJpaRepository concertJpaRepository;
    @Autowired private SeatReservationJpaRepository seatReservationJpaRepository;

    private List<User> testUsers;
    private Concert testConcert;
    private ConcertDate testConcertDate;
    private Integer seatNumber;
    private Long seatPrice;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성 (50명)
        testUsers = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            User user = User.create("distributedUser" + i, 500000L);
            testUsers.add(userRepository.save(user));
        }

        // 콘서트 생성
        testConcert = Concert.create(
                "인기 콘서트 with Redis Lock",
                "인기 가수",
                "서울 올림픽 경기장",
                1,
                200000L
        );
        testConcert = concertRepository.save(testConcert);

        // 콘서트 일정 생성
        LocalDateTime concertDateTime = LocalDateTime.now().plusDays(30);
        testConcertDate = ConcertDate.create(
                testConcert.getId(),
                concertDateTime,
                concertDateTime.minusHours(1),
                concertDateTime.plusHours(3),
                1
        );
        testConcertDate.openBooking();
        testConcertDate = concertDateRepository.save(testConcertDate);

        seatNumber = 1;
        seatPrice = 200000L;

        // 예약 가능한 좌석 생성
        SeatReservation availableSeat = SeatReservation.createAvailableSeat(
                testConcert.getId(), seatNumber, seatPrice);
        seatReservationRepository.save(availableSeat);
    }

    @AfterEach
    void tearDown() {
        seatReservationJpaRepository.deleteAllInBatch();
        concertJpaRepository.deleteAllInBatch();
        userJpaRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("분산락 동시성 테스트: 50명이 동시 예약 시도, Redis 분산락으로 1명만 성공")
    void shouldAllowOnlyOneReservationWithDistributedLock() throws InterruptedException {
        // Given
        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        // When: 50명이 정확히 동시에 예약 시도
        for (int i = 0; i < threadCount; i++) {
            final int userIndex = i;
            final User currentUser = testUsers.get(userIndex);

            executorService.submit(() -> {
                try {
                    startLatch.await();

                    // 분산락이 적용된 메서드 호출
                    SeatReservation reservedSeat = seatReservationService.reserveSeatTemporarily(
                            testConcert.getId(), seatNumber, currentUser.getId());

                    successCount.incrementAndGet();
                    System.out.println("User " + userIndex + " 예약 성공! (분산락 통과)");

                } catch (IllegalStateException e) {
                    failureCount.incrementAndGet();
                    System.out.println("User " + userIndex + " 예약 실패: " + e.getMessage());

                } catch (Exception e) {
                    exceptions.add(e);
                    failureCount.incrementAndGet();
                    System.out.println("User " + userIndex + " 예외 발생: " + e.getMessage());

                } finally {
                    completeLatch.countDown();
                }
            });
        }

        // 모든 스레드 동시 시작
        startLatch.countDown();

        // 모든 스레드 완료 대기
        boolean finished = completeLatch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 결과 검증
        assertThat(finished).isTrue();

        System.out.println("=== Redis 분산락 좌석 예약 테스트 결과 ===");
        System.out.println("예약 성공: " + successCount.get() + "명");
        System.out.println("예약 실패: " + failureCount.get() + "명");
        System.out.println("예외 발생: " + exceptions.size() + "건");

        // 정확히 1명만 예약 성공해야 함
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(49);

        // DB에서 좌석 상태 확인
        SeatReservation finalSeat = seatReservationRepository
                .findByConcertIdAndSeatNumber(testConcert.getId(), seatNumber)
                .orElseThrow();

        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.RESERVED);
        assertThat(finalSeat.getUserId()).isNotNull();

        // 예외가 발생하지 않아야 함 (모두 비즈니스 로직으로 처리)
        assertThat(exceptions).isEmpty();
    }

    @Test
    @DisplayName("분산락 미적용 시 동시성 문제 발생 확인 (비교 테스트)")
    void shouldFailWithoutDistributedLock() throws InterruptedException {
        // Given: 분산락 없이 비관적 락만 사용하는 경우
        // Note: 이 테스트는 분산락의 필요성을 증명하기 위한 대조군

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 10명이 동시 예약 시도 (분산락 없이)
        for (int i = 0; i < threadCount; i++) {
            final int userIndex = i;
            final User currentUser = testUsers.get(userIndex);

            executorService.submit(() -> {
                try {
                    startLatch.await();

                    // 분산락 없이 직접 Repository 접근
                    SeatReservation seat = seatReservationRepository
                            .findByConcertIdAndSeatNumber(testConcert.getId(), seatNumber)
                            .orElseThrow();

                    if (seat.isAvailable()) {
                        // Race condition 시뮬레이션
                        Thread.sleep(100);
                        seat.reserveTemporarily(currentUser.getId());
                        seatReservationRepository.save(seat);
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        completeLatch.await();
        executorService.shutdown();

        // Then: 분산락이 없으면 동시성 문제 발생 가능
        System.out.println("=== 분산락 미적용 테스트 결과 ===");
        System.out.println("예약 성공: " + successCount.get() + "명");
        System.out.println("예약 실패: " + failureCount.get() + "명");

        // 분산락 없이는 여러 명이 예약 성공할 수 있음 (동시성 문제)
        // Note: 실제로는 DB 레벨 제약조건이나 비관적 락으로 일부 방어 가능
    }
}