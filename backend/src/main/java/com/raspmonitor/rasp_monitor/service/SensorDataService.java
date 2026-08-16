package com.raspmonitor.rasp_monitor.service;

import com.raspmonitor.rasp_monitor.domain.SensorData;
import com.raspmonitor.rasp_monitor.dto.SensorDataRequest;
import com.raspmonitor.rasp_monitor.dto.history.SensorDataPageResponse;
import com.raspmonitor.rasp_monitor.dto.history.SensorHistoryPoint;
import com.raspmonitor.rasp_monitor.dto.history.SensorHistoryStatistics;
import com.raspmonitor.rasp_monitor.dto.history.SensorHistorySummaryResponse;
import com.raspmonitor.rasp_monitor.dto.history.SensorMetricStatistics;
import com.raspmonitor.rasp_monitor.repository.SensorDataRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class SensorDataService {

    private static final Duration HISTORY_RETENTION = Duration.ofDays(14);
    private static final int MAX_PAGE_SIZE = 1000;
    private static final int MAX_BUCKET_SECONDS = 24 * 60 * 60;

    private final SensorDataRepository sensorDataRepository; 
    private final JdbcTemplate jdbcTemplate;

    public SensorDataService(
            SensorDataRepository sensorDataRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.sensorDataRepository = sensorDataRepository; 
        this.jdbcTemplate = jdbcTemplate;
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

    @Transactional(readOnly = true)
    public List<SensorData> findRecent(int limit) {
        int safeLimit = Math.min(limit, 100);
        return sensorDataRepository
                .findAll(PageRequest.of(0, safeLimit,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC,
                                "createdAt", "id")))
                .getContent();
    }

    @Transactional(readOnly = true)
    public SensorDataPageResponse findHistory(
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size
    ) {
        validateRange(from, to);
        if (page < 0) {
            throw badRequest("page는 0 이상이어야 합니다.");
        }

        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        Page<SensorData> result = sensorDataRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                        from,
                        to,
                        PageRequest.of(page, safeSize)
                );

        return new SensorDataPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public SensorHistorySummaryResponse summarizeHistory(
            LocalDateTime from,
            LocalDateTime to,
            int bucketSeconds
    ) {
        validateRange(from, to);
        if (bucketSeconds < 1 || bucketSeconds > MAX_BUCKET_SECONDS) {
            throw badRequest("bucketSeconds는 1에서 86400 사이여야 합니다.");
        }

        String pointsSql = """
                select date_bin(make_interval(secs => ?), created_at,
                                timestamp '2000-01-01 00:00:00') as bucket_start,
                       avg(temperature) as temperature,
                       avg(cpu_usage) as cpu_usage,
                       avg(memory_usage) as memory_usage
                from sensor_data
                where created_at >= ? and created_at < ?
                group by bucket_start
                order by bucket_start asc
                """;

        List<SensorHistoryPoint> points = jdbcTemplate.query(
                pointsSql,
                (resultSet, rowNumber) -> new SensorHistoryPoint(
                        resultSet.getTimestamp("bucket_start").toLocalDateTime(),
                        resultSet.getDouble("temperature"),
                        resultSet.getDouble("cpu_usage"),
                        resultSet.getDouble("memory_usage")
                ),
                bucketSeconds,
                Timestamp.valueOf(from),
                Timestamp.valueOf(to)
        );

        String statisticsSql = """
                select count(*) as record_count,
                       avg(temperature) as temperature_average,
                       min(temperature) as temperature_minimum,
                       max(temperature) as temperature_maximum,
                       avg(cpu_usage) as cpu_usage_average,
                       min(cpu_usage) as cpu_usage_minimum,
                       max(cpu_usage) as cpu_usage_maximum,
                       avg(memory_usage) as memory_usage_average,
                       min(memory_usage) as memory_usage_minimum,
                       max(memory_usage) as memory_usage_maximum
                from sensor_data
                where created_at >= ? and created_at < ?
                """;

        return jdbcTemplate.queryForObject(
                statisticsSql,
                (resultSet, rowNumber) -> new SensorHistorySummaryResponse(
                        from,
                        to,
                        bucketSeconds,
                        resultSet.getLong("record_count"),
                        points,
                        new SensorHistoryStatistics(
                                new SensorMetricStatistics(
                                        getNullableDouble(resultSet, "temperature_average"),
                                        getNullableDouble(resultSet, "temperature_minimum"),
                                        getNullableDouble(resultSet, "temperature_maximum")
                                ),
                                new SensorMetricStatistics(
                                        getNullableDouble(resultSet, "cpu_usage_average"),
                                        getNullableDouble(resultSet, "cpu_usage_minimum"),
                                        getNullableDouble(resultSet, "cpu_usage_maximum")
                                ),
                                new SensorMetricStatistics(
                                        getNullableDouble(resultSet, "memory_usage_average"),
                                        getNullableDouble(resultSet, "memory_usage_minimum"),
                                        getNullableDouble(resultSet, "memory_usage_maximum")
                                )
                        )
                ),
                Timestamp.valueOf(from),
                Timestamp.valueOf(to)
        );
    }

    public void writeHistoryCsv(
            OutputStream outputStream,
            LocalDateTime from,
            LocalDateTime to
    ) throws IOException {
        validateRange(from, to);
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
        );
        writer.write("Timestamp,Temperature (°C),CPU Usage (%),Memory Usage (%)\n");

        jdbcTemplate.query(
                """
                        select created_at, temperature, cpu_usage, memory_usage
                        from sensor_data
                        where created_at >= ? and created_at < ?
                        order by created_at asc, id asc
                        """,
                resultSet -> {
                    try {
                        writer.write(csvCell(resultSet.getTimestamp("created_at").toLocalDateTime().toString()));
                        writer.write(',');
                        writer.write(csvCell(resultSet.getObject("temperature")));
                        writer.write(',');
                        writer.write(csvCell(resultSet.getObject("cpu_usage")));
                        writer.write(',');
                        writer.write(csvCell(resultSet.getObject("memory_usage")));
                        writer.newLine();
                    } catch (IOException exception) {
                        throw new CsvWriteException(exception);
                    }
                },
                Timestamp.valueOf(from),
                Timestamp.valueOf(to)
        );
        writer.flush();
    }

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw badRequest("from은 to보다 이전이어야 합니다.");
        }
        if (Duration.between(from, to).compareTo(HISTORY_RETENTION) > 0) {
            throw badRequest("조회 구간은 최대 14일입니다.");
        }
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private Double getNullableDouble(java.sql.ResultSet resultSet, String column)
            throws java.sql.SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private String csvCell(Object value) {
        String escaped = String.valueOf(value == null ? "" : value)
                .replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static class CsvWriteException extends RuntimeException {
        CsvWriteException(IOException cause) {
            super(cause);
        }
    }
}
