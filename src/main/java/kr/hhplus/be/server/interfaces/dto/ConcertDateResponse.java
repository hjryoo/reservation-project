package kr.hhplus.be.server.interfaces.dto;
import kr.hhplus.be.server.domain.model.ConcertDate;
import kr.hhplus.be.server.domain.model.ConcertDate.ConcertDateStatus;

import java.time.LocalDateTime;

public record ConcertDateResponse(
        Long id,
        Long concertId,
        LocalDateTime concertDateTime,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer totalSeats,
        Integer availableSeats,
        ConcertDateStatus status,
        boolean isBookingAvailable,
        LocalDateTime createdAt
) {
    public static ConcertDateResponse from(ConcertDate concertDate) {
        return new ConcertDateResponse(
                concertDate.getId(),
                concertDate.getConcertId(),
                concertDate.getConcertDateTime(),
                concertDate.getStartTime(),
                concertDate.getEndTime(),
                concertDate.getTotalSeats(),
                concertDate.getAvailableSeats(),
                concertDate.getStatus(),
                concertDate.isBookingAvailable(),
                concertDate.getCreatedAt()
        );
    }
}
