package kr.hhplus.be.server.infrastructure.client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 데이터 플랫폼 Mock API 클라이언트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlatformClient {

    private final RestTemplate restTemplate;

    private static final String MOCK_ENDPOINT =
            "http://localhost:8080/api/data-platform/reservations";

    /**
     * 예약 정보를 데이터 플랫폼으로 전송
     */
    public void sendReservationData(DataPlatformPayload payload) {
        try {
            log.debug("데이터 플랫폼 API 호출 - reservationId: {}", payload.reservationId());

            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    MOCK_ENDPOINT,
                    payload,
                    ApiResponse.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new DataPlatformException(
                        "HTTP 상태 코드: " + response.getStatusCode()
                );
            }

            ApiResponse body = response.getBody();
            if (body == null || !body.success()) {
                throw new DataPlatformException(
                        "API 응답 실패: " + (body != null ? body.message() : "null response")
                );
            }

            log.debug("데이터 플랫폼 API 호출 성공 - reservationId: {}", payload.reservationId());

        } catch (Exception e) {
            log.error("데이터 플랫폼 API 호출 실패 - endpoint: {}, error: {}",
                    MOCK_ENDPOINT, e.getMessage());
            throw new DataPlatformException("데이터 플랫폼 전송 실패", e);
        }
    }

    public static class DataPlatformException extends RuntimeException {
        public DataPlatformException(String message) {
            super(message);
        }

        public DataPlatformException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}