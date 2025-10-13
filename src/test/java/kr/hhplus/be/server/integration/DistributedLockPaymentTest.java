package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.application.ProcessPaymentService;
import kr.hhplus.be.server.application.service.ConcurrencyUserBalanceService;
import kr.hhplus.be.server.config.RedisTestContainerConfig;
import kr.hhplus.be.server.config.TestPaymentConfig;
import kr.hhplus.be.server.domain.model.*;
import kr.hhplus.be.server.domain.port.in.ProcessPaymentUseCase.*;
import kr.hhplus.be.server.domain.repository.*;
import kr.hhplus.be.server.infrastructure.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Redis 분산락을 적용한 결제 처리 동시성 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({RedisTestContainerConfig.class, TestPaymentConfig.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DistributedLockPaymentTest {

    @Autowired
    private ProcessPaymentService processPaymentService;
    @Autowired
    private ConcurrencyUserBalanceService userBalanceService;
    @Autowired
    private SeatReservationRepository seatReservationRepository;
    @Autowired
    private UserBalanceRepository userBalanceRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SeatReservationJpaRepository seatReservationJpaRepository;
    @Autowired
    private UserBalanceJpaRepository userBalanceJpaRepository;
    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    private Long testUserId;
    private Long concertId;
    private Integer seatNumber;
    private Long reservationId;
    private Long paymentAmount;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        testUserId = 1L;
        concertId = 1L;
        seatNumber = 1;
        paymentAmount = 50000L;

        // 기존 데이터 완전 삭제 (순서 중요!)
        paymentJpaRepository.deleteAllInBatch();
        seatReservationJpaRepository.deleteAllInBatch();
        userBalanceJpaRepository.deleteAllInBatch();

        // 사용자 잔액 생성 (100,000원)
        UserBalance userBalance = UserBalance.create(testUserId, 100000L);
        userBalanceRepository.save(userBalance);

        // 임시 예약 생성 (RESERVED 상태)
        LocalDateTime now = LocalDateTime.now();
        SeatReservation reservation = SeatReservation.createWithTimes(
                concertId,
                seatNumber,
                testUserId,
                paymentAmount,
                now,
                now.plusMinutes(10) // 10분 후 만료
        );
        SeatReservation savedReservation = seatReservationRepository.save(reservation);
        reservationId = savedReservation.getId();

        System.out.println("\n=== 테스트 초기화 완료 ===");
        System.out.println("예약 ID: " + reservationId);
        System.out.println("사용자 ID: " + testUserId);
        System.out.println("좌석 상태: " + savedReservation.getStatus());
        System.out.println("초기 잔액: " + userBalanceRepository.findByUserId(testUserId)
                .map(UserBalance::getBalance).orElse(0L) + "원\n");
    }

    @AfterEach
    void tearDown() {
        paymentJpaRepository.deleteAllInBatch();
        seatReservationJpaRepository.deleteAllInBatch();
        userBalanceJpaRepository.deleteAllInBatch();
    }

    @Test
    @Order(1)
    @DisplayName("분산락 결제 처리: 동일 예약에 10명 동시 결제 시도, 1명만 성공")
    void shouldAllowOnlyOnePaymentWithDistributedLock() throws InterruptedException {
        // Given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> errorMessages = new CopyOnWriteArrayList<>();

        // When: 10명이 동시에 같은 예약에 대해 결제 시도
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    ProcessPaymentCommand command = new ProcessPaymentCommand(
                            reservationId, testUserId, paymentAmount);

                    // 분산락이 적용된 메서드 호출
                    Payment payment = processPaymentService.processPayment(command);
                    if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                        successCount.incrementAndGet();
                        System.out.println("✅ 결제 성공 [" + index + "] - ID: " + payment.getId());
                    } else {
                        failureCount.incrementAndGet();
                        System.out.println("❌ 결제 실패 [" + index + "]: " + payment.getFailureReason());
                        errorMessages.add(payment.getFailureReason());
                    }

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    String errorMsg = e.getMessage();
                    System.err.println("⚠️ 예외 발생 [" + index + "]: " + errorMsg);
                    errorMessages.add(errorMsg);
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
        assertThat(finished).as("모든 스레드가 60초 내에 완료되어야 함").isTrue();

        System.out.println("\n=== Redis 분산락 결제 처리 테스트 결과 ===");
        System.out.println("결제 성공: " + successCount.get() + "건");
        System.out.println("결제 실패: " + failureCount.get() + "건");
        System.out.println("에러 메시지 샘플: " +
                (errorMessages.isEmpty() ? "없음" : errorMessages.get(0)));

        // 정확히 1건만 결제 성공해야 함
        assertThat(successCount.get())
                .as("분산락으로 인해 정확히 1건의 결제만 성공해야 함")
                .isEqualTo(1);

        assertThat(failureCount.get())
                .as("나머지 9건은 실패해야 함")
                .isEqualTo(9);

        // 잔액 확인: 100,000 - 50,000 = 50,000
        UserBalance finalBalance = userBalanceService.getBalance(testUserId);
        assertThat(finalBalance.getBalance())
                .as("최종 잔액은 50,000원이어야 함 (1번만 차감)")
                .isEqualTo(50000L);

        // 좌석 상태 확인: SOLD (CONFIRMED로 변경됨)
        SeatReservation finalSeat = seatReservationRepository
                .findById(reservationId)
                .orElseThrow();
        assertThat(finalSeat.getStatus())
                .as("좌석 상태는 SOLD여야 함")
                .isEqualTo(SeatStatus.SOLD);

        // DB에 실제로 COMPLETED 결제가 1건만 존재하는지 확인
        List<Payment> completedPayments = paymentRepository.findByUserId(testUserId)
                .stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                .toList();

        assertThat(completedPayments).hasSize(1);
    }

    @Test
    @Order(2)
    @DisplayName("분산락 멱등성 키 결제: 동일 멱등성 키로 중복 결제 방지")
    void shouldPreventDuplicatePaymentsWithIdempotencyKey() throws InterruptedException {
        // Given
        String idempotencyKey = "test-idempotency-key-12345";
        int threadCount = 5;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);

        AtomicInteger completedCount = new AtomicInteger(0);
        ConcurrentHashMap<Long, Integer> paymentIdMap = new ConcurrentHashMap<>();
        List<String> errorMessages = new CopyOnWriteArrayList<>();

        // When: 5명이 동일한 멱등성 키로 결제 시도
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    ProcessPaymentCommand command = new ProcessPaymentCommand(
                            reservationId, testUserId, paymentAmount);

                    // 멱등성 키를 사용한 결제 처리
                    Payment payment = processPaymentService.processPaymentIdempotent(
                            command, idempotencyKey);

                    if (payment != null && payment.getId() != null) {
                        paymentIdMap.merge(payment.getId(), 1, Integer::sum);

                        if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                            completedCount.incrementAndGet();
                            System.out.println("✅ 결제 응답 [" + index + "]: ID=" +
                                    payment.getId() + ", Status=" + payment.getStatus());
                        } else {
                            System.out.println("❌ 결제 실패 응답 [" + index + "]: " +
                                    payment.getFailureReason());
                            errorMessages.add(payment.getFailureReason());
                        }
                    }

                } catch (Exception e) {
                    System.err.println("⚠️ 예외 발생 [" + index + "]: " + e.getMessage());
                    errorMessages.add(e.getMessage());
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = completeLatch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 결과 검증
        assertThat(finished).isTrue();

        System.out.println("\n=== Redis 분산락 멱등성 키 결제 테스트 결과 ===");
        System.out.println("완료된 결제 응답: " + completedCount.get() + "건");
        System.out.println("고유 결제 ID 개수: " + paymentIdMap.size());
        paymentIdMap.forEach((id, count) ->
                System.out.println("  - Payment ID " + id + ": " + count + "번 반환"));

        if (!errorMessages.isEmpty()) {
            System.out.println("에러 메시지들:");
            errorMessages.forEach(msg -> System.out.println("  - " + msg));
        }

        // 멱등성 키로 인해 실제 결제는 1번만 처리되어야 함
        // 나머지는 같은 결과를 반환받음
        assertThat(completedCount.get())
                .as("멱등성 키로 인해 최소 1건 이상의 성공 응답")
                .isGreaterThanOrEqualTo(1);

        // 모든 요청이 동일한 Payment ID를 반환받아야 함
        assertThat(paymentIdMap.size())
                .as("멱등성으로 인해 모든 요청이 같은 결제 ID를 받아야 함")
                .isEqualTo(1);

        // 실제 DB에는 1건의 결제만 존재
        List<Payment> allPayments = paymentRepository.findByUserId(testUserId);
        long completedPaymentCount = allPayments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                .count();

        assertThat(completedPaymentCount)
                .as("DB에는 1건의 완료된 결제만 존재해야 함")
                .isEqualTo(1);

        // 잔액은 한 번만 차감되어야 함
        UserBalance finalBalance = userBalanceService.getBalance(testUserId);
        assertThat(finalBalance.getBalance())
                .as("잔액은 1번만 차감되어 50,000원이어야 함")
                .isEqualTo(50000L);
    }

    @Test
    @Order(3)
    @DisplayName("분산락 결제: 잔액 부족 시 모두 실패")
    void shouldFailAllPaymentsWhenInsufficientBalance() throws InterruptedException {
        // Given: 잔액을 10,000원으로 변경 (기존 100,000원에서 90,000원 차감)
        userBalanceService.deductBalanceWithConditionalUpdate(testUserId, 90000L);

        // 잔액 확인
        UserBalance currentBalance = userBalanceService.getBalance(testUserId);
        System.out.println("현재 잔액: " + currentBalance.getBalance() + "원");
        assertThat(currentBalance.getBalance()).isEqualTo(10000L);

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);

        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> errorMessages = new CopyOnWriteArrayList<>();

        // When: 5명이 동시에 결제 시도 (모두 잔액 부족)
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    ProcessPaymentCommand command = new ProcessPaymentCommand(
                            reservationId, testUserId, paymentAmount);

                    Payment payment = processPaymentService.processPayment(command);

                    if (payment.getStatus() == Payment.PaymentStatus.FAILED) {
                        failureCount.incrementAndGet();
                        System.out.println("❌ 예상된 실패 [" + index + "]: " +
                                payment.getFailureReason());
                        errorMessages.add(payment.getFailureReason());
                    } else {
                        System.out.println("⚠️ 예상치 못한 상태 [" + index + "]: " +
                                payment.getStatus());
                    }

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.err.println("⚠️ 예외 발생 (예상됨) [" + index + "]: " + e.getMessage());
                    errorMessages.add(e.getMessage());
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = completeLatch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 모두 실패해야 함
        assertThat(finished).isTrue();
        assertThat(failureCount.get())
                .as("잔액 부족으로 모든 결제가 실패해야 함")
                .isEqualTo(threadCount);

        // 잔액은 변하지 않아야 함
        UserBalance finalBalance = userBalanceService.getBalance(testUserId);
        assertThat(finalBalance.getBalance())
                .as("잔액은 변하지 않고 10,000원이어야 함")
                .isEqualTo(10000L);

        // 좌석은 여전히 RESERVED 상태여야 함
        SeatReservation finalSeat = seatReservationRepository
                .findById(reservationId)
                .orElseThrow();
        assertThat(finalSeat.getStatus())
                .as("좌석은 여전히 RESERVED 상태여야 함")
                .isEqualTo(SeatStatus.RESERVED);

        System.out.println("\n=== 잔액 부족 테스트 결과 ===");
        System.out.println("모든 결제 실패: " + failureCount.get() + "건");
        System.out.println("최종 잔액: " + finalBalance.getBalance() + "원 (변동 없음)");
        System.out.println("좌석 상태: " + finalSeat.getStatus() + " (변동 없음)");
    }

    @Test
    @Order(4)
    @DisplayName("분산락 결제: 락 획득 순서에 따라 순차적으로 실패")
    void shouldProcessPaymentsSequentiallyWithLock() throws InterruptedException {
        // Given
        int threadCount = 3;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);

        List<String> executionOrder = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 3명이 동시에 결제 시도
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    long startTime = System.currentTimeMillis();
                    ProcessPaymentCommand command = new ProcessPaymentCommand(
                            reservationId, testUserId, paymentAmount);

                    Payment payment = processPaymentService.processPayment(command);
                    long endTime = System.currentTimeMillis();

                    String log = String.format("Thread[%d] - %s - %dms",
                            index,
                            payment.getStatus(),
                            (endTime - startTime));
                    executionOrder.add(log);

                    if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }

                } catch (Exception e) {
                    executionOrder.add("Thread[" + index + "] - EXCEPTION: " + e.getMessage());
                    failureCount.incrementAndGet();
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = completeLatch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 결과 검증
        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(2);

        System.out.println("\n=== 순차 처리 테스트 결과 ===");
        System.out.println("실행 순서:");
        executionOrder.forEach(System.out::println);
        System.out.println("\n✅ 분산락이 정상적으로 순차 처리를 보장합니다!");
    }
}
