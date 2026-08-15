<script setup>
import {
  computed,
  onMounted,
  onUnmounted,
  ref,
} from 'vue'
import { storeToRefs } from 'pinia'

import RecentDataTable from '@/components/RecentDataTable.vue'
import StatusCard from '@/components/StatusCard.vue'
import TemperatureChart from '@/components/TemperatureChart.vue'
import { useSensorStore } from '@/stores/sensor'

const sensorStore = useSensorStore()
const TEMPERATURE_WARNING_CELSIUS = 75
const OFFLINE_TIMEOUT_MS = 20_000
const currentTime = ref(Date.now())
let clockTimer = null

const {
  latestSensor,
  isConnected,
  isLoading,
  errorMessage,
} = storeToRefs(sensorStore)

function displayNumber(value) {
  return value == null ? '--' : value.toFixed(1)
}

const lastReceivedTime = computed(() => {
  const timestamp = latestSensor.value?.createdAt

  if (!timestamp) {
    return null
  }

  const time = new Date(timestamp).getTime()
  return Number.isFinite(time) ? time : null
})

const lastReceivedAge = computed(() => {
  if (lastReceivedTime.value == null) {
    return Number.POSITIVE_INFINITY
  }

  return Math.max(0, currentTime.value - lastReceivedTime.value)
})

const isSensorOnline = computed(() => (
  isConnected.value
  && lastReceivedAge.value <= OFFLINE_TIMEOUT_MS
))

const isTemperatureWarning = computed(() => (
  latestSensor.value?.temperature >= TEMPERATURE_WARNING_CELSIUS
))

const lastReceivedLabel = computed(() => {
  if (lastReceivedTime.value == null) {
    return 'No sensor data received'
  }

  const seconds = Math.floor(lastReceivedAge.value / 1000)

  if (seconds < 60) {
    return `Last received ${seconds}s ago`
  }

  const minutes = Math.floor(seconds / 60)

  if (minutes < 60) {
    return `Last received ${minutes}m ago`
  }

  const hours = Math.floor(minutes / 60)
  return `Last received ${hours}h ago`
})

onMounted(async () => {
  clockTimer = window.setInterval(() => {
    currentTime.value = Date.now()
  }, 1000)

  await sensorStore.fetchHistory()
  sensorStore.connectWebSocket()
})

onUnmounted(() => {
  if (clockTimer != null) {
    window.clearInterval(clockTimer)
  }

  sensorStore.disconnectWebSocket()
})
</script>

<template>
  <main class="dashboard">
    <header class="dashboard__header">
      <div>
        <p class="eyebrow">RASPBERRY PI MONITOR</p>
        <h1>System Dashboard</h1>
      </div>

      <span
        class="connection"
        :class="{ 'connection--online': isSensorOnline }"
      >
        {{ isSensorOnline ? 'Live' : 'Offline' }}
      </span>
    </header>

    <p v-if="isLoading">과거 데이터를 불러오는 중입니다.</p>
    <p v-if="errorMessage" class="error">
      {{ errorMessage }}
    </p>

    <div v-if="isTemperatureWarning" class="temperature-alert">
      <strong>High temperature warning</strong>
      <span>
        CPU temperature is
        {{ displayNumber(latestSensor?.temperature) }}°C.
        The warning threshold is {{ TEMPERATURE_WARNING_CELSIUS }}°C.
      </span>
    </div>

    <section class="status-grid">
      <StatusCard
        label="CPU Temperature"
        :value="displayNumber(latestSensor?.temperature)"
        unit="°C"
        :tone="isTemperatureWarning ? 'warning' : 'normal'"
        :detail="isTemperatureWarning ? 'Threshold exceeded' : 'Normal range'"
      />

      <StatusCard
        label="CPU Usage"
        :value="displayNumber(latestSensor?.cpuUsage)"
        unit="%"
      />

      <StatusCard
        label="Memory Usage"
        :value="displayNumber(latestSensor?.memoryUsage)"
        unit="%"
      />

      <StatusCard
        label="Online Status"
        :value="isSensorOnline ? 'Online' : 'Offline'"
        :tone="isSensorOnline ? 'online' : 'offline'"
        :detail="lastReceivedLabel"
      />
    </section>

    <TemperatureChart />
    <RecentDataTable />
  </main>
</template>

<style scoped>
.dashboard {
  display: grid;
  gap: 24px;
  width: min(1200px, calc(100% - 32px));
  margin: 0 auto;
  padding: 40px 0;
}

.dashboard__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dashboard__header h1 {
  margin: 4px 0 0;
  color: #0f172a;
}

.eyebrow {
  margin: 0;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.connection {
  padding: 8px 12px;
  border-radius: 999px;
  color: #991b1b;
  background: #fee2e2;
  font-size: 13px;
  font-weight: 700;
}

.connection--online {
  color: #166534;
  background: #dcfce7;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.error {
  padding: 12px 16px;
  border-radius: 8px;
  color: #991b1b;
  background: #fee2e2;
}

.temperature-alert {
  display: flex;
  gap: 8px 16px;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid #fca5a5;
  border-radius: 12px;
  color: #991b1b;
  background: #fff1f2;
}

.temperature-alert span {
  font-size: 14px;
}

@media (max-width: 850px) {
  .status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .status-grid {
    grid-template-columns: 1fr;
  }

  .temperature-alert {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
