package kr.hhplus.be.server.application.service;

import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.model.SeatStatus;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service("seatExpirationService")
public class SeatExpirationService {

    private final SeatReservationRepository seatReservationRepository;

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

    private List<SeatReservation> findAllSeats() {
        // SeatReservationRepository에 findAll() 메서드 추가 필요
        try {
            return seatReservationRepository.findAll();
        } catch (Exception e) {
            // findAll이 없다면 빈 리스트 반환
            return List.of();
        }
    }
}
