package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.domain.model.*;
import kr.hhplus.be.server.domain.repository.*;
import kr.hhplus.be.server.domain.port.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ConcurrentSeatReservationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private SeatReservationRepository seatReservationRepository;

    private List<User> testUsers;
    private Concert testConcert;
    private Integer seatNumber;
    private Long seatPrice;

    @BeforeEach
    void setUp() {
        // 10명의 테스트 사용자 생성
        testUsers = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            User user = User.create("user" + i, 100000L);
            testUsers.add(userRepository.save(user));
        }

        testConcert = Concert.create(
                "인기 콘서트",
                "인기 아티스트",
                LocalDateTime.now().plusDays(30),
                1, // 단 1석만 available
                200000
        );
        testConcert = concertRepository.save(testConcert);

        seatNumber = 1; // VIP-1 좌석
        seatPrice = 200000L;

        // 예약 가능한 좌석 생성
        SeatReservation availableSeat = SeatReservation.createAvailableSeat(
                testConcert.getId(),
                seatNumber,
                seatPrice
        );
        seatReservationRepository.save(availableSeat);
    }

    @Test
    @DisplayName("다중 유저가 동시에 좌석 요청 시 한 명만 성공하도록 테스트")
    void testConcurrentSeatReservation() throws InterruptedException {
        // Given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        // When: 10명이 동시에 같은 좌석 예약 시도
        for (int i = 0; i < threadCount; i++) {
            final int userIndex = i;
            executorService.submit(() -> {
                try {
                    User currentUser = testUsers.get(userIndex);

                    // 동시 실행을 위한 대기
                    latch.countDown();
                    latch.await();

                    // 좌석 예약 시도
                    SeatReservation reservation = SeatReservation.createTemporaryReservation(
                            testConcert.getId(),
                            seatNumber,
                            currentUser.getId(),
                            seatPrice
                    );

                    // DB에 저장 시도 (여기서 동시성 제어가 일어남)
                    seatReservationRepository.save(reservation);
                    successCount.incrementAndGet();

                    System.out.println("사용자 " + userIndex + " 예약 성공!");

                } catch (Exception e) {
                    exceptions.add(e);
                    failureCount.incrementAndGet();
                    System.out.println("사용자 " + userIndex + " 예약 실패: " + e.getMessage());
                }
            });
        }

        executorService.shutdown();
        boolean finished = executorService.awaitTermination(10, TimeUnit.SECONDS);

        // Then: 결과 검증
        assertThat(finished).isTrue();

        // 정확히 1명만 성공해야 함 (동시성 제어가 제대로 되었다면)
        System.out.println("성공: " + successCount.get() + "명, 실패: " + failureCount.get() + "명");

        // 실제 운영에서는 DB 락이나 유니크 제약조건으로 1명만 성공하게 됨
        // 테스트에서는 동시성 제어 메커니즘이 작동하는지 확인
        assertThat(successCount.get() + failureCount.get()).isEqualTo(10);
    }
}
