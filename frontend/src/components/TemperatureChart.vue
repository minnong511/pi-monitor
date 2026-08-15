<!-- 실시간 온도 Chart 작성 -->
<script setup>
import { computed } from 'vue'
import { Line } from 'vue-chartjs'
import {
  CategoryScale,
  Chart as ChartJS,
  Legend,
  LineElement,
  LinearScale,
  PointElement,
  Title,
  Tooltip,
} from 'chart.js'

import { useSensorStore } from '@/stores/sensor'

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
)

const sensorStore = useSensorStore()

function formatTime(timestamp) {
  return new Date(timestamp).toLocaleTimeString('ko-KR', {
    hour12: false,
  })
}

const chartData = computed(() => ({
  labels: sensorStore.sensors.map((sensor) =>
    formatTime(sensor.createdAt),
  ),
  datasets: [
    {
      label: 'CPU Temperature',
      data: sensorStore.sensors.map(
        (sensor) => sensor.temperature,
      ),
      borderColor: '#f97316',
      backgroundColor: '#fed7aa',
      pointBackgroundColor: '#f97316',
      borderWidth: 2,
      tension: 0.25,
    },
  ],
}))

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  animation: {
    duration: 250,
  },
  scales: {
    y: {
      title: {
        display: true,
        text: 'Temperature (°C)',
      },
    },
  },
}
</script>

<template>
  <section class="chart-panel">
    <h2>Temperature History</h2>

    <div class="chart-wrapper">
      <Line
        :data="chartData"
        :options="chartOptions"
      />
    </div>
  </section>
</template>

<style scoped>
.chart-panel {
  padding: 24px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: white;
}

.chart-panel h2 {
  margin: 0 0 20px;
  font-size: 18px;
}

.chart-wrapper {
  height: 320px;
}
</style>
