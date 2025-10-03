package kr.hhplus.be.server.infrastructure.persistence;

import kr.hhplus.be.server.domain.model.Payment;
import kr.hhplus.be.server.domain.port.out.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class PaymentGatewayImpl implements PaymentGateway {

    private static final Logger logger = LoggerFactory.getLogger(PaymentGatewayImpl.class);

    @Override
    public Payment processPayment(Payment payment) {
        try {
            // 실제 결제 게이트웨이 연동 로직
            // 예: RestTemplate, WebClient 등을 사용한 외부 API 호출

            // TODO: 실제 PG사 API 연동 구현
            // PaymentResponse response = paymentClient.process(payment);

            logger.info("결제 처리 시작. reservationId: {}, amount: {}",
                    payment.getReservationId(), payment.getAmount());

            // 시뮬레이션을 제거하고 바로 성공 처리
            // 실제로는 외부 API 응답에 따라 처리
            String transactionId = generateTransactionId();

            logger.info("결제 처리 완료. transactionId: {}", transactionId);
            return payment.complete(transactionId);

        } catch (Exception e) {
            logger.error("결제 처리 실패. reservationId: {}", payment.getReservationId(), e);
            return payment.fail("결제 처리 실패: " + e.getMessage());
        }
    }

    private String generateTransactionId() {
        return "txn-" + System.currentTimeMillis() + "-" +
                String.format("%04d", (int)(Math.random() * 10000));
    }
}