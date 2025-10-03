package kr.hhplus.be.server.application.service;

import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.model.SeatStatus;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service("seatExpirationService")
public class SeatExpirationService {

    private final SeatReservationRepository seatReservationRepository;
    private static final Logger logger = LoggerFactory.getLogger(SeatExpirationService.class);

    public SeatExpirationService(SeatReservationRepository seatReservationRepository) {
        this.seatReservationRepository = seatReservationRepository;
    }
    public void expireReservations() {
        LocalDateTime now = LocalDateTime.now();

        // 모든 예약된 좌석을 조회하여 만료된 것들을 처리
        List<SeatReservation> allReservedSeats = seatReservationRepository
                .findByConcertIdAndStatus(null, SeatStatus.RESERVED); // concertId null로 모든 예약 조회

        // 실제로는 findAll로 모든 데이터를 가져와서 필터링
        if (allReservedSeats.isEmpty()) {
            // fallback: 전체 조회 후 필터링
            List<SeatReservation> allSeats = findAllSeats();
            for (SeatReservation seat : allSeats) {
                if (seat.getStatus() == SeatStatus.RESERVED && seat.isExpired()) {
                    // 새로운 AVAILABLE 좌석 생성
                    SeatReservation availableSeat = SeatReservation.createAvailableSeat(
                            seat.getConcertId(),
                            seat.getSeatNumber(),
                            seat.getPrice()
                    );
                    availableSeat.assignId(seat.getId());
                    seatReservationRepository.save(availableSeat);
                }
            }
        } else {
            // 만료된 예약 처리
            for (SeatReservation reservation : allReservedSeats) {
                if (reservation.isExpired()) {
                    // release 메서드로 상태 변경
                    reservation.release();
                    seatReservationRepository.save(reservation);
                }
            }
        }
    }

    /**
     * 스케줄러 - 30초마다 만료된 좌석 예약 자동 해제
     */
    @Scheduled(fixedDelay = 30000) // 30초마다 실행
    @Transactional
    public void releaseExpiredReservationsScheduler() {
        LocalDateTime now = LocalDateTime.now();

        try {
            // 배치 UPDATE로 만료된 예약 일괄 해제
            int releasedCount = seatReservationRepository.releaseExpiredReservationsBatch(now);

            if (releasedCount > 0) {
                logger.info("만료된 좌석 예약 {}개를 자동 해제했습니다. 실행 시간: {}", releasedCount, now);
            }

        } catch (Exception e) {
            logger.error("만료된 좌석 예약 해제 중 오류 발생", e);
        }
    }


    private List<SeatReservation> findAllSeats() {
        // SeatReservationRepository에 findAll() 메서드 추가 필요
        try {
            return seatReservationRepository.findAll();
        } catch (Exception e) {
            // findAll이 없다면 빈 리스트 반환
            return List.of();
        }
    }

    /**
     * 수동 실행용 메서드 (관리자 기능 또는 테스트용)
     */
    @Transactional
    public int expireReservationsManually() {
        LocalDateTime now = LocalDateTime.now();
        int releasedCount = seatReservationRepository.releaseExpiredReservationsBatch(now);

        logger.info("수동으로 만료된 좌석 예약 {}개를 해제했습니다. 실행 시간: {}", releasedCount, now);
        return releasedCount;
    }

    /**
     * 특정 시간 이전의 예약들을 강제로 만료시키는 메서드 (테스트용)
     */
    @Transactional
    public int forceExpireReservationsBefore(LocalDateTime cutoffTime) {
        // 이 메서드는 테스트에서 시간을 조작할 때 사용
        int releasedCount = seatReservationRepository.releaseExpiredReservationsBatch(cutoffTime);

        logger.info("{}ㅇ만 이전 좌석 예약 {}개를 강제 해제했습니다.", cutoffTime, releasedCount);
        return releasedCount;
    }
}
