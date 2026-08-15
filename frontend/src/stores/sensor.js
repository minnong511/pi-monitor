import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { Client } from '@stomp/stompjs'

import api from '@/services/api'

const MAX_SENSOR_COUNT = 20

let stompClient = null

export const useSensorStore = defineStore('sensor', () => {
  const sensors = ref([])
  const isConnected = ref(false)
  const isLoading = ref(false)
  const errorMessage = ref('')

  const latestSensor = computed(() => {
    return sensors.value.at(-1) ?? null
  })

  async function fetchHistory() {
    isLoading.value = true
    errorMessage.value = ''

    try {
      const response = await api.get('/api/sensors')

      /*
       * 백엔드는 최신순으로 반환합니다.
       * 최근 20개를 선택한 후 시간 오름차순으로 뒤집습니다.
       */
      sensors.value = response.data
        .slice(0, MAX_SENSOR_COUNT)
        .reverse()
    } catch (error) {
      console.error(error)
      errorMessage.value = '과거 센서 데이터를 가져오지 못했습니다.'
    } finally {
      isLoading.value = false
    }
  }

  function addSensor(sensorData) {
    const alreadyExists = sensors.value.some(
      (sensor) => sensor.id === sensorData.id,
    )

    if (alreadyExists) {
      return
    }

    sensors.value.push(sensorData)

    if (sensors.value.length > MAX_SENSOR_COUNT) {
      sensors.value.splice(
        0,
        sensors.value.length - MAX_SENSOR_COUNT,
      )
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
    latestSensor,
    isConnected,
    isLoading,
    errorMessage,
    fetchHistory,
    connectWebSocket,
    disconnectWebSocket,
  }
})