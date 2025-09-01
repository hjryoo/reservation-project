package kr.hhplus.be.server.domain.model;

import java.time.LocalDateTime;
import java.util.List;

public class Concert {
    private final String concertId;
    private final String title;
    private final String venue;
    private final int totalSeats;
    private final long price;
    private final List<ConcertDate> concertDates;

    private Concert(String concertId, String title, String venue,
                    int totalSeats, long price, List<ConcertDate> concertDates) {
        this.concertId = concertId;
        this.title = title;
        this.venue = venue;
        this.totalSeats = totalSeats;
        this.price = price;
        this.concertDates = concertDates;
    }

    public static Concert create(String concertId, String title, String venue,
                                 int totalSeats, long price, List<ConcertDate> concertDates) {
        return new Concert(concertId, title, venue, totalSeats, price, concertDates);
    }

    // 예약 가능한 콘서트인지 검증
    public boolean isAvailableForReservation() {
        return concertDates.stream()
                .anyMatch(ConcertDate::hasAvailableSeats);
    }

    // 특정 날짜의 예약 가능 좌석 수 계산
    public int getAvailableSeatsForDate(LocalDateTime concertDateTime) {
        return concertDates.stream()
                .filter(date -> date.getConcertDateTime().equals(concertDateTime))
                .findFirst()
                .map(ConcertDate::getAvailableSeats)
                .orElse(0);
    }

    // Getters
    public String getConcertId() { return concertId; }
    public String getTitle() { return title; }
    public String getVenue() { return venue; }
    public int getTotalSeats() { return totalSeats; }
    public long getPrice() { return price; }
    public List<ConcertDate> getConcertDates() { return concertDates; }
}