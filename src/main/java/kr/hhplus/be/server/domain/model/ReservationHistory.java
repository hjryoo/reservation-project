package kr.hhplus.be.server.domain.model;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservation_history", indexes = {
        @Index(name = "idx_reservation_id", columnList = "reservationId", unique = true) // 멱등성을 위한 유니크 인덱스
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long reservationId; // 원본 예약 ID (중복 체크 키)

    private Long userId;
    private Long concertId;
    private Long amount;
    private LocalDateTime createdAt;

    // 메시지 처리 시각 (Audit)
    private LocalDateTime processedAt;

    public ReservationHistory(Long reservationId, Long userId, Long concertId, Long amount, LocalDateTime createdAt) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.concertId = concertId;
        this.amount = amount;
        this.createdAt = createdAt;
        this.processedAt = LocalDateTime.now();
    }
}
