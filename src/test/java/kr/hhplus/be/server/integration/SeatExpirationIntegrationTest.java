package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.application.service.SeatExpirationService;
import kr.hhplus.be.server.application.service.SeatReservationService;
import kr.hhplus.be.server.domain.model.*;
import kr.hhplus.be.server.domain.repository.*;
import kr.hhplus.be.server.domain.port.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SeatExpirationIntegrationTest {

    @Autowired private SeatReservationService seatReservationService;
    @Autowired private SeatExpirationService seatExpirationService;
    @Autowired private UserRepository userRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private SeatReservationRepository seatReservationRepository;

    private User user1, user2;
    private Concert testConcert;
    private Integer seatNumber;
    private Long seatPrice;

    @BeforeEach
    void setUp() {
        user1 = User.create("user1", 100000L);
        user2 = User.create("user2", 100000L);
        user1 = userRepository.save(user1);
        user2 = userRepository.save(user2);

        testConcert = Concert.create("테스트 콘서트", "아티스트",
                LocalDateTime.now().plusDays(7), 50, 100000);
        testConcert = concertRepository.save(testConcert);

        seatNumber = 1;
        seatPrice = 100000L;

        // 초기 예약 가능 좌석 생성
        SeatReservation availableSeat = SeatReservation.createAvailableSeat(
                testConcert.getId(), seatNumber, seatPrice);
        seatReservationRepository.save(availableSeat);
    }

    @Test
    @DisplayName("실제 시간 기반: 만료 시간 후 좌석이 자동으로 예약 가능 상태로 변경")
    void shouldAutomaticallyExpireReservationAfterRealTime() throws InterruptedException {
        // Given: 사용자1이 좌석을 임시 예약 (5분 만료)
        SeatReservation reservedSeat = seatReservationService.reserveSeatTemporarily(
                testConcert.getId(), seatNumber, user1.getId());

        // Then 1: 임시 예약 상태 확인
        assertThat(reservedSeat.getStatus()).isEqualTo(SeatStatus.RESERVED);
        assertThat(reservedSeat.getUserId()).isEqualTo(user1.getId());
        assertThat(reservedSeat.getExpiresAt()).isAfter(LocalDateTime.now());

        // When: 실제 시간 대기 및 스케줄러 실행
        // 테스트 환경에서는 만료 시간을 짧게 설정 (6초)
        Thread.sleep(6000); // 6초 대기

        // 스케줄러 수동 실행 (실제로는 @Scheduled가 자동 실행)
        seatExpirationService.expireReservations();

        // Then 2: 좌석이 자동으로 AVAILABLE 상태로 변경되었는지 확인
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            SeatReservation currentSeat = seatReservationRepository
                    .findByConcertIdAndSeatNumber(testConcert.getId(), seatNumber)
                    .orElseThrow();

            assertThat(currentSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            assertThat(currentSeat.getUserId()).isNull();
        });

        // When 2: 사용자2가 만료된 좌석을 새로 예약
        SeatReservation newReservation = seatReservationService.reserveSeatTemporarily(
                testConcert.getId(), seatNumber, user2.getId());

        // Then 3: 사용자2의 예약이 성공했는지 확인
        assertThat(newReservation.getStatus()).isEqualTo(SeatStatus.RESERVED);
        assertThat(newReservation.getUserId()).isEqualTo(user2.getId());
        assertThat(newReservation.getExpiresAt()).isAfter(LocalDateTime.now());
    }
}
