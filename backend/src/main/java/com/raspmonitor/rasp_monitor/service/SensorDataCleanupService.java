package com.raspmonitor.rasp_monitor.service;

import com.raspmonitor.rasp_monitor.repository.SensorDataRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SensorDataCleanupService {

    private static final int RETENTION_DAYS = 14;

    private final SensorDataRepository sensorDataRepository;

    public SensorDataCleanupService(
            SensorDataRepository sensorDataRepository
    ) {
        this.sensorDataRepository = sensorDataRepository;
    }

    @Transactional
    @Scheduled(
            cron = "0 0 3 * * *",
            zone = "Asia/Seoul"
    )
    public void deleteOldSensorData() {
        LocalDateTime cutoff =
                LocalDateTime.now().minusDays(RETENTION_DAYS);

        int deletedCount =
                sensorDataRepository.deleteOlderThan(cutoff);

        System.out.println(
                "오래된 센서 데이터 삭제: "
                + deletedCount
                + "개"
        );
    }
}