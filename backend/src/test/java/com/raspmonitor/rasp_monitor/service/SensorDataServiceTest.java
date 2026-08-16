package com.raspmonitor.rasp_monitor.service;

import com.raspmonitor.rasp_monitor.domain.SensorData;
import com.raspmonitor.rasp_monitor.dto.history.SensorDataPageResponse;
import com.raspmonitor.rasp_monitor.repository.SensorDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SensorDataServiceTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 15, 12, 0);

    @Autowired
    private SensorDataService sensorDataService;

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        sensorDataRepository.deleteAll();
    }

    @Test
    void historyIsChronologicalAndPaged() {
        saveAt(BASE_TIME.plusMinutes(20), 60.0);
        SensorData earlier = saveAt(BASE_TIME.plusMinutes(10), 50.0);

        SensorDataPageResponse page = sensorDataService.findHistory(
                BASE_TIME,
                BASE_TIME.plusHours(1),
                0,
                1
        );

        assertEquals(2, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(earlier.getId(), page.content().getFirst().getId());
    }

    @Test
    void rejectsInvalidAndTooLongRanges() {
        assertThrows(ResponseStatusException.class, () ->
                sensorDataService.findHistory(BASE_TIME, BASE_TIME, 0, 100));
        assertThrows(ResponseStatusException.class, () ->
                sensorDataService.findHistory(
                        BASE_TIME,
                        BASE_TIME.plusDays(14).plusSeconds(1),
                        0,
                        100
                ));
    }

    @Test
    void exportContainsOnlyRequestedRawRows() throws Exception {
        saveAt(BASE_TIME.plusMinutes(5), 40.0);
        saveAt(BASE_TIME.plusMinutes(20), 55.0);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        sensorDataService.writeHistoryCsv(
                output,
                BASE_TIME,
                BASE_TIME.plusMinutes(10)
        );

        String csv = output.toString(StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("Timestamp,Temperature (°C)"));
        assertTrue(csv.contains("\"40.0\""));
        assertTrue(!csv.contains("\"55.0\""));
    }

    private SensorData saveAt(LocalDateTime createdAt, double temperature) {
        SensorData sensorData = sensorDataRepository.save(
                new SensorData(temperature, 20.0, 30.0)
        );
        jdbcTemplate.update(
                "update sensor_data set created_at = ? where id = ?",
                Timestamp.valueOf(createdAt),
                sensorData.getId()
        );
        return sensorData;
    }
}
