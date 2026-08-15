package com.raspmonitor.rasp_monitor.service;

import com.raspmonitor.rasp_monitor.domain.SensorData;
import com.raspmonitor.rasp_monitor.dto.SensorDataRequest;
import com.raspmonitor.rasp_monitor.repository.SensorDataRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class SensorDataService {

    private final SensorDataRepository sensorDataRepository; 

    public SensorDataService(SensorDataRepository sensorDataRepository) {
        this.sensorDataRepository = sensorDataRepository; 
    }
    
    @Transactional
    public SensorData create(SensorDataRequest request){
        SensorData sensorData = new SensorData(
            request.temperature(),
            request.cpuUsage(),
            request.memoryUsage()
        ); 
        return sensorDataRepository.save(sensorData); 
    }

    @Transactional(readOnly = true)
    public List<SensorData> findAll() {
        return sensorDataRepository
                .findAllByOrderByCreatedAtDescIdDesc();
    }

    @Transactional(readOnly = true)
    public SensorData findLatest() {
        return sensorDataRepository
                .findFirstByOrderByCreatedAtDescIdDesc()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "저장된 센서 데이터가 없습니다."
                ));
    }
}