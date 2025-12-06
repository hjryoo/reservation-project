package kr.hhplus.be.server.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 예약 완료 도메인 이벤트
 *
 * 발행 시점: 결제 완료 + 좌석 확정 후
 * 목적: 데이터 플랫폼에 예약 정보 전송
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationCompletedEvent {
    private Long reservationId;
    private Long concertId;
    private Long userId;
    private Integer seatNumber;
    private Long amount;
    private String concertTitle;
    private String transactionId;
    private LocalDateTime completedAt;

    public ReservationCompletedEvent(Long reservationId, Long concertId, Long userId,
                                     Integer seatNumber, Long amount, String concertTitle,
                                     String transactionId) {
        this.reservationId = reservationId;
        this.concertId = concertId;
        this.userId = userId;
        this.seatNumber = seatNumber;
        this.amount = amount;
        this.concertTitle = concertTitle;
        this.transactionId = transactionId;
        this.completedAt = LocalDateTime.now();
    }

    public Long getReservationId() { return reservationId; }
    public Long getConcertId() { return concertId; }
    public Long getUserId() { return userId; }
    public Integer getSeatNumber() { return seatNumber; }
    public Long getAmount() { return amount; }
    public String getConcertTitle() { return concertTitle; }
    public String getTransactionId() { return transactionId; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}