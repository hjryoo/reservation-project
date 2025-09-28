package kr.hhplus.be.server.infrastructure.persistence;

import kr.hhplus.be.server.domain.model.ConcertDate;
import kr.hhplus.be.server.domain.model.ConcertDate.ConcertDateStatus;
import kr.hhplus.be.server.domain.repository.ConcertDateRepository;
import kr.hhplus.be.server.infrastructure.persistence.entity.ConcertDateEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository("legacyConcertDateRepository")
@Transactional(readOnly = true)
public class ConcertJpaRepository implements ConcertDateRepository {

    private final SpringDataConcertDateRepository concertDateRepo;

    public ConcertJpaRepository(SpringDataConcertDateRepository concertDateRepo) {
        this.concertDateRepo = concertDateRepo;
    }

    @Override
    @Transactional
    public ConcertDate save(ConcertDate concertDate) {
        ConcertDateEntity entity = toEntity(concertDate);
        ConcertDateEntity savedEntity = concertDateRepo.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public List<ConcertDate> saveAll(List<ConcertDate> concertDates) {
        return List.of();
    }

    @Override
    public Optional<ConcertDate> findById(Long id) {
        return concertDateRepo.findById(id)
                .map(this::toDomain);
    }

    @Override
    public List<ConcertDate> findAll() {
        return concertDateRepo.findAll()
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertDate> findAllById(List<Long> ids) {
        return List.of();
    }

    @Override
    public List<ConcertDate> findByConcertId(Long concertId) {
        return concertDateRepo.findByConcert_Id(concertId)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ConcertDate> findByConcertIdAndDateTime(Long concertId, LocalDateTime dateTime) {
        return concertDateRepo.findByConcert_IdAndConcertDateTime(concertId, dateTime)
                .map(this::toDomain);
    }

    @Override
    public List<ConcertDate> findAvailableDatesByMonth(LocalDate month, int limit) {
        LocalDateTime startOfMonth = month.atStartOfDay();
        LocalDateTime endOfMonth = month.plusMonths(1).atStartOfDay().minusSeconds(1);

        Pageable pageable = PageRequest.of(0, limit);
        List<ConcertDateEntity> entities = concertDateRepo
                .findAvailableDatesByDateRange(startOfMonth, endOfMonth, pageable);

        return entities.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertDate> findAllAvailableDates(int limit) {
        // LIMIT 문제 해결: Pageable 사용
        Pageable pageable = PageRequest.of(0, limit);
        List<ConcertDateEntity> entities = concertDateRepo
                .findAvailableDates(LocalDateTime.now(), pageable);

        return entities.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertDate> findByStatus(ConcertDateStatus status) {
        return concertDateRepo.findByStatus(status)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertDate> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return concertDateRepo.findByConcertDateTimeBetween(startDate, endDate)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertDate> findUpcomingDates(LocalDateTime fromDate, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return concertDateRepo.findUpcomingDates(fromDate, pageable)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int bulkUpdateStatus(ConcertDateStatus newStatus, ConcertDateStatus oldStatus) {
        return 0;
    }

    @Override
    public int bulkUpdateAvailableSeats(Long concertId, int newAvailableSeats) {
        return 0;
    }

    @Override
    @Transactional
    public void delete(ConcertDate concertDate) {
        if (concertDate.getId() != null) {
            concertDateRepo.deleteById(concertDate.getId());
        }
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        concertDateRepo.deleteById(id);
    }

    @Override
    public void deleteAll() {

    }

    @Override
    public void deleteAll(List<ConcertDate> concertDates) {

    }

    @Override
    public void deleteAllById(List<Long> ids) {

    }

    @Override
    public boolean existsById(Long id) {
        return concertDateRepo.existsById(id);
    }

    @Override
    public long count() {
        return concertDateRepo.count();
    }

    // Entity ↔ Domain 변환 메서드
    private ConcertDateEntity toEntity(ConcertDate domain) {
        ConcertDateEntity entity = new ConcertDateEntity(
                domain.getConcertDateTime(),
                domain.getStartTime(),
                domain.getEndTime(),
                domain.getTotalSeats(),
                domain.getAvailableSeats(),
                domain.getStatus()
        );

        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }

        return entity;
    }

    private ConcertDate toDomain(ConcertDateEntity entity) {
        ConcertDate domain = ConcertDate.create(
                entity.getConcertId(), // Long 타입
                entity.getConcertDateTime(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getTotalSeats()
        );

        domain.assignTechnicalFields(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );

        // 상태 및 좌석 수 복원
        restoreDomainState(domain, entity);

        return domain;
    }

    private void restoreDomainState(ConcertDate domain, ConcertDateEntity entity) {
        if (entity.getStatus() != ConcertDateStatus.SCHEDULED) {
            switch (entity.getStatus()) {
                case AVAILABLE:
                    if (domain.getStatus() == ConcertDateStatus.SCHEDULED) {
                        domain.openBooking();
                    }
                    break;
                case CANCELLED:
                    domain.cancel();
                    break;
                case COMPLETED:
                    domain.complete();
                    break;
                case SOLD_OUT:
                    // availableSeats 조정으로 자동 처리됨
                    break;
            }
        }

        int currentAvailable = domain.getAvailableSeats();
        int targetAvailable = entity.getAvailableSeats();

        if (targetAvailable < currentAvailable) {
            for (int i = 0; i < currentAvailable - targetAvailable; i++) {
                domain.decreaseAvailableSeats();
            }
        } else if (targetAvailable > currentAvailable) {
            for (int i = 0; i < targetAvailable - currentAvailable; i++) {
                domain.increaseAvailableSeats();
            }
        }
    }

    @Override
    @Transactional
    public void deleteAllInBatch() {
        concertDateRepo.deleteAllInBatch();
    }

    @Override
    @Transactional
    public void deleteAllInBatch(List<ConcertDate> concertDates) {
        List<ConcertDateEntity> entities = concertDates.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
        concertDateRepo.deleteAllInBatch(entities);
    }

    @Override
    @Transactional
    public void deleteAllByIdInBatch(List<Long> ids) {
        concertDateRepo.deleteAllByIdInBatch(ids);
    }
}