package kr.hhplus.be.server.infrastructure.persistence;

import kr.hhplus.be.server.domain.model.ConcertDate;
import kr.hhplus.be.server.domain.model.ConcertDate.ConcertDateStatus;
import kr.hhplus.be.server.domain.repository.ConcertDateRepository;
import kr.hhplus.be.server.infrastructure.persistence.entity.ConcertDateEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.ConcertEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository("concertDateRepositoryImpl")
@Primary
@Transactional(readOnly = true)
public class ConcertDateRepositoryImpl implements ConcertDateRepository {

    private final SpringDataConcertDateRepository jpaRepository;
    private final SpringDataConcertRepository concertJpaRepository;

    public ConcertDateRepositoryImpl(SpringDataConcertDateRepository jpaRepository, SpringDataConcertRepository concertJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.concertJpaRepository = concertJpaRepository;
    }

    @Override
    @Transactional
    public ConcertDate save(ConcertDate concertDate) {
        ConcertDateEntity entity = toEntity(concertDate);
        ConcertDateEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public List<ConcertDate> saveAll(List<ConcertDate> concertDates) {
        return List.of();
    }

    @Override
    public Optional<ConcertDate> findById(Long id) {
        return jpaRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public List<ConcertDate> findAll() {
        return jpaRepository.findAll()
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
        return jpaRepository.findByConcert_Id(concertId)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ConcertDate> findByConcertIdAndDateTime(Long concertId, LocalDateTime dateTime) {
        return jpaRepository.findByConcert_IdAndConcertDateTime(concertId, dateTime)
                .map(this::toDomain);
    }

    @Override
    public List<ConcertDate> findAvailableDatesByMonth(LocalDate month, int limit) {
        LocalDateTime startOfMonth = month.atStartOfDay();
        LocalDateTime endOfMonth = month.plusMonths(1).atStartOfDay().minusSeconds(1);

        Pageable pageable = PageRequest.of(0, limit);

        return jpaRepository.findAvailableDatesByDateRange(startOfMonth, endOfMonth, pageable)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertDate> findAllAvailableDates(int limit) {
        Pageable pageable = PageRequest.of(0, limit);

        return jpaRepository.findAvailableDates(LocalDateTime.now(), pageable)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertDate> findByStatus(ConcertDateStatus status) {
        return jpaRepository.findByStatus(status)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertDate> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return jpaRepository.findByConcertDateTimeBetween(startDate, endDate)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertDate> findUpcomingDates(LocalDateTime fromDate, int limit) {
        Pageable pageable = PageRequest.of(0, limit);

        return jpaRepository.findUpcomingDates(fromDate, pageable)
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
            jpaRepository.deleteById(concertDate.getId());
        }
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
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
    public void deleteAllInBatch() {

    }

    @Override
    public void deleteAllInBatch(List<ConcertDate> concertDates) {

    }

    @Override
    public void deleteAllByIdInBatch(List<Long> ids) {

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
    private ConcertDateEntity toEntity(ConcertDate domain) {
        ConcertDateEntity entity = new ConcertDateEntity(
                domain.getConcertDateTime(),
                domain.getStartTime(),
                domain.getEndTime(),
                domain.getTotalSeats(),
                domain.getAvailableSeats(),
                domain.getStatus()
        );

        // ID가 있는 경우 (업데이트)
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        if (domain.getConcertId() != null) {
            ConcertEntity concertEntity = concertJpaRepository.findById(domain.getConcertId())
                    .orElseThrow(() -> new IllegalArgumentException("Concert not found with id: " + domain.getConcertId()));
            entity.setConcert(concertEntity); // ConcertDateEntity에 setConcert() 메서드가 있다고 가정
        }

        return entity;
    }

    private ConcertDate toDomain(ConcertDateEntity entity) {
        ConcertDate domain = ConcertDate.create(
                entity.getConcertId(), // 편의 메서드 사용
                entity.getConcertDateTime(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getTotalSeats()
        );

        // 기술적 필드 할당
        domain.assignTechnicalFields(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );

        // 상태 복원
        if (entity.getStatus() != ConcertDateStatus.SCHEDULED) {
            // 상태에 따른 적절한 메서드 호출
            switch (entity.getStatus()) {
                case AVAILABLE:
                    domain.openBooking();
                    break;
                case CANCELLED:
                    domain.cancel();
                    break;
                case COMPLETED:
                    // 완료 상태는 시간 체크 때문에 직접 설정하기 어려움
                    // 필요시 별도 메서드 추가
                    break;
                case SOLD_OUT:
                    // availableSeats 조정으로 자동 처리됨
                    break;
            }
        }

        // availableSeats 복원
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