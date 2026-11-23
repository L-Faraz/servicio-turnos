package com.catedra.turnos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "turnos")
public class TurnosProperties {

    private final Availability availability = new Availability();

    public Availability getAvailability() {
        return availability;
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
}
