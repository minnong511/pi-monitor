import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { Client } from '@stomp/stompjs'

import api from '@/services/api'

const MAX_RECENT_SENSOR_COUNT = 20

let stompClient = null

export const useSensorStore = defineStore('sensor', () => {
  const sensors = ref([])
  const isConnected = ref(false)
  const isLoading = ref(false)
  const errorMessage = ref('')
  const sensorEventVersion = ref(0)
  const knownSensorIds = new Set()

  const latestSensor = computed(() => sensors.value.at(-1) ?? null)

  async function fetchRecent() {
    isLoading.value = true
    errorMessage.value = ''

    try {
      const response = await api.get('/api/sensors/recent', {
        params: { limit: MAX_RECENT_SENSOR_COUNT },
      })
      const byId = new Map(sensors.value.map((sensor) => [sensor.id, sensor]))
      response.data.forEach((sensor) => byId.set(sensor.id, sensor))
      const recentSensors = [...byId.values()]
        .sort((first, second) => (
          new Date(first.createdAt).getTime() - new Date(second.createdAt).getTime()
          || first.id - second.id
        ))
        .slice(-MAX_RECENT_SENSOR_COUNT)

      sensors.value = recentSensors
      knownSensorIds.clear()
      recentSensors.forEach((sensor) => knownSensorIds.add(sensor.id))
      sensorEventVersion.value += 1
    } catch (error) {
      console.error(error)
      errorMessage.value = '최근 센서 데이터를 가져오지 못했습니다.'
    } finally {
      isLoading.value = false
    }
  }

  function addSensor(sensorData) {
    if (knownSensorIds.has(sensorData.id)) {
      return
    }

    knownSensorIds.add(sensorData.id)
    sensors.value.push(sensorData)

    if (sensors.value.length > MAX_RECENT_SENSOR_COUNT) {
      const removedSensor = sensors.value.shift()
      knownSensorIds.delete(removedSensor.id)
    }

    sensorEventVersion.value += 1
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
          addSensor(JSON.parse(message.body))
        } catch (error) {
          console.error('WebSocket JSON 변환 실패', error)
        }
      })
      void fetchRecent()
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
    latestSensor,
    isConnected,
    isLoading,
    errorMessage,
    sensorEventVersion,
    fetchRecent,
    connectWebSocket,
    disconnectWebSocket,
  }
})
