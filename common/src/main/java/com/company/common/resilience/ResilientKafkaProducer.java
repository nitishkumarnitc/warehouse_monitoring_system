package com.company.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Wrapper around KafkaProducer with circuit breaker pattern.
 * Prevents cascading failures when Kafka is unavailable.
 */
public class ResilientKafkaProducer<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(ResilientKafkaProducer.class);

    private final KafkaProducer<K, V> producer;
    private final CircuitBreaker circuitBreaker;

    // Configuration constants
    private static final int FAILURE_RATE_THRESHOLD = 50; // 50% failures opens circuit
    private static final int SLOW_CALL_DURATION_THRESHOLD_MS = 5000;
    private static final int SLOW_CALL_RATE_THRESHOLD = 60; // 60% slow calls opens circuit
    private static final int SLIDING_WINDOW_SIZE = 100;
    private static final int MINIMUM_CALLS = 10;
    private static final Duration WAIT_DURATION_IN_OPEN_STATE = Duration.ofSeconds(60);

    public ResilientKafkaProducer(KafkaProducer<K, V> producer, String name) {
        this.producer = producer;
        this.circuitBreaker = createCircuitBreaker(name);
    }

    private CircuitBreaker createCircuitBreaker(String name) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                .slowCallRateThreshold(SLOW_CALL_RATE_THRESHOLD)
                .slowCallDurationThreshold(Duration.ofMillis(SLOW_CALL_DURATION_THRESHOLD_MS))
                .slidingWindowSize(SLIDING_WINDOW_SIZE)
                .minimumNumberOfCalls(MINIMUM_CALLS)
                .waitDurationInOpenState(WAIT_DURATION_IN_OPEN_STATE)
                .permittedNumberOfCallsInHalfOpenState(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker(name);

        // Log circuit breaker state transitions
        cb.getEventPublisher()
                .onStateTransition(event ->
                    logger.warn("Circuit breaker state changed: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onError(event ->
                    logger.debug("Circuit breaker recorded error: {}",
                        event.getThrowable().getMessage()))
                .onSuccess(event ->
                    logger.trace("Circuit breaker recorded success"));

        return cb;
    }

    /**
     * Send message with circuit breaker protection.
     * Throws exception if circuit is open.
     */
    public RecordMetadata send(ProducerRecord<K, V> record) throws Exception {
        return circuitBreaker.executeCallable(() -> {
            try {
                return producer.send(record).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while sending to Kafka", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to send to Kafka", e.getCause());
            }
        });
    }

    /**
     * Async send with circuit breaker protection.
     */
    public CompletableFuture<RecordMetadata> sendAsync(ProducerRecord<K, V> record) {
        if (!circuitBreaker.tryAcquirePermission()) {
            CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
            future.completeExceptionally(
                new IllegalStateException("Circuit breaker is OPEN - Kafka appears unavailable"));
            return future;
        }

        long start = System.nanoTime();
        CompletableFuture<RecordMetadata> future = new CompletableFuture<>();

        producer.send(record, (metadata, exception) -> {
            long duration = System.nanoTime() - start;

            if (exception != null) {
                circuitBreaker.onError(duration, java.util.concurrent.TimeUnit.NANOSECONDS, exception);
                future.completeExceptionally(exception);
            } else {
                circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
                future.complete(metadata);
            }
        });

        return future;
    }

    public void flush() {
        producer.flush();
    }

    public void close() {
        producer.close();
    }

    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }

    public CircuitBreaker.Metrics getMetrics() {
        return circuitBreaker.getMetrics();
    }
}
