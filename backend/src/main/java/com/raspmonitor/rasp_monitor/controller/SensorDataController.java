package com.raspmonitor.rasp_monitor.controller;

import com.raspmonitor.rasp_monitor.domain.SensorData;
import com.raspmonitor.rasp_monitor.dto.SensorDataRequest;
import com.raspmonitor.rasp_monitor.service.SensorDataService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/sensors")

public class SensorDataController {

    private final SensorDataService sensorDataService;

    public SensorDataController(
        SensorDataService  sensorDataService
    ) {
        this.sensorDataService = sensorDataService;
    }
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SensorData create(
        @Valid @RequestBody SensorDataRequest request
    ) {
        return sensorDataService.create(request);
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
