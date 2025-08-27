package kr.hhplus.be.server.application;

import kr.hhplus.be.server.domain.QueueStatus;
import kr.hhplus.be.server.domain.QueueToken;
import kr.hhplus.be.server.repository.QueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private QueueRepository queueRepository;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(queueRepository);
    }

    @Test
    @DisplayName("대기열에 여유가 있으면 바로 활성 토큰을 발급한다")
    void issueActiveToken_WhenSlotsAvailable() {
        // given
        String userId = "user123";
        when(queueRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(queueRepository.countByStatus(QueueStatus.ACTIVE)).thenReturn(50L);
        when(queueRepository.save(any(QueueToken.class))).thenAnswer(invocation -> {
            QueueToken token = invocation.getArgument(0);
            token.setId(1L);
            return token;
        });

        // when
        QueueToken result = queueService.issueToken(userId);

        // then
        assertThat(result.getStatus()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getPosition()).isEqualTo(0L);

        verify(queueRepository).save(any(QueueToken.class));
    }

    @Test
    @DisplayName("대기열이 가득 차면 대기 토큰을 발급한다")
    void issueWaitingToken_WhenSlotsFull() {
        // given
        String userId = "user123";
        when(queueRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(queueRepository.countByStatus(QueueStatus.ACTIVE)).thenReturn(100L);
        when(queueRepository.countByStatus(QueueStatus.WAITING)).thenReturn(10L);
        when(queueRepository.save(any(QueueToken.class))).thenAnswer(invocation -> {
            QueueToken token = invocation.getArgument(0);
            token.setId(1L);
            return token;
        });

        // when
        QueueToken result = queueService.issueToken(userId);

        // then
        assertThat(result.getStatus()).isEqualTo(QueueStatus.WAITING);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getPosition()).isEqualTo(11L);

        verify(queueRepository).save(any(QueueToken.class));
    }

    @Test
    @DisplayName("기존 유효한 토큰이 있으면 그대로 반환한다")
    void returnExistingToken_WhenValidTokenExists() {
        // given
        String userId = "user123";
        QueueToken existingToken = QueueToken.createActiveToken(userId);
        existingToken.setId(1L);

        when(queueRepository.findByUserId(userId)).thenReturn(Optional.of(existingToken));

        // when
        QueueToken result = queueService.issueToken(userId);

        // then
        assertThat(result).isEqualTo(existingToken);
        verify(queueRepository, never()).save(any(QueueToken.class));
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 상태 조회 시 예외가 발생한다")
    void throwException_WhenInvalidToken() {
        // given
        String invalidToken = "invalid_token";
        when(queueRepository.findByToken(invalidToken)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> queueService.getTokenStatus(invalidToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 토큰입니다.");
    }

    @Test
    @DisplayName("활성 토큰 검증이 성공한다")
    void validateActiveToken_Success() {
        // given
        String token = "active_token";
        QueueToken activeToken = QueueToken.createActiveToken("user123");
        activeToken.setId(1L);

        when(queueRepository.findByToken(token)).thenReturn(Optional.of(activeToken));

        // when & then
        assertThatCode(() -> queueService.validateActiveToken(token))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("비활성 토큰 검증 시 예외가 발생한다")
    void throwException_WhenInactiveToken() {
        // given
        String token = "waiting_token";
        QueueToken waitingToken = QueueToken.createWaitingToken("user123", 1L);
        waitingToken.setId(1L);

        when(queueRepository.findByToken(token)).thenReturn(Optional.of(waitingToken));

        // when & then
        assertThatThrownBy(() -> queueService.validateActiveToken(token))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("활성화되지 않은 토큰입니다. 대기열에서 순서를 기다려주세요.");
    }

    @Test
    @DisplayName("사용자 ID가 null이면 예외가 발생한다")
    void throwException_WhenUserIdIsNull() {
        // when & then
        assertThatThrownBy(() -> queueService.issueToken(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 ID는 필수입니다.");
    }

    @Test
    @DisplayName("사용자 ID가 빈 문자열이면 예외가 발생한다")
    void throwException_WhenUserIdIsEmpty() {
        // when & then
        assertThatThrownBy(() -> queueService.issueToken(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 ID는 필수입니다.");
    }
}