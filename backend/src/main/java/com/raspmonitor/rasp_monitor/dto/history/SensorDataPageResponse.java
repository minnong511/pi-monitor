package com.raspmonitor.rasp_monitor.dto.history;

import com.raspmonitor.rasp_monitor.domain.SensorData;

import java.util.List;

public record SensorDataPageResponse(
        List<SensorData> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
