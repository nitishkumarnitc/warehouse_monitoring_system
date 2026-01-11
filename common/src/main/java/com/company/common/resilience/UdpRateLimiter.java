package com.company.common.resilience;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Rate limiter for UDP packet processing to prevent system overwhelm.
 * Uses token bucket algorithm via Resilience4j.
 */
public class UdpRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(UdpRateLimiter.class);

    private final RateLimiter rateLimiter;
    private final String name;

    // Configuration constants
    private static final int DEFAULT_LIMIT_FOR_PERIOD = 1000; // 1000 requests
    private static final Duration DEFAULT_LIMIT_REFRESH_PERIOD = Duration.ofSeconds(1); // per second
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(100); // wait up to 100ms for permission

    public UdpRateLimiter(String name) {
        this(name, DEFAULT_LIMIT_FOR_PERIOD, DEFAULT_LIMIT_REFRESH_PERIOD);
    }

    public UdpRateLimiter(String name, int limitForPeriod, Duration refreshPeriod) {
        this.name = name;
        this.rateLimiter = createRateLimiter(name, limitForPeriod, refreshPeriod);
    }

    private RateLimiter createRateLimiter(String name, int limitForPeriod, Duration refreshPeriod) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(limitForPeriod)
                .limitRefreshPeriod(refreshPeriod)
                .timeoutDuration(DEFAULT_TIMEOUT)
                .build();

        RateLimiter limiter = RateLimiter.of(name, config);

        // Log rate limit events
        limiter.getEventPublisher()
                .onSuccess(event -> logger.trace("{}: Request permitted", name))
                .onFailure(event -> logger.warn("{}: Rate limit exceeded - request rejected", name));

        return limiter;
    }

    /**
     * Attempts to acquire permission to process a packet.
     * Returns false if rate limit is exceeded.
     */
    public boolean tryAcquire() {
        boolean acquired = rateLimiter.acquirePermission();
        if (!acquired) {
            logger.warn("{}: Packet dropped due to rate limiting", name);
        }
        return acquired;
    }

    /**
     * Waits for permission (up to timeout).
     * Returns false if timeout exceeded.
     */
    public boolean acquire() {
        try {
            RateLimiter.waitForPermission(rateLimiter);
            return true;
        } catch (Exception e) {
            logger.error("{}: Error acquiring rate limit permission", name, e);
            return false;
        }
    }

    public String getName() {
        return name;
    }

    public RateLimiter.Metrics getMetrics() {
        return rateLimiter.getMetrics();
    }
}
