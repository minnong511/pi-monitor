package com.raspmonitor.rasp_monitor.dto.history;

public record SensorMetricStatistics(
        Double average,
        Double minimum,
        Double maximum
) {
}
