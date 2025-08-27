package kr.hhplus.be.server.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class QueueTokenTest {

    @Test
    @DisplayName("대기 토큰을 생성한다")
    void createWaitingToken() {
        // given
        String userId = "user123";
        Long position = 5L;

        // when
        QueueToken token = QueueToken.createWaitingToken(userId, position);

        // then
        assertThat(token.getUserId()).isEqualTo(userId);
        assertThat(token.getPosition()).isEqualTo(position);
        assertThat(token.getStatus()).isEqualTo(QueueStatus.WAITING);
        assertThat(token.getToken()).startsWith("queue_" + userId + "_");
        assertThat(token.getCreatedAt()).isNotNull();
        assertThat(token.getExpiresAt()).isAfter(token.getCreatedAt());
    }

    @Test
    @DisplayName("활성 토큰을 생성한다")
    void createActiveToken() {
        // given
        String userId = "user123";

        // when
        QueueToken token = QueueToken.createActiveToken(userId);

        // then
        assertThat(token.getUserId()).isEqualTo(userId);
        assertThat(token.getPosition()).isEqualTo(0L);
        assertThat(token.getStatus()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(token.getActivatedAt()).isNotNull();
        assertThat(token.isActive()).isTrue();
    }

    @Test
    @DisplayName("대기 토큰을 활성화한다")
    void activateWaitingToken() {
        // given
        QueueToken token = QueueToken.createWaitingToken("user123", 3L);

        // when
        token.activate();

        // then
        assertThat(token.getStatus()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(token.getPosition()).isEqualTo(0L);
        assertThat(token.getActivatedAt()).isNotNull();
        assertThat(token.isActive()).isTrue();
    }

    @Test
    @DisplayName("활성 토큰을 활성화하려 하면 예외가 발생한다")
    void throwException_WhenActivateActiveToken() {
        // given
        QueueToken token = QueueToken.createActiveToken("user123");

        // when & then
        assertThatThrownBy(token::activate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("대기 중인 토큰만 활성화할 수 있습니다.");
    }

    @Test
    @DisplayName("토큰을 만료시킨다")
    void expireToken() {
        // given
        QueueToken token = QueueToken.createActiveToken("user123");

        // when
        token.expire();

        // then
        assertThat(token.getStatus()).isEqualTo(QueueStatus.EXPIRED);
        assertThat(token.isExpired()).isTrue();
        assertThat(token.isActive()).isFalse();
    }

    @Test
    @DisplayName("예상 대기 시간을 계산한다")
    void calculateEstimatedWaitTime() {
        // given
        QueueToken waitingToken = QueueToken.createWaitingToken("user123", 25L);
        QueueToken activeToken = QueueToken.createActiveToken("user456");

        // when & then
        assertThat(waitingToken.getEstimatedWaitTimeMinutes()).isEqualTo(3L); // (25/10) + 1
        assertThat(activeToken.getEstimatedWaitTimeMinutes()).isEqualTo(0L);
    }
}
