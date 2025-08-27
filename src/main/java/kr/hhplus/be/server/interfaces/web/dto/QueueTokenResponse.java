package kr.hhplus.be.server.interfaces.web.dto;

import kr.hhplus.be.server.domain.QueueStatus;
import kr.hhplus.be.server.domain.QueueToken;

import java.time.LocalDateTime;

public record QueueTokenResponse(
        String token,
        String userId,
        Long position,
        QueueStatus status,
        Long estimatedWaitTimeMinutes,
        LocalDateTime expiresAt,
        String message
) {
    public static QueueTokenResponse from(QueueToken queueToken) {
        String message = switch (queueToken.getStatus()) {
            case WAITING -> "대기 중입니다. 예상 대기 시간: " + queueToken.getEstimatedWaitTimeMinutes() + "분";
            case ACTIVE -> "서비스를 이용하실 수 있습니다.";
            case EXPIRED -> "토큰이 만료되었습니다.";
        };

        return new QueueTokenResponse(
                queueToken.getToken(),
                queueToken.getUserId(),
                queueToken.getPosition(),
                queueToken.getStatus(),
                queueToken.getEstimatedWaitTimeMinutes(),
                queueToken.getExpiresAt(),
                message
        );
    }
}
