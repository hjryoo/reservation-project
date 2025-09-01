package kr.hhplus.be.server.domain.model;

import java.time.LocalDateTime;

public class ConcertDate {
    private final String concertId;
    private final LocalDateTime concertDateTime;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final int totalSeats;
    private final int availableSeats;

    private ConcertDate(String concertId, LocalDateTime concertDateTime,
                        LocalDateTime startTime, LocalDateTime endTime,
                        int totalSeats, int availableSeats) {
        this.concertId = concertId;
        this.concertDateTime = concertDateTime;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
    }

    public static ConcertDate create(String concertId, LocalDateTime concertDateTime,
                                     LocalDateTime startTime, LocalDateTime endTime,
                                     int totalSeats, int availableSeats) {
        return new ConcertDate(concertId, concertDateTime, startTime, endTime, totalSeats, availableSeats);
    }

    // 예약 가능한 좌석이 있는지 확인
    public boolean hasAvailableSeats() {
        return availableSeats > 0;
    }

    // 예약 가능한 날짜인지 확인 (현재 시간 이후)
    public boolean isBookable() {
        return concertDateTime.isAfter(LocalDateTime.now()) && hasAvailableSeats();
    }

    // Getters
    public String getConcertId() { return concertId; }
    public LocalDateTime getConcertDateTime() { return concertDateTime; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public int getTotalSeats() { return totalSeats; }
    public int getAvailableSeats() { return availableSeats; }
}