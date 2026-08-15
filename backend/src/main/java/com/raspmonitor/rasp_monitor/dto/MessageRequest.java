package com.raspmonitor.rasp_monitor.dto;

import jakarta.validation.constraints.NotBlank;

public record MessageRequest(@NotBlank String content) {
}