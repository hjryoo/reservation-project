package kr.hhplus.be.server.config;

import kr.hhplus.be.server.domain.model.Payment;
import kr.hhplus.be.server.domain.port.out.PaymentGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestPaymentConfig {

    @Bean
    @Primary
    public PaymentGateway paymentGateway() {
        return new PaymentGateway() {
            @Override
            public Payment processPayment(Payment payment) {
                // 테스트용 성공 처리
                return payment.complete("test-transaction-" + System.currentTimeMillis());
            }
        };
    }
}