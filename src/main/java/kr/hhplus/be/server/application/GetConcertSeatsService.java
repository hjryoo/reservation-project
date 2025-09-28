package kr.hhplus.be.server.application;

import kr.hhplus.be.server.domain.model.Concert;
import kr.hhplus.be.server.domain.model.ConcertDate;
import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.model.SeatStatus;
import kr.hhplus.be.server.domain.repository.ConcertRepository;
import kr.hhplus.be.server.domain.repository.ConcertDateRepository;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import kr.hhplus.be.server.interfaces.dto.ConcertSeatsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GetConcertSeatsService {

    private final ConcertRepository concertRepository;
    private final ConcertDateRepository concertDateRepository;
    private final SeatReservationRepository seatReservationRepository;

    public GetConcertSeatsService(ConcertRepository concertRepository,
                                  ConcertDateRepository concertDateRepository,
                                  SeatReservationRepository seatReservationRepository) {
        this.concertRepository = concertRepository;
        this.concertDateRepository = concertDateRepository;
        this.seatReservationRepository = seatReservationRepository;
    }

    public ConcertSeatsResponse getConcertSeats(Long concertId, String dateStr) {
        // 입력값 검증
        validateInput(concertId, dateStr);

        // 날짜 파싱
        LocalDateTime concertDateTime = parseDate(dateStr);

        // 콘서트 기본 정보 조회
        Concert concert = getConcert(concertId);

        // 콘서트 일정 정보 조회
        ConcertDate concertDate = getConcertDate(concertId, concertDateTime);

        // 좌석 정보 조회
        List<SeatReservation> seatReservations = getSeatReservations(concertId);

        // 좌석 상태별 개수 집계
        SeatSummary seatSummary = calculateSeatSummary(seatReservations, concertDate.getTotalSeats());

        // 응답 객체 생성
        return createResponse(concert, concertDate, seatReservations, seatSummary, dateStr);
    }

    private void validateInput(Long concertId, String dateStr) {
        if (concertId == null || concertId <= 0) {
            throw new IllegalArgumentException("유효한 콘서트 ID가 필요합니다.");
        }
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new IllegalArgumentException("날짜는 필수입니다.");
        }
    }

    private LocalDateTime parseDate(String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            return date.atStartOfDay(); // 00:00:00으로 설정
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. yyyy-MM-dd 형식으로 입력해주세요.");
        }
    }

    private Concert getConcert(Long concertId) {
        return concertRepository.findById(concertId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘서트입니다: " + concertId));
    }

    private ConcertDate getConcertDate(Long concertId, LocalDateTime dateTime) {
        return concertDateRepository.findByConcertIdAndDateTime(concertId, dateTime)
                .orElseThrow(() -> new IllegalArgumentException("해당 날짜에 콘서트 일정이 없습니다."));
    }

    private List<SeatReservation> getSeatReservations(Long concertId) {
        // 현재 구현된 Repository에 맞춰 조회
        // 실제로는 concertId + dateTime으로 조회해야 하지만,
        // 현재 구조에서는 concertId로만 조회 가능
        List<SeatReservation> availableSeats = seatReservationRepository.findByConcertIdAndStatus(concertId, SeatStatus.AVAILABLE);
        List<SeatReservation> reservedSeats = seatReservationRepository.findByConcertIdAndStatus(concertId, SeatStatus.RESERVED);
        List<SeatReservation> soldSeats = seatReservationRepository.findByConcertIdAndStatus(concertId, SeatStatus.SOLD);

        // 모든 상태의 좌석을 합쳐서 반환
        availableSeats.addAll(reservedSeats);
        availableSeats.addAll(soldSeats);

        return availableSeats;
    }

    private SeatSummary calculateSeatSummary(List<SeatReservation> seats, Integer totalSeats) {
        long availableCount = seats.stream()
                .mapToLong(seat -> seat.getStatus() == SeatStatus.AVAILABLE ? 1 : 0)
                .sum();

        long reservedCount = seats.stream()
                .mapToLong(seat -> seat.getStatus() == SeatStatus.RESERVED ? 1 : 0)
                .sum();

        long soldCount = seats.stream()
                .mapToLong(seat -> seat.getStatus() == SeatStatus.SOLD ? 1 : 0)
                .sum();

        return new SeatSummary(
                totalSeats != null ? totalSeats : seats.size(),
                (int) availableCount,
                (int) reservedCount,
                (int) soldCount
        );
    }

    private ConcertSeatsResponse createResponse(Concert concert, ConcertDate concertDate,
                                                List<SeatReservation> seats, SeatSummary summary,
                                                String dateStr) {
        // Concert 기본 정보
        ConcertInfo concertInfo = new ConcertInfo(
                concert.getId(),
                concert.getTitle(),
                dateStr,
                concert.getVenue(),
                concertDate.getStartTime().toLocalTime().toString(),
                concert.getPrice()
        );

        // 좌석 상세 정보
        List<SeatInfo> seatInfos = seats.stream()
                .map(this::toSeatInfo)
                .sorted((s1, s2) -> Integer.compare(s1.getSeatNumber(), s2.getSeatNumber())) // 좌석 번호순 정렬
                .collect(Collectors.toList());

        return new ConcertSeatsResponse(concertInfo, seatInfos, summary);
    }

    private SeatInfo toSeatInfo(SeatReservation seat) {
        return new SeatInfo(
                seat.getSeatNumber(),
                seat.getStatus().name(),
                generateSeatPosition(seat.getSeatNumber()), // 좌석 위치 생성 로직
                seat.getExpiresAt() != null ? seat.getExpiresAt().toString() : null
        );
    }

    private String generateSeatPosition(Integer seatNumber) {
        // 좌석 번호를 기반으로 위치 생성 (예: 1-10 -> A열, 11-20 -> B열)
        if (seatNumber == null) return "Unknown";

        int row = (seatNumber - 1) / 10; // 10개씩 한 줄로 가정
        int seat = (seatNumber - 1) % 10 + 1;
        char rowChar = (char) ('A' + row);

        return rowChar + "-" + seat;
    }

    // Inner Classes (응답 객체들)
    public static class SeatSummary {
        private final int totalSeats;
        private final int availableSeats;
        private final int reservedSeats;
        private final int soldSeats;

        public SeatSummary(int totalSeats, int availableSeats, int reservedSeats, int soldSeats) {
            this.totalSeats = totalSeats;
            this.availableSeats = availableSeats;
            this.reservedSeats = reservedSeats;
            this.soldSeats = soldSeats;
        }

        public int getTotalSeats() { return totalSeats; }
        public int getAvailableSeats() { return availableSeats; }
        public int getReservedSeats() { return reservedSeats; }
        public int getSoldSeats() { return soldSeats; }
    }

    public static class ConcertInfo {
        private final Long concertId;
        private final String title;
        private final String date;
        private final String venue;
        private final String startTime;
        private final Long price;

        public ConcertInfo(Long concertId, String title, String date, String venue, String startTime, Long price) {
            this.concertId = concertId;
            this.title = title;
            this.date = date;
            this.venue = venue;
            this.startTime = startTime;
            this.price = price;
        }

        public Long getConcertId() { return concertId; }
        public String getTitle() { return title; }
        public String getDate() { return date; }
        public String getVenue() { return venue; }
        public String getStartTime() { return startTime; }
        public Long getPrice() { return price; }
    }

    public static class SeatInfo {
        private final Integer seatNumber;
        private final String status;
        private final String position;
        private final String reservedUntil;

        public SeatInfo(Integer seatNumber, String status, String position, String reservedUntil) {
            this.seatNumber = seatNumber;
            this.status = status;
            this.position = position;
            this.reservedUntil = reservedUntil;
        }

        public Integer getSeatNumber() { return seatNumber; }
        public String getStatus() { return status; }
        public String getPosition() { return position; }
        public String getReservedUntil() { return reservedUntil; }
    }
}
