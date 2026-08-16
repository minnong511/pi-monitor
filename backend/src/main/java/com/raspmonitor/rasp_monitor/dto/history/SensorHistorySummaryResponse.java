package com.raspmonitor.rasp_monitor.dto.history;

import java.time.LocalDateTime;
import java.util.List;

public record SensorHistorySummaryResponse(
        LocalDateTime from,
        LocalDateTime to,
        int bucketSeconds,
        long recordCount,
        List<SensorHistoryPoint> points,
        SensorHistoryStatistics statistics
) {
}
