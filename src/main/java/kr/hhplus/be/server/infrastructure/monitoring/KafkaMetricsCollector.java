package kr.hhplus.be.server.infrastructure.monitoring;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaMetricsCollector {

    private final MeterRegistry meterRegistry;

    public void recordPublishSuccess(String topic) {
        meterRegistry.counter("kafka.producer.success", "topic", topic).increment();
    }

    public void recordPublishFailure(String topic) {
        meterRegistry.counter("kafka.producer.failure", "topic", topic).increment();
    }

    public void recordConsumeLag(String topic, long lag) {
        meterRegistry.gauge("kafka.consumer.lag", lag);
    }
}
