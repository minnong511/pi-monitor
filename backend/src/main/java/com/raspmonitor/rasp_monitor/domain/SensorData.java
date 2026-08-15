package com.raspmonitor.rasp_monitor.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sensor_data")
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; 

    @Column(nullable = false)
    private Double temperature; 

    @Column(nullable = false)
    private Double cpuUsage;

    @Column(nullable = false)
    private Double memoryUsage; 

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected SensorData() {}

    public SensorData(
        Double temperature,
        Double cpuUsage,
        Double memoryUsage
    ) {
        this.temperature = temperature;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage; 

    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getCpuUsage() {
        return cpuUsage;
    }

    public Double getMemoryUsage() {
        return memoryUsage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


}
    
