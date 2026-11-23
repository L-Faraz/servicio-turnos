package com.catedra.turnos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "turnos")
public class TurnosProperties {

    private final Availability availability = new Availability();
    private final Outbox outbox = new Outbox();
    private final Recovery recovery = new Recovery();

    public Availability getAvailability() {
        return availability;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Recovery getRecovery() {
        return recovery;
    }

    public static class Availability {

        private int maxDays = 31;

        public int getMaxDays() {
            return maxDays;
        }

        public void setMaxDays(int maxDays) {
            this.maxDays = Math.max(1, maxDays);
        }
    }

    public static class Outbox {

        private boolean enabled = true;
        private int maxAttempts = 5;
        private long retryInitialMs = 2000;
        private long retryMultiplier = 2;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = Math.max(1, maxAttempts);
        }

        public long getRetryInitialMs() {
            return retryInitialMs;
        }

        public void setRetryInitialMs(long retryInitialMs) {
            this.retryInitialMs = Math.max(1, retryInitialMs);
        }

        public long getRetryMultiplier() {
            return Math.max(1, retryMultiplier);
        }

        public void setRetryMultiplier(long retryMultiplier) {
            this.retryMultiplier = Math.max(1, retryMultiplier);
        }
    }

    public static class Recovery {

        private boolean enabled = true;
        private long intervalMs = 60000;
        private long initialDelayMs = 10000;
        private long staleAfterMs = 300000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getIntervalMs() {
            return Math.max(1000, intervalMs);
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = Math.max(1000, intervalMs);
        }

        public long getInitialDelayMs() {
            return Math.max(0, initialDelayMs);
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = Math.max(0, initialDelayMs);
        }

        public long getStaleAfterMs() {
            return Math.max(1000, staleAfterMs);
        }

        public void setStaleAfterMs(long staleAfterMs) {
            this.staleAfterMs = Math.max(1000, staleAfterMs);
        }
    }
}
