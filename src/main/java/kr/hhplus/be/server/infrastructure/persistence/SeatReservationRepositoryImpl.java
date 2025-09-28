package kr.hhplus.be.server.infrastructure.persistence;
import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.model.SeatStatus;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import kr.hhplus.be.server.infrastructure.persistence.entity.SeatReservationEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository("seatReservationRepositoryImpl")
public class SeatReservationRepositoryImpl implements SeatReservationRepository {

    private final SeatReservationJpaRepository jpaRepository;

    public SeatReservationRepositoryImpl(SeatReservationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public SeatReservation save(SeatReservation reservation) {
        SeatReservationEntity entity = toEntity(reservation);
        SeatReservationEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<SeatReservation> findById(Long id) {
        return jpaRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public Optional<SeatReservation> findByConcertIdAndSeatNumber(Long concertId, Integer seatNumber) {
        return jpaRepository.findByConcertIdAndSeatNumber(concertId, seatNumber)
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<SeatReservation> findByConcertIdAndSeatNumberWithLock(Long concertId, Integer seatNumber) {
        return jpaRepository.findByConcertIdAndSeatNumberWithLock(concertId, seatNumber)
                .map(this::toDomain);
    }

    @Override
    public List<SeatReservation> findByConcertIdAndStatus(Long concertId, SeatStatus status) {
        return List.of();
    }

    @Override
    public List<SeatReservation> findAvailableSeats(Long concertId) {
        return jpaRepository.findAvailableSeats(concertId, LocalDateTime.now())
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<SeatReservation> findExpiredReservations(LocalDateTime now) {
        List<SeatReservationEntity> entities = jpaRepository.findExpiredReservations(now);
        return entities.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void releaseExpiredReservations(LocalDateTime now) {
        jpaRepository.releaseExpiredReservations(now);
    }

    @Override
    public List<SeatReservation> findByUserIdAndStatus(Long userId, SeatStatus status) {
        return jpaRepository.findByUserIdAndStatus(userId, status)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<SeatReservation> findByConcertIdAndSeatNumberForUpdate(Long concertId, Integer seatNumber) {
        return jpaRepository.findAndLockByConcertIdAndSeatNumber(concertId, seatNumber)
                .map(this::toDomain);
    }

    @Override
    public List<SeatReservation> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    // Entity → Domain 변환
    private SeatReservation toDomain(SeatReservationEntity entity) {
        SeatReservation reservation;

        switch (entity.getStatus()) {
            case AVAILABLE:
                reservation = SeatReservation.createAvailableSeat(
                        entity.getConcertId(),
                        entity.getSeatNumber(),
                        entity.getPrice()
                );
                break;
            case RESERVED:
                // 기존 시간 정보를 그대로 복원
                reservation = SeatReservation.createWithTimes(
                        entity.getConcertId(),
                        entity.getSeatNumber(),
                        entity.getUserId(),
                        entity.getPrice(),
                        entity.getReservedAt(),
                        entity.getExpiresAt()  // Entity의 실제 만료 시간 사용
                );
                break;
            case SOLD:
                reservation = SeatReservation.createConfirmedReservation(
                        entity.getConcertId(),
                        entity.getSeatNumber(),
                        entity.getUserId(),
                        entity.getPrice()
                );
                break;
            default:
                throw new IllegalStateException("Unknown seat status: " + entity.getStatus());
        }

        reservation.assignId(entity.getId());
        return reservation;
    }

    // Domain → Entity 변환
    private SeatReservationEntity toEntity(SeatReservation domain) {
        SeatReservationEntity entity = new SeatReservationEntity(
                domain.getConcertId(),
                domain.getSeatNumber(),
                domain.getUserId(),
                domain.getStatus(),
                domain.getReservedAt(),
                domain.getExpiresAt(),
                domain.getPrice()
        );
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }

        return entity;
    }
}
