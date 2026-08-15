package com.raspmonitor.rasp_monitor.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record SensorDataRequest(
    @NotNull
    Double temperature,

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    Double cpuUsage, 

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    Double memoryUsage
    
) {
}
