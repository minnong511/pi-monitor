<script setup>
import { onMounted, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'

import RecentDataTable from '@/components/RecentDataTable.vue'
import StatusCard from '@/components/StatusCard.vue'
import TemperatureChart from '@/components/TemperatureChart.vue'
import { useSensorStore } from '@/stores/sensor'

const sensorStore = useSensorStore()

const {
  latestSensor,
  isConnected,
  isLoading,
  errorMessage,
} = storeToRefs(sensorStore)

function displayNumber(value) {
  return value == null ? '--' : value.toFixed(1)
}

onMounted(async () => {
  await sensorStore.fetchHistory()
  sensorStore.connectWebSocket()
})

onUnmounted(() => {
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
        :class="{ 'connection--online': isConnected }"
      >
        {{ isConnected ? 'Live' : 'Disconnected' }}
      </span>
    </header>

    <p v-if="isLoading">과거 데이터를 불러오는 중입니다.</p>
    <p v-if="errorMessage" class="error">
      {{ errorMessage }}
    </p>

    <section class="status-grid">
      <StatusCard
        label="CPU Temperature"
        :value="displayNumber(latestSensor?.temperature)"
        unit="°C"
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
        :value="isConnected ? 'Online' : 'Offline'"
        :tone="isConnected ? 'online' : 'offline'"
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

@media (max-width: 850px) {
  .status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .status-grid {
    grid-template-columns: 1fr;
  }
}
</style>
