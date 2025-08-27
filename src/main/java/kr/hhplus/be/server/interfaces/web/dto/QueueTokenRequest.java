package kr.hhplus.be.server.interfaces.web.dto;


import jakarta.validation.constraints.NotBlank;

public record QueueTokenRequest(
        @NotBlank(message = "사용자 ID는 필수입니다.")
        String userId
) {}