import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { Client } from '@stomp/stompjs'

import api from '@/services/api'

const MAX_RECENT_SENSOR_COUNT = 20
const HISTORY_RETENTION_MS = 7 * 24 * 60 * 60 * 1000

let stompClient = null

export const useSensorStore = defineStore('sensor', () => {
  const sensors = ref([])
  const sensorHistory = ref([])
  const isConnected = ref(false)
  const isLoading = ref(false)
  const errorMessage = ref('')
  const knownSensorIds = new Set()

  const latestSensor = computed(() => {
    return sensors.value.at(-1) ?? null
  })

  async function fetchHistory() {
    isLoading.value = true
    errorMessage.value = ''

    try {
      const response = await api.get('/api/sensors')
      const cutoffTime = Date.now() - HISTORY_RETENTION_MS

      /*
       * 백엔드는 최신순으로 반환하므로 프론트에서는 시간 오름차순으로
       * 보관합니다. 표에는 최근 20개만 사용하고 차트/CSV에는 전체 이력을
       * 사용합니다.
       */
      sensorHistory.value = response.data
        .filter(
          (sensor) => new Date(sensor.createdAt).getTime() >= cutoffTime,
        )
        .reverse()

      sensors.value = sensorHistory.value.slice(
        -MAX_RECENT_SENSOR_COUNT,
      )

      knownSensorIds.clear()
      sensorHistory.value.forEach((sensor) => {
        knownSensorIds.add(sensor.id)
      })
    } catch (error) {
      console.error(error)
      errorMessage.value = '과거 센서 데이터를 가져오지 못했습니다.'
    } finally {
      isLoading.value = false
    }
  }

  function addSensor(sensorData) {
    if (knownSensorIds.has(sensorData.id)) {
      return
    }

    knownSensorIds.add(sensorData.id)
    sensorHistory.value.push(sensorData)
    sensors.value.push(sensorData)

    if (sensors.value.length > MAX_RECENT_SENSOR_COUNT) {
      sensors.value.splice(
        0,
        sensors.value.length - MAX_RECENT_SENSOR_COUNT,
      )
    }

    const cutoffTime = Date.now() - HISTORY_RETENTION_MS

    while (
      sensorHistory.value.length > 0
      && new Date(sensorHistory.value[0].createdAt).getTime()
        < cutoffTime
    ) {
      const removedSensor = sensorHistory.value.shift()
      knownSensorIds.delete(removedSensor.id)
    }
  }

  function connectWebSocket() {
    if (stompClient?.active) {
      return
    }

    stompClient = new Client({
      brokerURL: import.meta.env.VITE_WS_URL,
      reconnectDelay: 5000,
      debug: () => {},
    })

    stompClient.onConnect = () => {
      isConnected.value = true
      errorMessage.value = ''

      stompClient.subscribe('/topic/sensors', (message) => {
        try {
          const sensorData = JSON.parse(message.body)
          addSensor(sensorData)
        } catch (error) {
          console.error('WebSocket JSON 변환 실패', error)
        }
      })
    }

    stompClient.onWebSocketClose = () => {
      isConnected.value = false
    }

    stompClient.onWebSocketError = (error) => {
      console.error('WebSocket 오류', error)
      errorMessage.value = 'WebSocket 연결에 실패했습니다.'
    }

    stompClient.onStompError = (frame) => {
      console.error('STOMP 오류', frame)
      errorMessage.value = 'STOMP 메시지 처리 중 오류가 발생했습니다.'
    }

    stompClient.activate()
  }

  async function disconnectWebSocket() {
    if (stompClient) {
      await stompClient.deactivate()
      stompClient = null
    }

    isConnected.value = false
  }

  return {
    sensors,
    sensorHistory,
    latestSensor,
    isConnected,
    isLoading,
    errorMessage,
    fetchHistory,
    connectWebSocket,
    disconnectWebSocket,
  }
})
