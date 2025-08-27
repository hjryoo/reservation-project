package kr.hhplus.be.server.interfaces.web.dto;

import kr.hhplus.be.server.domain.QueueStatus;
import kr.hhplus.be.server.domain.QueueToken;

public record QueueStatusResponse(
        Long position,
        QueueStatus status,
        Long estimatedWaitTimeMinutes,
        String message
) {
    public static QueueStatusResponse from(QueueToken queueToken) {
        String message = switch (queueToken.getStatus()) {
            case WAITING -> "대기 순서: " + queueToken.getPosition() + "번";
            case ACTIVE -> "서비스 이용 가능";
            case EXPIRED -> "토큰 만료";
        };

        return new QueueStatusResponse(
                queueToken.getPosition(),
                queueToken.getStatus(),
                queueToken.getEstimatedWaitTimeMinutes(),
                message
        );
    }
}