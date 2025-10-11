package kr.hhplus.be.server.application.service;

import kr.hhplus.be.server.domain.model.Reservation;
import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.port.in.ReserveSeatUseCase;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import kr.hhplus.be.server.infrastructure.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좌석 예약 서비스 (분산락 적용)
 *
 * 분산락 적용 이유:
 * - 다중 인스턴스 환경에서 동일 좌석 중복 예약 방지
 * - DB 비관적 락보다 낮은 DB 부하
 * - 예약 프로세스 전체를 원자적으로 보호
 */
@Service("seatReservationService")
@RequiredArgsConstructor
public class SeatReservationService implements ReserveSeatUseCase {

    private final SeatReservationRepository seatReservationRepository;

    /**
     * 좌석 임시 예약 (분산락 적용)
     *
     * 락 키: seat:reservation:{concertId}:{seatNumber}
     * 락 범위: 좌석 조회 → 상태 검증 → 임시 예약 저장
     *
     * 중요: @DistributedLock이 @Transactional보다 먼저 실행되어
     * 락 획득 → 트랜잭션 시작 순서를 보장합니다.
     */
    @DistributedLock(
            key = "'seat:reservation:' + #concertId + ':' + #seatNumber",
            waitTime = 5L,
            leaseTime = 3L
    )
    @Transactional
    public SeatReservation reserveSeatTemporarily(Long concertId, Integer seatNumber, Long userId) {
        // 1. 좌석 조회 (비관적 락 제거 - 분산락으로 대체)
        SeatReservation seat = seatReservationRepository
                .findByConcertIdAndSeatNumber(concertId, seatNumber)
                .orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다: " + seatNumber));

        // 2. 예약 가능성 검증
        if (!seat.isAvailable()) {
            throw new IllegalStateException("이미 예약된 좌석입니다: " + seatNumber);
        }

        // 3. 임시 예약 처리
        seat.reserveTemporarily(userId);

        // 4. 저장 및 반환
        return seatReservationRepository.save(seat);
    }

    /**
     * 예약 확정 (분산락 적용)
     *
     * 락 키: seat:confirmation:{concertId}:{seatNumber}
     * 락 범위: 예약 조회 → 상태 검증 → 확정 처리
     */
    @DistributedLock(
            key = "'seat:confirmation:' + #concertId + ':' + #seatNumber",
            waitTime = 5L,
            leaseTime = 3L
    )
    @Transactional
    public SeatReservation confirmReservation(Long concertId, Integer seatNumber, Long userId) {
        // 1. 예약 조회
        SeatReservation seat = seatReservationRepository
                .findByConcertIdAndSeatNumber(concertId, seatNumber)
                .orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다."));

        // 2. 확정 가능 여부 검증
        if (!seat.canBeConfirmed(userId)) {
            throw new IllegalStateException("결제할 수 없는 예약입니다.");
        }

        // 3. 예약 확정
        seat.confirm();

        // 4. 저장 및 반환
        return seatReservationRepository.save(seat);
    }

    @Override
    public Reservation reserve(ReserveSeatCommand command) {
        // 분산락이 적용된 메서드 호출
        SeatReservation reservedSeat = reserveSeatTemporarily(
                command.concertId(),
                command.seatNumber(),
                command.userId()
        );

        return convertToReservation(reservedSeat);
    }

    private Reservation convertToReservation(SeatReservation seatReservation) {
        Reservation reservation = Reservation.create(
                seatReservation.getUserId(),
                seatReservation.getConcertId(),
                seatReservation.getSeatNumber(),
                seatReservation.getPrice()
        );
        reservation.assignId(seatReservation.getId());
        return reservation;
    }
}