package kr.hhplus.be.server.application;

import kr.hhplus.be.server.domain.model.ConcertDate;
import kr.hhplus.be.server.domain.repository.ConcertDateRepository;
import kr.hhplus.be.server.domain.repository.ConcertRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class GetAvailableDatesService {
    private final ConcertRepository concertRepository;
    private final ConcertDateRepository concertDateRepository;

    public GetAvailableDatesService(ConcertRepository concertRepository, ConcertDateRepository concertDateRepository) {
        this.concertRepository = concertRepository;
        this.concertDateRepository = concertDateRepository;
    }

    public List<AvailableDateResponse> getAvailableDates(String month, Integer limit) {
        List<ConcertDate> availableDates;
        int queryLimit = (limit != null) ? limit : 30;

        if (month != null && !month.isEmpty()) {
            LocalDate monthDate = LocalDate.parse(month + "-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            availableDates = concertDateRepository.findAvailableDatesByMonth(monthDate, queryLimit);
        } else {
            availableDates = concertDateRepository.findAllAvailableDates(queryLimit);
        }

        return availableDates.stream()
                .filter(ConcertDate::isBookable)
                .map(this::toAvailableDateResponse)
                .collect(Collectors.toList());
    }

    private AvailableDateResponse toAvailableDateResponse(ConcertDate concertDate) {
        return new AvailableDateResponse(
                concertDate.getConcertDateTime().toLocalDate().toString(),
                concertDate.getConcertId().toString(),
                "Concert Title",
                "Concert Venue",
                concertDate.getStartTime().toLocalTime().toString(),
                concertDate.getEndTime().toLocalTime().toString(),
                concertDate.getTotalSeats(),
                concertDate.getAvailableSeats(),
                10000L
        );
    }

    public static class AvailableDateResponse {
        private final String date;
        private final String concertId;
        private final String title;
        private final String venue;
        private final String startTime;
        private final String endTime;
        private final int totalSeats;
        private final int availableSeats;
        private final long price;

        public AvailableDateResponse(String date, String concertId, String title, String venue,
                                     String startTime, String endTime, int totalSeats,
                                     int availableSeats, long price) {
            this.date = date;
            this.concertId = concertId;
            this.title = title;
            this.venue = venue;
            this.startTime = startTime;
            this.endTime = endTime;
            this.totalSeats = totalSeats;
            this.availableSeats = availableSeats;
            this.price = price;
        }

        // Getters
        public String getDate() { return date; }
        public String getConcertId() { return concertId; }
        public String getTitle() { return title; }
        public String getVenue() { return venue; }
        public String getStartTime() { return startTime; }
        public String getEndTime() { return endTime; }
        public int getTotalSeats() { return totalSeats; }
        public int getAvailableSeats() { return availableSeats; }
        public long getPrice() { return price; }
    }
}