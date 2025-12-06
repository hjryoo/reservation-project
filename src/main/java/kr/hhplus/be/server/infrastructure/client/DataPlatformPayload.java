package kr.hhplus.be.server.infrastructure.client;
import kr.hhplus.be.server.domain.event.ReservationCompletedEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * 데이터 플랫폼 전송 DTO
 */
public record DataPlatformPayload(
        @JsonProperty("reservation_id")
        Long reservationId,

        @JsonProperty("concert_id")
        Long concertId,

        @JsonProperty("user_id")
        Long userId,

        @JsonProperty("seat_number")
        Integer seatNumber,

        @JsonProperty("amount")
        Long amount,

        @JsonProperty("concert_title")
        String concertTitle,

        @JsonProperty("transaction_id")
        String transactionId,

        @JsonProperty("completed_at")
        LocalDateTime completedAt
) {
    public static DataPlatformPayload from(ReservationCompletedEvent event) {
        return new DataPlatformPayload(
                event.getReservationId(),
                event.getConcertId(),
                event.getUserId(),
                event.getSeatNumber(),
                event.getAmount(),
                event.getConcertTitle(),
                event.getTransactionId(),
                event.getCompletedAt()
        );
    }
}