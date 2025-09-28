package kr.hhplus.be.server.interfaces.dto;

import kr.hhplus.be.server.domain.model.Concert;
import kr.hhplus.be.server.domain.model.ConcertStatus;

import java.time.LocalDateTime;

public record ConcertResponse(
        Long id,
        String title,
        String artist,
        String venue,
        Integer totalSeats,
        Integer availableSeats,
        Long price,
        ConcertStatus status,
        boolean isBookingAvailable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ConcertResponse from(Concert concert) {
        return new ConcertResponse(
                concert.getId(),
                concert.getTitle(),
                concert.getArtist(),
                concert.getVenue(),
                concert.getTotalSeats(),
                concert.getAvailableSeats(),
                concert.getPrice(),
                concert.getStatus(),
                concert.isBookingAvailable(),
                concert.getCreatedAt(),
                concert.getUpdatedAt()
        );
    }
}

