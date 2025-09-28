package kr.hhplus.be.server.domain.port.in;

import kr.hhplus.be.server.domain.model.Payment;

public interface ProcessPaymentUseCase {
    Payment processPayment(ProcessPaymentCommand command);

    record ProcessPaymentCommand(Long reservationId, Long userId, Long amount) {
    }
}
