package com.raspmonitor.rasp_monitor.dto.history;

public record SensorHistoryStatistics(
        SensorMetricStatistics temperature,
        SensorMetricStatistics cpuUsage,
        SensorMetricStatistics memoryUsage
) {
}
