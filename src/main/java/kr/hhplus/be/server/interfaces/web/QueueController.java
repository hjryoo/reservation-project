package kr.hhplus.be.server.interfaces.web;
import jakarta.validation.Valid;
import kr.hhplus.be.server.application.QueueService;
import kr.hhplus.be.server.domain.QueueToken;
import kr.hhplus.be.server.interfaces.web.dto.QueueStatusResponse;
import kr.hhplus.be.server.interfaces.web.dto.QueueTokenRequest;
import kr.hhplus.be.server.interfaces.web.dto.QueueTokenResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.*;

@RestController
@RequestMapping("/api/v1/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    /**
     * 대기열 토큰 발급
     */
    @PostMapping("/token")
    public ResponseEntity<QueueTokenResponse> issueToken(
            @RequestBody @Valid QueueTokenRequest request) {

        QueueToken queueToken = queueService.issueToken(request.userId());
        QueueTokenResponse response = QueueTokenResponse.from(queueToken);

        return ResponseEntity.ok(response);
    }

    /**
     * 대기열 상태 조회 (폴링용)
     */
    @GetMapping("/status")
    public ResponseEntity<QueueStatusResponse> getQueueStatus(
            @RequestHeader("Authorization") String authHeader) {

        String token = extractTokenFromHeader(authHeader);
        QueueToken queueToken = queueService.getTokenStatus(token);
        QueueStatusResponse response = QueueStatusResponse.from(queueToken);

        return ResponseEntity.ok(response);
    }

    private String extractTokenFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("유효하지 않은 Authorization 헤더입니다.");
        }
        return authHeader.substring(7);
    }
}
