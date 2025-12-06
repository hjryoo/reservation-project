package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.application.service.ConcertRankingService;
import kr.hhplus.be.server.application.service.SeatReservationService;
import kr.hhplus.be.server.config.RedisTestContainerConfig;
import kr.hhplus.be.server.config.TestEventConfig;
import kr.hhplus.be.server.domain.model.Concert;
import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.repository.ConcertRepository;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import kr.hhplus.be.server.infrastructure.persistence.SpringDataConcertRepository;
import kr.hhplus.be.server.infrastructure.persistence.SeatReservationJpaRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * 좌석 예약 시 자동 매진 랭킹 등록 통합 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({RedisTestContainerConfig.class, TestEventConfig.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SeatReservationWithRankingTest {

    @Autowired
    private SeatReservationService seatReservationService;

    @Autowired
    private ConcertRankingService rankingService;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private SeatReservationRepository seatReservationRepository;

    @Autowired
    private SpringDataConcertRepository concertJpaRepository;

    @Autowired
    private SeatReservationJpaRepository seatJpaRepository;

    @BeforeEach
    void setUp() {
        seatJpaRepository.deleteAll();
        concertJpaRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        seatJpaRepository.deleteAll();
        concertJpaRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("마지막 좌석 확정 시 자동으로 매진 랭킹 등록")
    void confirmLastSeat_AutoRegisterRanking() throws InterruptedException {
        // Given: 10석 콘서트
        Concert concert = Concert.create("소규모 콘서트", "아티스트", "소극장", 10, 50000L);
        concert.openBooking();
        Concert savedConcert = concertRepository.save(concert);

        // 10개 좌석 생성
        IntStream.rangeClosed(1, 10).forEach(seatNum -> {
            SeatReservation seat = SeatReservation.createAvailableSeat(
                    savedConcert.getId(), seatNum, 50000L);
            seatReservationRepository.save(seat);
        });

        // When: 1초 대기 후 10개 좌석을 순차적으로 예약 및 확정
        Thread.sleep(1000); // 1초 대기로 duration > 0 보장

        for (int i = 1; i <= 10; i++) {
            seatReservationService.reserveSeatTemporarily(savedConcert.getId(), i, 100L + i);
            seatReservationService.confirmReservation(savedConcert.getId(), i, 100L + i);
        }

        // Then: 랭킹에 자동 등록되어야 함
        List<ConcertRankingService.ConcertRankingDto> ranking =
                rankingService.getTopDailySoldOutConcerts(10);

        assertThat(ranking).hasSize(1);
        assertThat(ranking.get(0).concertId()).isEqualTo(savedConcert.getId());
        assertThat(ranking.get(0).title()).isEqualTo("소규모 콘서트");
        assertThat(ranking.get(0).durationSeconds()).isGreaterThan(0L); // 1초 이상
    }

    @Test
    @Order(2)
    @DisplayName("여러 콘서트 동시 매진 - 빠른 순서대로 랭킹")
    void multipleConcerts_SortedBySpeed() throws InterruptedException {
        // Given: 3개 콘서트 (좌석 수 다름)
        Concert fastConcert = createConcertWithSeats("빠른 매진", 5);
        Concert mediumConcert = createConcertWithSeats("중간 매진", 5);
        Concert slowConcert = createConcertWithSeats("느린 매진", 5);

        // When: 서로 다른 속도로 매진
        Thread.sleep(1000); // 1초 대기
        sellOutConcert(fastConcert.getId(), 5, 0L);

        Thread.sleep(2000); // 2초 추가 대기
        sellOutConcert(mediumConcert.getId(), 5, 0L);

        Thread.sleep(2000); // 2초 추가 대기
        sellOutConcert(slowConcert.getId(), 5, 0L);

        // Then
        List<ConcertRankingService.ConcertRankingDto> ranking =
                rankingService.getTopDailySoldOutConcerts(10);

        assertThat(ranking).hasSize(3);
        assertThat(ranking.get(0).title()).isEqualTo("빠른 매진");
        assertThat(ranking.get(0).durationSeconds()).isGreaterThan(0L);

        assertThat(ranking.get(1).title()).isEqualTo("중간 매진");
        assertThat(ranking.get(1).durationSeconds()).isGreaterThan(ranking.get(0).durationSeconds());

        assertThat(ranking.get(2).title()).isEqualTo("느린 매진");
        assertThat(ranking.get(2).durationSeconds()).isGreaterThan(ranking.get(1).durationSeconds());
    }

    private Concert createConcertWithSeats(String title, int seatCount) {
        Concert concert = Concert.create(title, "아티스트", "장소", seatCount, 50000L);
        concert.openBooking();
        Concert saved = concertRepository.save(concert);

        IntStream.rangeClosed(1, seatCount).forEach(seatNum -> {
            SeatReservation seat = SeatReservation.createAvailableSeat(
                    saved.getId(), seatNum, 50000L);
            seatReservationRepository.save(seat);
        });

        return saved;
    }

    private void sellOutConcert(Long concertId, int seatCount, Long delayMs) throws InterruptedException {
        for (int i = 1; i <= seatCount; i++) {
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
            seatReservationService.reserveSeatTemporarily(concertId, i, 100L + i);
            seatReservationService.confirmReservation(concertId, i, 100L + i);
        }
    }
}