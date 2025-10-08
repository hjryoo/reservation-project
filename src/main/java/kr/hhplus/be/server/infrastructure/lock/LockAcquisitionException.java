package kr.hhplus.be.server.infrastructure.lock;

/**
 * 분산락 획득 실패 예외
 */
public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException(String message) {
        super(message);
    }

    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}