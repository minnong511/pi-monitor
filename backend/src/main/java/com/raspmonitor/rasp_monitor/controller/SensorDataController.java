package com.raspmonitor.rasp_monitor.controller;

import com.raspmonitor.rasp_monitor.domain.SensorData;
import com.raspmonitor.rasp_monitor.dto.SensorDataRequest;
import com.raspmonitor.rasp_monitor.service.SensorDataService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sensors")


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
}
