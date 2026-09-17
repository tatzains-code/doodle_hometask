package com.tatzains.doodle_hometask.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Getter
@Setter
@ConfigurationProperties(prefix = "scheduling")
public class SchedulingProperties {

    @NestedConfigurationProperty
    private final Slot slot = new Slot();

    @NestedConfigurationProperty
    private final Availability availability = new Availability();

    @Getter
    @Setter
    public static class Slot {
        private int granularityMinutes = 15;
        private int minDurationMinutes = 15;
        private int maxDurationMinutes = 240;
        private int minBookingBufferMinutes = 15;
        private int maxHorizonDays = 90;
    }

    @Getter
    @Setter
    public static class Availability {
        private int defaultRangeDays = 7;
        private int maxRangeDays = 90;
    }
}
