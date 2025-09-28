package kr.hhplus.be.server.application.service;

import kr.hhplus.be.server.domain.model.Concert;
import kr.hhplus.be.server.domain.model.ConcertDate;
import kr.hhplus.be.server.domain.model.ConcertStatus;
import kr.hhplus.be.server.domain.repository.ConcertDateRepository;
import kr.hhplus.be.server.domain.repository.ConcertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service("concertService")
@Transactional(readOnly = true)
public class ConcertService {

    private final ConcertRepository concertRepository;
    private final ConcertDateRepository concertDateRepository;

    public ConcertService(ConcertRepository concertRepository,
                          ConcertDateRepository concertDateRepository) {
        this.concertRepository = concertRepository;
        this.concertDateRepository = concertDateRepository;
    }

    // ===== Concert 기본 정보 관리 =====

    public List<Concert> getAvailableConcerts() {
        return concertRepository.findAvailableConcerts(
                ConcertStatus.AVAILABLE,
                LocalDateTime.now()
        );
    }

    public Concert getConcertById(Long concertId) {
        return concertRepository.findById(concertId)
                .orElseThrow(() -> new IllegalArgumentException("콘서트를 찾을 수 없습니다: " + concertId));
    }

    public List<Concert> getConcertsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작 날짜는 종료 날짜보다 이전이어야 합니다.");
        }
        return concertRepository.findConcertsByDateRange(startDate, endDate);
    }

    public List<Concert> getConcertsByArtist(String artist) {
        if (artist == null || artist.trim().isEmpty()) {
            throw new IllegalArgumentException("아티스트명은 필수입니다.");
        }
        return concertRepository.findByArtistAndFutureDates(artist, LocalDateTime.now());
    }

    public List<Concert> getConcertsByVenue(String venue) {
        if (venue == null || venue.trim().isEmpty()) {
            throw new IllegalArgumentException("장소명은 필수입니다.");
        }
        return concertRepository.findByVenue(venue);
    }

    public List<Concert> getAllConcerts() {
        return concertRepository.findAll();
    }

    // ===== ConcertDate 일정 관리 =====

    public List<ConcertDate> getAvailableDates(int limit) {
        return concertDateRepository.findAllAvailableDates(limit);
    }

    public List<ConcertDate> getAvailableDatesByMonth(LocalDate month, int limit) {
        if (month == null) {
            throw new IllegalArgumentException("조회할 월은 필수입니다.");
        }
        return concertDateRepository.findAvailableDatesByMonth(month, limit);
    }

    public List<ConcertDate> getConcertDatesByConcertId(Long concertId) {
        return concertDateRepository.findByConcertId(concertId);
    }

    public ConcertDate getConcertDate(Long concertId, LocalDateTime dateTime) {
        return concertDateRepository.findByConcertIdAndDateTime(concertId, dateTime)
                .orElseThrow(() -> new IllegalArgumentException("해당 공연 일정을 찾을 수 없습니다."));
    }

    public List<ConcertDate> getConcertDatesByRange(LocalDateTime startDate, LocalDateTime endDate) {
        return concertDateRepository.findByDateRange(startDate, endDate);
    }

    public List<ConcertDate> getUpcomingDates(LocalDateTime fromDate, int limit) {
        return concertDateRepository.findUpcomingDates(fromDate, limit);
    }

    public List<ConcertDate> getAllConcertDates(int limit) {
        return concertDateRepository.findUpcomingDates(LocalDateTime.now().minusYears(1), limit);
    }
}