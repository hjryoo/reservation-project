package kr.hhplus.be.server.interfaces.api;

import kr.hhplus.be.server.application.service.ConcertService;
import kr.hhplus.be.server.domain.model.Concert;
import kr.hhplus.be.server.domain.model.ConcertDate;
import kr.hhplus.be.server.interfaces.dto.ConcertResponse;
import kr.hhplus.be.server.interfaces.dto.ConcertDateResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController("concertController")
@RequestMapping("/api/v1/concerts")
@Validated
public class ConcertController {

    private final ConcertService concertService;
    public ConcertController(ConcertService concertService) {
        this.concertService = concertService;
    }

    // ===== Concert 기본 정보 API =====

    /**
     * 예약 가능한 콘서트 목록 조회
     */
    @GetMapping("/available")
    public ResponseEntity<List<ConcertResponse>> getAvailableConcerts() {
        List<Concert> concerts = concertService.getAvailableConcerts();
        List<ConcertResponse> responses = concerts.stream()
                .map(ConcertResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * 콘서트 상세 정보 조회
     */
    @GetMapping("/{concertId}")
    public ResponseEntity<ConcertResponse> getConcertById(
            @PathVariable @NotNull @Positive Long concertId) {
        Concert concert = concertService.getConcertById(concertId);
        return ResponseEntity.ok(ConcertResponse.from(concert));
    }

    /**
     * 아티스트별 콘서트 조회
     */
    @GetMapping("/search/artist")
    public ResponseEntity<List<ConcertResponse>> getConcertsByArtist(
            @RequestParam @NotNull String artist) {
        List<Concert> concerts = concertService.getConcertsByArtist(artist);
        List<ConcertResponse> responses = concerts.stream()
                .map(ConcertResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * 장소별 콘서트 조회
     */
    @GetMapping("/search/venue")
    public ResponseEntity<List<ConcertResponse>> getConcertsByVenue(
            @RequestParam @NotNull String venue) {
        List<Concert> concerts = concertService.getConcertsByVenue(venue);
        List<ConcertResponse> responses = concerts.stream()
                .map(ConcertResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // ===== ConcertDate 일정 관리 API =====

    /**
     * 전체 예약 가능한 공연 일정 조회 (최신순)
     */
    @GetMapping("/dates/available")
    public ResponseEntity<List<ConcertDateResponse>> getAvailableDates(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        List<ConcertDate> concertDates = concertService.getAvailableDates(limit);
        List<ConcertDateResponse> responses = concertDates.stream()
                .map(ConcertDateResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * 월별 예약 가능한 공연 일정 조회
     */
    @GetMapping("/dates/monthly")
    public ResponseEntity<List<ConcertDateResponse>> getAvailableDatesByMonth(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") LocalDate month,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        List<ConcertDate> concertDates = concertService.getAvailableDatesByMonth(month, limit);
        List<ConcertDateResponse> responses = concertDates.stream()
                .map(ConcertDateResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * 특정 콘서트의 모든 공연 일정 조회
     */
    @GetMapping("/{concertId}/dates")
    public ResponseEntity<List<ConcertDateResponse>> getConcertDates(
            @PathVariable @NotNull @Positive Long concertId) {
        List<ConcertDate> concertDates = concertService.getConcertDatesByConcertId(concertId);
        List<ConcertDateResponse> responses = concertDates.stream()
                .map(ConcertDateResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * 특정 콘서트의 특정 일정 상세 조회
     */
    @GetMapping("/{concertId}/dates/{dateTime}")
    public ResponseEntity<ConcertDateResponse> getConcertDate(
            @PathVariable @NotNull @Positive Long concertId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime) {
        ConcertDate concertDate = concertService.getConcertDate(concertId, dateTime);
        return ResponseEntity.ok(ConcertDateResponse.from(concertDate));
    }

    /**
     * 날짜 범위로 공연 일정 조회
     */
    @GetMapping("/dates/search/range")
    public ResponseEntity<List<ConcertDateResponse>> getConcertDatesByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작 날짜는 종료 날짜보다 이전이어야 합니다.");
        }

        List<ConcertDate> concertDates = concertService.getConcertDatesByRange(startDate, endDate);
        List<ConcertDateResponse> responses = concertDates.stream()
                .map(ConcertDateResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * 향후 예정된 공연 일정 조회
     */
    @GetMapping("/dates/upcoming")
    public ResponseEntity<List<ConcertDateResponse>> getUpcomingDates(
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        List<ConcertDate> concertDates = concertService.getUpcomingDates(LocalDateTime.now(), limit);
        List<ConcertDateResponse> responses = concertDates.stream()
                .map(ConcertDateResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // ===== 관리자용 API =====

    /**
     * 전체 콘서트 조회 (관리자용)
     */
    @GetMapping("/admin/all")
    public ResponseEntity<List<ConcertResponse>> getAllConcerts() {
        List<Concert> concerts = concertService.getAllConcerts();
        List<ConcertResponse> responses = concerts.stream()
                .map(ConcertResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * 전체 공연 일정 조회 (관리자용)
     */
    @GetMapping("/admin/dates/all")
    public ResponseEntity<List<ConcertDateResponse>> getAllConcertDates(
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        List<ConcertDate> concertDates = concertService.getAllConcertDates(limit);
        List<ConcertDateResponse> responses = concertDates.stream()
                .map(ConcertDateResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }
}