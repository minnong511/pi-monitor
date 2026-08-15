package com.raspmonitor.rasp_monitor.repository;


import com.raspmonitor.rasp_monitor.domain.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional; 

public interface SensorDataRepository extends JpaRepository<SensorData, Long>{
    
    List<SensorData> findAllByOrderByCreatedAtDescIdDesc();

    Optional<SensorData> findFirstByOrderByCreatedAtDescIdDesc();
    
}
