package com.company.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricsRegistryTest {

    @BeforeEach
    void setUp() {
        MetricsRegistry.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        MetricsRegistry.resetForTesting();
    }

    @Test
    void shouldCreateRegistrySingleton() {
        PrometheusMeterRegistry registry1 = MetricsRegistry.getRegistry();
        PrometheusMeterRegistry registry2 = MetricsRegistry.getRegistry();

        assertThat(registry1).isNotNull();
        assertThat(registry1).isSameAs(registry2);
    }

    @Test
    void shouldCreateCounter() {
        Counter counter = MetricsRegistry.counter(
                "test_counter",
                "Test counter description"
        );

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(0.0);

        counter.increment();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldCreateCounterWithTags() {
        Counter counter = MetricsRegistry.counter(
                "test_counter",
                "Test counter description",
                "env", "test",
                "service", "warehouse"
        );

        assertThat(counter).isNotNull();
        assertThat(counter.getId().getTag("env")).isEqualTo("test");
        assertThat(counter.getId().getTag("service")).isEqualTo("warehouse");
    }

    @Test
    void shouldCreateTimer() {
        Timer timer = MetricsRegistry.timer(
                "test_timer",
                "Test timer description"
        );

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(0L);

        timer.record(() -> {
            // Simulate work
        });

        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    void shouldCreateTimerWithTags() {
        Timer timer = MetricsRegistry.timer(
                "test_timer",
                "Test timer description",
                "env", "test"
        );

        assertThat(timer).isNotNull();
        assertThat(timer.getId().getTag("env")).isEqualTo("test");
    }

    @Test
    void shouldCreateAtomicGauge() {
        AtomicLong gauge = MetricsRegistry.atomicGauge(
                "test_gauge",
                "Test gauge description"
        );

        assertThat(gauge).isNotNull();
        assertThat(gauge.get()).isEqualTo(0L);

        gauge.set(100L);
        assertThat(gauge.get()).isEqualTo(100L);
    }

    @Test
    void shouldCreateAtomicGaugeWithTags() {
        AtomicLong gauge = MetricsRegistry.atomicGauge(
                "test_gauge",
                "Test gauge description",
                "type", "memory"
        );

        assertThat(gauge).isNotNull();
        gauge.set(50L);
        assertThat(gauge.get()).isEqualTo(50L);
    }

    // Note: HTTP server tests are commented out due to JaCoCo instrumentation conflicts
    // The HTTP server functionality is tested via integration tests

    @Test
    void shouldStopMetricsServerSafely() {
        // Stopping when no server exists should be safe
        MetricsRegistry.stopMetricsServer();
    }

    @Test
    void shouldResetForTesting() {
        Counter counter1 = MetricsRegistry.counter("test_counter", "Test");
        counter1.increment();

        MetricsRegistry.resetForTesting();

        Counter counter2 = MetricsRegistry.counter("test_counter", "Test");
        assertThat(counter2.count()).isEqualTo(0.0);
    }

    @Test
    void shouldHandleMultipleCountersWithSameName() {
        Counter counter1 = MetricsRegistry.counter(
                "requests_total",
                "Total requests",
                "service", "warehouse"
        );
        Counter counter2 = MetricsRegistry.counter(
                "requests_total",
                "Total requests",
                "service", "monitoring"
        );

        counter1.increment();
        counter2.increment();
        counter2.increment();

        assertThat(counter1.count()).isEqualTo(1.0);
        assertThat(counter2.count()).isEqualTo(2.0);
    }

    @Test
    void shouldRecordTimerDuration() {
        Timer timer = MetricsRegistry.timer("operation_duration", "Operation duration");

        timer.record(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }
}
