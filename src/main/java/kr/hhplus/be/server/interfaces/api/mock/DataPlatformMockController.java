package kr.hhplus.be.server.interfaces.api.mock;

import kr.hhplus.be.server.infrastructure.client.ApiResponse;
import kr.hhplus.be.server.infrastructure.client.DataPlatformPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데이터 플랫폼 Mock API 컨트롤러
 *
 * 테스트 및 개발 환경에서 사용
 */
@Slf4j
@RestController
@RequestMapping("/api/data-platform")
public class DataPlatformMockController {

    @PostMapping("/reservations")
    public ApiResponse receiveReservation(@RequestBody DataPlatformPayload payload) {
        log.info("[Mock API] 예약 정보 수신 - reservationId: {}, concertTitle: {}, userId: {}",
                payload.reservationId(), payload.concertTitle(), payload.userId());

        return new ApiResponse(true, "데이터 수신 완료", payload);
    }
}
