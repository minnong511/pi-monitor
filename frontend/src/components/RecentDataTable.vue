<!-- Recent Data Table 작성 -->
<script setup>
import { computed } from 'vue'

import { useSensorStore } from '@/stores/sensor'

const sensorStore = useSensorStore()

const recentSensors = computed(() => {
  return [...sensorStore.sensors].reverse()
})

function formatTimestamp(timestamp) {
  return new Date(timestamp).toLocaleString('ko-KR')
}
</script>

<template>
  <section class="table-panel">
    <h2>Recent Sensor Data</h2>

    <div class="table-wrapper">
      <table>
        <thead>
          <tr>
            <th>Timestamp</th>
            <th>Temperature</th>
            <th>CPU Usage</th>
            <th>Memory Usage</th>
          </tr>
        </thead>

        <tbody>
          <tr
            v-for="sensor in recentSensors"
            :key="sensor.id"
          >
            <td>{{ formatTimestamp(sensor.createdAt) }}</td>
            <td>{{ sensor.temperature.toFixed(1) }} °C</td>
            <td>{{ sensor.cpuUsage.toFixed(1) }} %</td>
            <td>{{ sensor.memoryUsage.toFixed(1) }} %</td>
          </tr>

          <tr v-if="recentSensors.length === 0">
            <td colspan="4" class="empty">
              수신된 센서 데이터가 없습니다.
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.table-panel {
  padding: 24px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: white;
}

.table-panel h2 {
  margin: 0 0 20px;
  font-size: 18px;
}

.table-wrapper {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 13px 16px;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
  white-space: nowrap;
}

th {
  color: #475569;
  background: #f8fafc;
  font-size: 13px;
}

td {
  color: #334155;
  font-size: 14px;
}

.empty {
  padding: 32px;
  color: #94a3b8;
  text-align: center;
}
</style>
