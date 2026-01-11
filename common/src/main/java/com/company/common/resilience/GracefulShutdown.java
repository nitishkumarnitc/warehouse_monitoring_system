package com.company.common.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages graceful shutdown of resources.
 * Ensures Kafka producers flush, consumers close, sockets close, etc.
 */
public class GracefulShutdown {

    private static final Logger logger = LoggerFactory.getLogger(GracefulShutdown.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final List<ShutdownTask> tasks = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean shutdownInitiated = false;

    public interface ShutdownTask {
        void execute() throws Exception;
        String getName();
    }

    public GracefulShutdown() {
        // Register JVM shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "shutdown-hook"));
    }

    /**
     * Register a resource to be closed on shutdown.
     */
    public void registerTask(String name, ShutdownTask task) {
        if (shutdownInitiated) {
            logger.warn("Shutdown already initiated, cannot register task: {}", name);
            return;
        }
        tasks.add(task);
        logger.debug("Registered shutdown task: {}", name);
    }

    /**
     * Convenience method for AutoCloseable resources.
     */
    public void registerCloseable(String name, AutoCloseable closeable) {
        registerTask(name, new ShutdownTask() {
            @Override
            public void execute() throws Exception {
                closeable.close();
            }

            @Override
            public String getName() {
                return name;
            }
        });
    }

    /**
     * Convenience method for Runnable cleanup tasks.
     */
    public void registerRunnable(String name, Runnable runnable) {
        registerTask(name, new ShutdownTask() {
            @Override
            public void execute() {
                runnable.run();
            }

            @Override
            public String getName() {
                return name;
            }
        });
    }

    /**
     * Execute shutdown sequence.
     */
    public void shutdown() {
        if (shutdownInitiated) {
            logger.debug("Shutdown already in progress");
            return;
        }

        shutdownInitiated = true;
        logger.info("Initiating graceful shutdown...");

        long startTime = System.currentTimeMillis();

        // Execute tasks in reverse order (LIFO - last registered, first shutdown)
        List<ShutdownTask> reversedTasks = new ArrayList<>(tasks);
        Collections.reverse(reversedTasks);

        int successful = 0;
        int failed = 0;

        for (ShutdownTask task : reversedTasks) {
            try {
                logger.info("Shutting down: {}", task.getName());
                task.execute();
                successful++;
            } catch (Exception e) {
                failed++;
                logger.error("Error shutting down: {}", task.getName(), e);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Graceful shutdown completed in {}ms ({} successful, {} failed)",
            duration, successful, failed);
    }

    /**
     * For testing - reset the singleton state.
     */
    public void reset() {
        tasks.clear();
        shutdownInitiated = false;
    }

    public boolean isShutdownInitiated() {
        return shutdownInitiated;
    }

    public int getRegisteredTaskCount() {
        return tasks.size();
    }
}
