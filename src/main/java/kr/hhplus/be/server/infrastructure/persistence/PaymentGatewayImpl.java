package kr.hhplus.be.server.infrastructure.persistence;

import kr.hhplus.be.server.domain.model.Payment;
import kr.hhplus.be.server.domain.port.out.PaymentGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class PaymentGatewayImpl implements PaymentGateway {

    @Override
    public Payment processPayment(Payment payment) {
        // 실제 결제 처리 로직 (PG사 연동 등)
        // 현재는 테스트용으로 단순 완료 처리

        try {
            // 결제 처리 시뮬레이션
            Thread.sleep(100); // 결제 처리 지연 시뮬레이션

            // 결제 성공으로 완료 처리
            return payment.complete("txn-" + System.currentTimeMillis());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("결제 처리 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            // 결제 실패 처리
            return payment.fail("결제 처리 실패: " + e.getMessage());
        }
    }
}
