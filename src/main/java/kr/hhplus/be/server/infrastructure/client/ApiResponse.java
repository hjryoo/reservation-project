package kr.hhplus.be.server.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mock API 응답 DTO
 */
public record ApiResponse(
        @JsonProperty("success")
        boolean success,

        @JsonProperty("message")
        String message,

        @JsonProperty("data")
        Object data
) {
}

