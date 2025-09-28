package kr.hhplus.be.server.infrastructure.gateway;

import kr.hhplus.be.server.domain.model.Payment;
import kr.hhplus.be.server.domain.port.out.PaymentGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test") // 이 Bean은 'test' 프로필에서만 활성화됩니다.
public class FakePaymentGateway implements PaymentGateway {

    @Override
    public Payment processPayment(Payment payment) {
        // 테스트 환경에서는 외부 시스템을 호출하지 않습니다.
        // 즉시 성공한 것으로 간주하고, 가짜 거래 ID를 부여하여 반환합니다.
        System.out.println("FakePaymentGateway is called!");
        return payment.complete("fake-txn-" + System.currentTimeMillis());
    }
}