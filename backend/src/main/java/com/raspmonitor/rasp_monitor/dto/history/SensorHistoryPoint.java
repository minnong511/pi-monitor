package com.raspmonitor.rasp_monitor.dto.history;

import java.time.LocalDateTime;

public record SensorHistoryPoint(
        LocalDateTime createdAt,
        Double temperature,
        Double cpuUsage,
        Double memoryUsage
) {
}
