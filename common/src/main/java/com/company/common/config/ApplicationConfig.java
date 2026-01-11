package com.company.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration loader for application settings.
 * Loads from application.properties or environment variables.
 */
public class ApplicationConfig {

    private final Properties properties;

    private static final String DEFAULT_CONFIG_FILE = "application.properties";

    public ApplicationConfig() {
        this(DEFAULT_CONFIG_FILE);
    }

    public ApplicationConfig(String configFile) {
        this.properties = new Properties();
        loadProperties(configFile);
    }

    private void loadProperties(String configFile) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(configFile)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            // Log warning but continue with defaults/env vars
            System.err.println("Warning: Could not load " + configFile + ", using defaults");
        }
    }

    /**
     * Get property with environment variable fallback.
     * Priority: System property > Environment variable > Config file > Default
     */
    public String get(String key, String defaultValue) {
        // Check system property first (for -D overrides)
        String value = System.getProperty(key);
        if (value != null) {
            return value;
        }

        // Check environment variable
        String envKey = key.toUpperCase().replace('.', '_');
        value = System.getenv(envKey);
        if (value != null) {
            return value;
        }

        // Check properties file
        value = properties.getProperty(key);
        if (value != null) {
            return value;
        }

        return defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        String value = get(key, String.valueOf(defaultValue));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getDouble(String key, double defaultValue) {
        String value = get(key, String.valueOf(defaultValue));
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }
}
