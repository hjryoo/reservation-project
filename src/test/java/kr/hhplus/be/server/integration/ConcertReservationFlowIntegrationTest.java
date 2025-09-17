package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.application.service.*;
import kr.hhplus.be.server.domain.model.*;
import kr.hhplus.be.server.domain.repository.*;
import kr.hhplus.be.server.domain.port.out.UserRepository;
import kr.hhplus.be.server.interfaces.web.UserController;
import kr.hhplus.be.server.interfaces.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ConcertReservationFlowIntegrationTest {

    @Autowired private UserController userController;
    @Autowired private SeatReservationService seatReservationService;
    @Autowired private UserRepository userRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private SeatReservationRepository seatReservationRepository;
    @Autowired private QueueTokenRepository queueTokenRepository;

    private User testUser;
    private Concert testConcert;
    private Integer testSeatNumber;
    private Long seatPrice;

    @BeforeEach
    void setUp() {
        // 사용자 생성
        CreateUserRequest createUserRequest = new CreateUserRequest("user123", 0L);
        UserBalanceResponse userResponse = userController.createUser(createUserRequest).getBody();
        testUser = userRepository.findById(userResponse.id()).orElseThrow();

        // 콘서트 생성
        testConcert = Concert.create("테스트 콘서트", "아티스트",
                LocalDateTime.now().plusDays(7), 50, 100000);
        testConcert = concertRepository.save(testConcert);

        testSeatNumber = 1;
        seatPrice = 100000L;

        // 예약 가능한 좌석 미리 생성
        SeatReservation availableSeat = SeatReservation.createAvailableSeat(
                testConcert.getId(), testSeatNumber, seatPrice);
        seatReservationRepository.save(availableSeat);

        // 잔액 충전
        ChargeBalanceRequest chargeRequest = new ChargeBalanceRequest(150000L);
        userController.chargeBalance(testUser.getUserId(), chargeRequest);
    }

    @Test
    @DisplayName("전체 흐름: 토큰 발급 → 좌석 예약 → 결제 완료 (트랜잭션 경계 명확)")
    @Transactional
    void shouldCompleteFullReservationFlowWithProperTransactionBoundary() {
        // Given: 초기 상태 확인
        User initialUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(initialUser.getBalance()).isEqualTo(150000L);

        // When & Then: 단일 트랜잭션으로 전체 예약 프로세스 실행
        executeReservationTransaction();

        // 최종 상태 검증
        verifyFinalReservationState();
    }

    @Transactional
    void executeReservationTransaction() {
        // 1. 토큰 발급 및 활성화
        QueueToken queueToken = QueueToken.createWaitingToken(testUser.getId(), testConcert.getId());
        queueToken = queueTokenRepository.save(queueToken);
        QueueToken activeToken = queueToken.activate();
        queueTokenRepository.save(activeToken);

        // 2. 좌석 상태 전환: AVAILABLE → RESERVED
        SeatReservation reservedSeat = seatReservationService.reserveSeatTemporarily(
                testConcert.getId(), testSeatNumber, testUser.getId());

        assertThat(reservedSeat.getStatus()).isEqualTo(SeatStatus.RESERVED);
        assertThat(reservedSeat.getUserId()).isEqualTo(testUser.getId());

        // 3. 결제 처리 및 잔액 차감
        User currentUser = userRepository.findById(testUser.getId()).orElseThrow();
        User updatedUser = currentUser.deductBalance(seatPrice);
        userRepository.save(updatedUser);

        // 4. 좌석 상태 전환: RESERVED → SOLD
        SeatReservation confirmedSeat = seatReservationService.confirmReservation(
                testConcert.getId(), testSeatNumber, testUser.getId());

        // 5. 토큰 완료 처리
        QueueToken completedToken = activeToken.complete();
        queueTokenRepository.save(completedToken);
    }

    void verifyFinalReservationState() {
        // 잔액 확인
        User finalUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(finalUser.getBalance()).isEqualTo(50000L);

        // 좌석 상태 확인 - 같은 좌석이 SOLD 상태로 변경됨
        SeatReservation finalSeat = seatReservationRepository
                .findByConcertIdAndSeatNumber(testConcert.getId(), testSeatNumber)
                .orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.SOLD);
        assertThat(finalSeat.getUserId()).isEqualTo(testUser.getId());
    }
}