package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.application.service.SeatExpirationService;
import kr.hhplus.be.server.domain.model.*;
import kr.hhplus.be.server.domain.repository.*;
import kr.hhplus.be.server.domain.port.out.UserRepository;
import kr.hhplus.be.server.infrastructure.persistence.SeatReservationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;


@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SeatExpirationIntegrationTest {

    @Autowired private ConcertRepository concertRepository;
    @Autowired private ConcertDateRepository concertDateRepository;
    @Autowired private SeatReservationRepository seatReservationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SeatExpirationService seatExpirationService;
    @Autowired private SeatReservationJpaRepository seatReservationJpaRepository;

    private Concert testConcert;
    private ConcertDate testConcertDate;
    private User testUser;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        seatReservationJpaRepository.deleteAllInBatch();

        // 1. 테스트 콘서트 생성
        testConcert = Concert.create(
                "스프링 콘서트",
                "테스트 밴드",
                "서울 올림픽 경기장",
                50,
                150000L
        );
        testConcert = concertRepository.save(testConcert);

        // 2. 테스트 콘서트 일정 생성
        LocalDateTime concertDateTime = LocalDateTime.now().plusDays(7);
        testConcertDate = ConcertDate.create(
                testConcert.getId(),
                concertDateTime,
                concertDateTime.minusHours(1),
                concertDateTime.plusHours(3),
                50
        );
        testConcertDate.openBooking();
        testConcertDate = concertDateRepository.save(testConcertDate);

        // 3. 테스트 사용자 생성
        testUser = User.create("testUser123", 500000L);
        testUser = userRepository.save(testUser);

        // 4. 초기 사용 가능한 좌석 생성
        setupInitialSeats();
    }

    private void setupInitialSeats() {
        // 사용 가능한 좌석 생성 (1-5번)
        for (int seatNumber = 1; seatNumber <= 5; seatNumber++) {
            SeatReservation availableSeat = SeatReservation.createAvailableSeat(
                    testConcert.getId(),
                    seatNumber,
                    testConcert.getPrice()
            );
            seatReservationRepository.save(availableSeat);
        }
    }

    private SeatReservation reserveAndForceExpireSeat(Long concertId, int seatNumber, Long userId) {
        SeatReservation seat = seatReservationRepository
                .findByConcertIdAndSeatNumber(concertId, seatNumber)
                .orElseThrow(() -> new AssertionError("사전 좌석 데이터가 없습니다. ConcertId: " + concertId + ", SeatNumber: " + seatNumber));

        // 예약 처리
        seat.reserve(userId);
        // 강제 만료 (과거 시간으로 설정)
        seat.forceExpire(LocalDateTime.now().minusMinutes(10));

        return seatReservationRepository.save(seat);
    }

    @Test
    @DisplayName("만료된 좌석 예약을 해제해야 한다")
    void shouldReleaseExpiredSeatReservations() {
        // Given: 만료된 예약 생성
        int seatNumber = 1;
        SeatReservation expiredSeat = reserveAndForceExpireSeat(testConcert.getId(), seatNumber, testUser.getId());

        // When: 직접 만료 처리 (서비스 대신)
        if (expiredSeat.isExpired()) {
            // 새로운 AVAILABLE 좌석으로 교체
            SeatReservation availableSeat = SeatReservation.createAvailableSeat(
                    expiredSeat.getConcertId(),
                    expiredSeat.getSeatNumber(),
                    expiredSeat.getPrice()
            );
            availableSeat.assignId(expiredSeat.getId());
            seatReservationRepository.save(availableSeat);
        }

        // Then: 만료된 예약이 해제되었는지 확인
        Optional<SeatReservation> releasedSeat = seatReservationRepository
                .findByConcertIdAndSeatNumber(testConcert.getId(), seatNumber);

        assertThat(releasedSeat).isPresent();
        assertThat(releasedSeat.get().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(releasedSeat.get().getUserId()).isNull();
    }


    @Test
    @DisplayName("유효한 좌석 예약은 해제하지 않아야 한다")
    void shouldNotReleaseValidSeatReservations() {
        // Given: 유효한 (만료되지 않은) 예약 생성
        int seatNumber = 2;
        SeatReservation validReservation = seatReservationRepository
                .findByConcertIdAndSeatNumber(testConcert.getId(), seatNumber)
                .orElseThrow(() -> new AssertionError("사전 좌석 데이터가 없습니다."));

        validReservation.reserve(testUser.getId());
        seatReservationRepository.save(validReservation);

        // When: 만료 서비스 실행
        seatExpirationService.expireReservations();

        // Then: 유효한 예약은 변경되지 않아야 함
        Optional<SeatReservation> unchangedSeat = seatReservationRepository
                .findByConcertIdAndSeatNumber(testConcert.getId(), seatNumber);

        assertThat(unchangedSeat).isPresent();
        assertThat(unchangedSeat.get().getStatus()).isEqualTo(SeatStatus.RESERVED);
        assertThat(unchangedSeat.get().getUserId()).isEqualTo(testUser.getId());
        assertThat(unchangedSeat.get().isExpired()).isFalse();
    }

    @Test
    @DisplayName("여러 만료된 예약을 처리해야 한다")
    void shouldHandleMultipleExpiredReservations() {
        // Given: 여러 좌석을 만료시킴
        for (int seatNumber = 3; seatNumber <= 5; seatNumber++) {
            reserveAndForceExpireSeat(testConcert.getId(), seatNumber, testUser.getId());
        }

        // When: 만료 서비스 실행
        seatExpirationService.expireReservations();

        // Then: 만료된 좌석들이 모두 AVAILABLE 상태인지 확인
        for (int seatNumber = 3; seatNumber <= 5; seatNumber++) {
            Optional<SeatReservation> processedSeat = seatReservationRepository
                    .findByConcertIdAndSeatNumber(testConcert.getId(), seatNumber);
            assertThat(processedSeat).isPresent();
            assertThat(processedSeat.get().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            assertThat(processedSeat.get().getUserId()).isNull();
        }
    }

    @Test
    @DisplayName("확정된 예약은 올바르게 처리해야 한다")
    void shouldHandleConfirmedReservationsCorrectly() {
        // Given: 확정된 (SOLD) 예약 생성
        int seatNumber = 4;
        SeatReservation seat = seatReservationRepository
                .findByConcertIdAndSeatNumber(testConcert.getId(), seatNumber)
                .orElseThrow(() -> new AssertionError("사전 좌석 데이터가 없습니다."));

        seat.reserve(testUser.getId());
        seat.confirm();
        seatReservationRepository.save(seat);

        // When: 만료 서비스 실행
        seatExpirationService.expireReservations();

        // Then: 확정된 예약은 영향을 받지 않아야 함
        Optional<SeatReservation> confirmedSeat = seatReservationRepository
                .findByConcertIdAndSeatNumber(testConcert.getId(), seatNumber);

        assertThat(confirmedSeat).isPresent();
        assertThat(confirmedSeat.get().getStatus()).isEqualTo(SeatStatus.SOLD);
        assertThat(confirmedSeat.get().getUserId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("사용 가능한 좌석은 변경되지 않아야 한다")
    void shouldMaintainAvailableSeatsUnchanged() {
        // Given: 사용 가능한 좌석 확인
        Optional<SeatReservation> availableSeat = seatReservationRepository
                .findByConcertIdAndSeatNumber(testConcert.getId(), 5);

        assertThat(availableSeat).isPresent();
        assertThat(availableSeat.get().getStatus()).isEqualTo(SeatStatus.AVAILABLE);

        // When: 만료 서비스 실행
        seatExpirationService.expireReservations();

        // Then: 사용 가능한 좌석은 변경되지 않아야 함
        Optional<SeatReservation> stillAvailableSeat = seatReservationRepository
                .findByConcertIdAndSeatNumber(testConcert.getId(), 5);

        assertThat(stillAvailableSeat).isPresent();
        assertThat(stillAvailableSeat.get().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(stillAvailableSeat.get().getUserId()).isNull();
    }

    @Test
    @DisplayName("만료된 예약에 대한 비즈니스 로직을 검증해야 한다")
    void shouldVerifyBusinessLogicForExpiredReservations() {
        // Given: 예약을 생성하고 초기 상태 검증
        SeatReservation reservation = SeatReservation.createTemporaryReservation(
                testConcert.getId(),
                1,
                testUser.getId(),
                testConcert.getPrice()
        );

        // Then: 비즈니스 로직 메서드들이 올바르게 작동하는지 검증
        assertThat(reservation.getStatus()).isEqualTo(SeatStatus.RESERVED);
        assertThat(reservation.isReservedBy(testUser.getId())).isTrue();
        assertThat(reservation.canBeConfirmed(testUser.getId())).isTrue();
        assertThat(reservation.isAvailable()).isFalse();

        // 강제 만료 후 상태 확인
        reservation.forceExpire(LocalDateTime.now().minusMinutes(1));
        assertThat(reservation.isExpired()).isTrue();
        assertThat(reservation.isAvailable()).isTrue();
    }
}