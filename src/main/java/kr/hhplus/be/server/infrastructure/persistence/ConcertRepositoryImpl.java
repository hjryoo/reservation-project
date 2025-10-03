package kr.hhplus.be.server.infrastructure.persistence;

import kr.hhplus.be.server.domain.model.Concert;
import kr.hhplus.be.server.domain.model.ConcertDate;
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
        ConcertEntity entity = toEntity(concert);
        ConcertEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<Concert> findById(Long id) {
        return jpaRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public List<Concert> findAll() {
        return jpaRepository.findAll()
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Concert> findAvailableConcerts(ConcertStatus status, LocalDateTime currentDate) {
        return jpaRepository.findByStatusAndCreatedAtAfter(status, currentDate)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Concert> findConcertsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        // ConcertDate를 통해 조회하는 것이 더 정확하므로,
        // 여기서는 생성일 기준으로 대체 (실제로는 ConcertDateRepository 사용 권장)
        return jpaRepository.findAll()
                .stream()
                .filter(entity -> entity.getCreatedAt().isAfter(startDate.minusHours(1)) &&
                        entity.getCreatedAt().isBefore(endDate.plusHours(1)))
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Concert> findByArtistAndFutureDates(String artist, LocalDateTime currentDate) {
        return jpaRepository.findByArtistAndCreatedAtAfter(artist, currentDate)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Concert> findByArtist(String artist) {
        return jpaRepository.findByArtist(artist)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Concert> findByVenue(String venue) {
        return jpaRepository.findByVenue(venue)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Concert> findByStatus(ConcertStatus status) {
        return jpaRepository.findByStatus(status)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void delete(Concert concert) {
        if (concert.getId() != null) {
            jpaRepository.deleteById(concert.getId());
        }
    }

    @Transactional
    @Override
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

    // Entity ↔ Domain 변환 메서드
    private ConcertEntity toEntity(Concert domain) {
        ConcertEntity entity = new ConcertEntity(
                domain.getTitle(),
                domain.getArtist(),
                domain.getVenue(),
                domain.getTotalSeats(),
                domain.getAvailableSeats(),
                domain.getPrice(),
                domain.getStatus()
        );

        // ID가 있는 경우 (업데이트)
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }

        return entity;
    }

    private Concert toDomain(ConcertEntity entity) {
        Concert domain = Concert.create(
                entity.getTitle(),
                entity.getArtist(),
                entity.getVenue(),
                entity.getTotalSeats(),
                entity.getPrice()
        );

        // 기술적 필드 할당
        domain.assignTechnicalFields(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );

        // 상태 및 가변 필드 복원
        if (entity.getStatus() != domain.getStatus()) {
            domain.updateStatus(entity.getStatus());
        }

        // availableSeats가 다른 경우 조정
        int seatDifference = entity.getAvailableSeats() - domain.getAvailableSeats();
        if (seatDifference < 0) {
            for (int i = 0; i < Math.abs(seatDifference); i++) {
                domain.decreaseAvailableSeats();
            }
        } else if (seatDifference > 0) {
            for (int i = 0; i < seatDifference; i++) {
                domain.increaseAvailableSeats();
            }
        }

        return domain;
    }
}