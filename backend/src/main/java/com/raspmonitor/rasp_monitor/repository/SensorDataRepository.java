package com.raspmonitor.rasp_monitor.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;


import com.raspmonitor.rasp_monitor.domain.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional; 

public interface SensorDataRepository
        extends JpaRepository<SensorData, Long> {

    List<SensorData> findAllByOrderByCreatedAtDescIdDesc();

    Optional<SensorData>
        findFirstByOrderByCreatedAtDescIdDesc();

    @Modifying
    @Query("""
        delete from SensorData sensor
        where sensor.createdAt < :cutoff
        """)
    int deleteOlderThan(
            @Param("cutoff") LocalDateTime cutoff
    );
}