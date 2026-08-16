package com.raspmonitor.rasp_monitor.controller;

import com.raspmonitor.rasp_monitor.domain.SensorData;
import com.raspmonitor.rasp_monitor.dto.SensorDataRequest;
import com.raspmonitor.rasp_monitor.dto.history.SensorDataPageResponse;
import com.raspmonitor.rasp_monitor.dto.history.SensorHistorySummaryResponse;
import com.raspmonitor.rasp_monitor.service.SensorDataService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sensors")
@Validated
public class SensorDataController {

    private final SensorDataService sensorDataService;
    private final SimpMessagingTemplate messagingTemplate;

    public SensorDataController(
            SensorDataService sensorDataService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.sensorDataService = sensorDataService;
        this.messagingTemplate = messagingTemplate;
    }

    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SensorData create(
        @Valid @RequestBody SensorDataRequest request
    ) {
        SensorData savedSensorData = sensorDataService.create(request);
        // DB에 데이터를 저장
        // 저장된 데이터를 WebSocket 구독자들에게 전달

        messagingTemplate.convertAndSend(
                "/topic/sensors",
                savedSensorData
        );
        // DB 저장이 실패하면 WebSocket 메시지도 발행되지 않는다

        return savedSensorData;
    }

    @GetMapping
    public List<SensorData> findAll() {
        return sensorDataService.findAll();
    }

    @GetMapping("/latest")
    public SensorData findLatest() {
        return sensorDataService.findLatest();
    }

    @GetMapping("/recent")
    public List<SensorData> findRecent(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return sensorDataService.findRecent(limit);
    }

    @GetMapping("/history")
    public SensorDataPageResponse findHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int size
    ) {
        return sensorDataService.findHistory(from, to, page, size);
    }

    @GetMapping("/history/summary")
    public SensorHistorySummaryResponse summarizeHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @RequestParam @Min(1) @Max(86400) int bucketSeconds
    ) {
        return sensorDataService.summarizeHistory(from, to, bucketSeconds);
    }

    @GetMapping(value = "/history/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @RequestParam(defaultValue = "sensor-history.csv") String filename
    ) {
        String safeFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        StreamingResponseBody body = outputStream ->
                sensorDataService.writeHistoryCsv(outputStream, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeFilename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
