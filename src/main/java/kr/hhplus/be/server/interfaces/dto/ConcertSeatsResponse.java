package kr.hhplus.be.server.interfaces.dto;

import java.util.List;

public class ConcertSeatsResponse {
    private final ConcertInfo concertInfo;
    private final List<SeatInfo> seats;
    private final SeatSummary summary;

    private ConcertSeatsResponse(Builder builder) {
        this.concertInfo = builder.concertInfo;
        this.seats = builder.seats;
        this.summary = builder.summary;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public ConcertInfo getConcertInfo() { return concertInfo; }
    public List<SeatInfo> getSeats() { return seats; }
    public SeatSummary getSummary() { return summary; }

    // Builder Pattern
    public static class Builder {
        private ConcertInfo concertInfo;
        private List<SeatInfo> seats;
        private SeatSummary summary;

        public Builder concertInfo(ConcertInfo concertInfo) {
            this.concertInfo = concertInfo;
            return this;
        }

        public Builder seats(List<SeatInfo> seats) {
            this.seats = seats;
            return this;
        }

        public Builder summary(SeatSummary summary) {
            this.summary = summary;
            return this;
        }

        public ConcertSeatsResponse build() {
            return new ConcertSeatsResponse(this);
        }
    }

    // Nested DTO Classes
    public static class ConcertInfo {
        private final Long concertId;
        private final String title;
        private final String artist;
        private final String venue;
        private final String date;
        private final String startTime;
        private final String endTime;
        private final Long price;

        private ConcertInfo(Builder builder) {
            this.concertId = builder.concertId;
            this.title = builder.title;
            this.artist = builder.artist;
            this.venue = builder.venue;
            this.date = builder.date;
            this.startTime = builder.startTime;
            this.endTime = builder.endTime;
            this.price = builder.price;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public Long getConcertId() { return concertId; }
        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public String getVenue() { return venue; }
        public String getDate() { return date; }
        public String getStartTime() { return startTime; }
        public String getEndTime() { return endTime; }
        public Long getPrice() { return price; }

        public static class Builder {
            private Long concertId;
            private String title;
            private String artist;
            private String venue;
            private String date;
            private String startTime;
            private String endTime;
            private Long price;

            public Builder concertId(Long concertId) { this.concertId = concertId; return this; }
            public Builder title(String title) { this.title = title; return this; }
            public Builder artist(String artist) { this.artist = artist; return this; }
            public Builder venue(String venue) { this.venue = venue; return this; }
            public Builder date(String date) { this.date = date; return this; }
            public Builder startTime(String startTime) { this.startTime = startTime; return this; }
            public Builder endTime(String endTime) { this.endTime = endTime; return this; }
            public Builder price(Long price) { this.price = price; return this; }

            public ConcertInfo build() {
                return new ConcertInfo(this);
            }
        }
    }

    public static class SeatInfo {
        private final Integer seatNumber;
        private final String status;
        private final String section;
        private final String rowNumber;
        private final Long price;
        private final String reservedUntil;

        private SeatInfo(Builder builder) {
            this.seatNumber = builder.seatNumber;
            this.status = builder.status;
            this.section = builder.section;
            this.rowNumber = builder.rowNumber;
            this.price = builder.price;
            this.reservedUntil = builder.reservedUntil;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public Integer getSeatNumber() { return seatNumber; }
        public String getStatus() { return status; }
        public String getSection() { return section; }
        public String getRowNumber() { return rowNumber; }
        public Long getPrice() { return price; }
        public String getReservedUntil() { return reservedUntil; }

        public static class Builder {
            private Integer seatNumber;
            private String status;
            private String section;
            private String rowNumber;
            private Long price;
            private String reservedUntil;

            public Builder seatNumber(Integer seatNumber) { this.seatNumber = seatNumber; return this; }
            public Builder status(String status) { this.status = status; return this; }
            public Builder section(String section) { this.section = section; return this; }
            public Builder rowNumber(String rowNumber) { this.rowNumber = rowNumber; return this; }
            public Builder price(Long price) { this.price = price; return this; }
            public Builder reservedUntil(String reservedUntil) { this.reservedUntil = reservedUntil; return this; }

            public SeatInfo build() {
                return new SeatInfo(this);
            }
        }
    }

    public static class SeatSummary {
        private final Integer totalSeats;
        private final Integer availableSeats;
        private final Integer reservedSeats;
        private final Integer soldSeats;

        private SeatSummary(Builder builder) {
            this.totalSeats = builder.totalSeats;
            this.availableSeats = builder.availableSeats;
            this.reservedSeats = builder.reservedSeats;
            this.soldSeats = builder.soldSeats;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public Integer getTotalSeats() { return totalSeats; }
        public Integer getAvailableSeats() { return availableSeats; }
        public Integer getReservedSeats() { return reservedSeats; }
        public Integer getSoldSeats() { return soldSeats; }

        public static class Builder {
            private Integer totalSeats;
            private Integer availableSeats;
            private Integer reservedSeats;
            private Integer soldSeats;

            public Builder totalSeats(Integer totalSeats) { this.totalSeats = totalSeats; return this; }
            public Builder availableSeats(Integer availableSeats) { this.availableSeats = availableSeats; return this; }
            public Builder reservedSeats(Integer reservedSeats) { this.reservedSeats = reservedSeats; return this; }
            public Builder soldSeats(Integer soldSeats) { this.soldSeats = soldSeats; return this; }

            public SeatSummary build() {
                return new SeatSummary(this);
            }
        }
    }
}
