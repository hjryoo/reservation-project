package kr.hhplus.be.server.infrastructure.persistence;

import kr.hhplus.be.server.domain.model.Concert;
import kr.hhplus.be.server.domain.model.ConcertStatus;
import kr.hhplus.be.server.domain.repository.ConcertRepository;
import kr.hhplus.be.server.infrastructure.persistence.entity.ConcertEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository("concertRepositoryImpl")
@Transactional(readOnly = true)
public class ConcertRepositoryImpl implements ConcertRepository {

    private final SpringDataConcertRepository jpaRepository;

    public ConcertRepositoryImpl(SpringDataConcertRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Concert save(Concert concert) {
        if (concert.getId() != null) {
            ConcertEntity existingEntity = jpaRepository.findById(concert.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "수정하려는 콘서트를 찾을 수 없습니다. ID: " + concert.getId()));

            updateEntity(existingEntity, concert);
            ConcertEntity savedEntity = jpaRepository.save(existingEntity);
            return toDomain(savedEntity);
        }

        ConcertEntity entity = toEntity(concert);
        ConcertEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    @Transactional
    public int decreaseAvailableSeatsAtomically(Long concertId) {
        return jpaRepository.decreaseAvailableSeatsAtomically(concertId);
    }

    /**
     * Entity 업데이트 (bookingOpenAt 필드 포함)
     */
    private void updateEntity(ConcertEntity entity, Concert concert) {
        entity.setTitle(concert.getTitle());
        entity.setArtist(concert.getArtist());
        entity.setVenue(concert.getVenue());
        entity.setTotalSeats(concert.getTotalSeats());
        entity.setAvailableSeats(concert.getAvailableSeats());
        entity.setPrice(concert.getPrice());
        entity.setStatus(concert.getStatus());
        entity.setBookingOpenAt(concert.getBookingOpenAt());
        entity.setSoldOutAt(concert.getSoldOutAt());
    }

    /**
     * Domain to Entity
     */
    private ConcertEntity toEntity(Concert concert) {
        ConcertEntity entity = new ConcertEntity(
                concert.getTitle(),
                concert.getArtist(),
                concert.getVenue(),
                concert.getTotalSeats(),
                concert.getAvailableSeats(),
                concert.getPrice(),
                concert.getStatus()
        );

        if (concert.getId() != null) {
            entity.setId(concert.getId());
        }

        entity.setBookingOpenAt(concert.getBookingOpenAt());
        entity.setSoldOutAt(concert.getSoldOutAt());

        return entity;
    }

    /**
     * Entity to Domain
     */
    private Concert toDomain(ConcertEntity entity) {
        Concert concert = Concert.create(
                entity.getTitle(),
                entity.getArtist(),
                entity.getVenue(),
                entity.getTotalSeats(),
                entity.getPrice()
        );

        concert.assignTechnicalFields(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );

        concert.assignSoldOutFields(
                entity.getBookingOpenAt(),
                entity.getSoldOutAt()
        );

        if (entity.getStatus() != concert.getStatus()) {
            concert.updateStatus(entity.getStatus());
        }

        if (entity.getAvailableSeats() != null &&
                !entity.getAvailableSeats().equals(entity.getTotalSeats())) {
            int difference = entity.getTotalSeats() - entity.getAvailableSeats();
            for (int i = 0; i < difference; i++) {
                concert.decreaseAvailableSeats();
            }
        }

        return concert;
    }

    @Override
    public Optional<Concert> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Concert> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Concert> findAvailableConcerts(ConcertStatus status, LocalDateTime currentDate) {
        return jpaRepository.findByStatus(status).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Concert> findConcertsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .filter(c -> c.getCreatedAt().isAfter(startDate) && c.getCreatedAt().isBefore(endDate))
                .collect(Collectors.toList());
    }

    @Override
    public List<Concert> findByArtistAndFutureDates(String artist, LocalDateTime currentDate) {
        return jpaRepository.findByArtist(artist).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Concert> findByArtist(String artist) {
        return jpaRepository.findByArtist(artist).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Concert> findByVenue(String venue) {
        return jpaRepository.findByVenue(venue).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Concert> findByStatus(ConcertStatus status) {
        return jpaRepository.findByStatus(status).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Concert> findSoldOutConcerts() {
        return jpaRepository.findByStatus(ConcertStatus.SOLD_OUT).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Concert concert) {
        if (concert.getId() != null) {
            jpaRepository.deleteById(concert.getId());
        }
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }
}
