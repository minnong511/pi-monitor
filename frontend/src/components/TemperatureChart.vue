<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Line } from 'vue-chartjs'
import { CategoryScale, Chart as ChartJS, Legend, LineElement, LinearScale, PointElement, Tooltip } from 'chart.js'
import zoomPlugin from 'chartjs-plugin-zoom'

import api from '@/services/api'
import { useSensorStore } from '@/stores/sensor'
import { moveRangeEnd, rangeStartFor, toKoreanLocalIso } from '@/utils/historyRange'

const LIVE_REFRESH_DELAY_MS = 500
const TEMPERATURE_WARNING_CELSIUS = 75

const metricOptions = {
  temperature: {
    buttonLabel: 'Temperature',
    title: 'Temperature History',
    datasetLabel: 'CPU Temperature',
    field: 'temperature',
    unit: '°C',
    axisTitle: 'Temperature (°C)',
    borderColor: '#f97316',
    backgroundColor: '#fed7aa',
    suggestedMin: 30,
    suggestedMax: 80,
  },
  cpuUsage: {
    buttonLabel: 'CPU Usage',
    title: 'CPU Usage History',
    datasetLabel: 'CPU Usage',
    field: 'cpuUsage',
    unit: '%',
    axisTitle: 'CPU Usage (%)',
    borderColor: '#2563eb',
    backgroundColor: '#bfdbfe',
    suggestedMin: 0,
    suggestedMax: 100,
  },
  memoryUsage: {
    buttonLabel: 'Memory Usage',
    title: 'Memory Usage History',
    datasetLabel: 'Memory Usage',
    field: 'memoryUsage',
    unit: '%',
    axisTitle: 'Memory Usage (%)',
    borderColor: '#7c3aed',
    backgroundColor: '#ddd6fe',
    suggestedMin: 0,
    suggestedMax: 100,
  },
}

const rangeOptions = {
  '1h': {
    label: '1 hour',
    shortLabel: '1H',
    durationMs: 60 * 60 * 1000,
    bucketSeconds: 60,
  },
  '6h': {
    label: '6 hours',
    shortLabel: '6H',
    durationMs: 6 * 60 * 60 * 1000,
    bucketSeconds: 300,
  },
  '24h': {
    label: '24 hours',
    shortLabel: '24H',
    durationMs: 24 * 60 * 60 * 1000,
    bucketSeconds: 900,
  },
  '7d': {
    label: '7 days',
    shortLabel: '7D',
    durationMs: 7 * 24 * 60 * 60 * 1000,
    bucketSeconds: 7200,
  },
  '14d': {
    label: '14 days',
    shortLabel: '14D',
    durationMs: 14 * 24 * 60 * 60 * 1000,
    bucketSeconds: 14400,
  },
}

const valueLabelsPlugin = {
  id: 'valueLabels',
  afterDatasetsDraw(chart, _args, options) {
    const { chartArea, ctx } = chart
    chart.data.datasets.forEach((dataset, datasetIndex) => {
      const points = chart.getDatasetMeta(datasetIndex).data
      const visiblePoints = points.map((point, dataIndex) => ({ dataIndex, point })).filter(({ point }) => point.x >= chartArea.left && point.x <= chartArea.right && point.y >= chartArea.top && point.y <= chartArea.bottom)
      const labelStep = Math.max(1, Math.ceil(visiblePoints.length / (chart.width < 640 ? 6 : 12)))
      visiblePoints.forEach(({ dataIndex, point }, visibleIndex) => {
        if (visibleIndex % labelStep !== 0 && visibleIndex !== visiblePoints.length - 1) return
        const value = Number(dataset.data[dataIndex])
        if (!Number.isFinite(value)) return
        ctx.save()
        ctx.fillStyle = Array.isArray(dataset.pointBackgroundColor) ? dataset.pointBackgroundColor[dataIndex] : dataset.borderColor
        ctx.font = '600 11px Inter, sans-serif'
        ctx.textAlign = 'center'
        ctx.textBaseline = 'bottom'
        ctx.fillText(`${value.toFixed(1)}${options.unit ?? ''}`, point.x, point.y - 8)
        ctx.restore()
      })
    })
  },
}

const thresholdLinePlugin = {
  id: 'thresholdLine',
  beforeDatasetsDraw(chart, _args, options) {
    if (!options.display) return
    const { ctx, chartArea, scales } = chart
    const y = scales.y.getPixelForValue(options.value)
    if (y < chartArea.top || y > chartArea.bottom) return
    ctx.save()
    ctx.strokeStyle = options.color
    ctx.fillStyle = options.color
    ctx.lineWidth = 1
    ctx.setLineDash([6, 5])
    ctx.beginPath()
    ctx.moveTo(chartArea.left, y)
    ctx.lineTo(chartArea.right, y)
    ctx.stroke()
    ctx.setLineDash([])
    ctx.font = '600 11px Inter, sans-serif'
    ctx.textAlign = 'right'
    ctx.textBaseline = 'bottom'
    ctx.fillText(`Warning ${options.value}°C`, chartArea.right, y - 4)
    ctx.restore()
  },
}

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend, zoomPlugin, valueLabelsPlugin, thresholdLinePlugin)

const sensorStore = useSensorStore()
const chartRef = ref(null)
const selectedMetricKey = ref('temperature')
const selectedRangeKey = ref('1h')
const rangeEnd = ref(Date.now())
const isCurrentView = ref(true)
const summary = ref(null)
const isLoading = ref(false)
const errorMessage = ref('')
const hasNewData = ref(false)
const isZoomed = ref(false)
let liveRefreshTimer = null
let summaryRequestId = 0

const selectedMetric = computed(() => metricOptions[selectedMetricKey.value])
const selectedRange = computed(() => rangeOptions[selectedRangeKey.value])
const rangeStart = computed(() => rangeStartFor(rangeEnd.value, selectedRange.value.durationMs))
const chartSensors = computed(() => summary.value?.points ?? [])
const rangeStatistics = computed(() => summary.value?.statistics?.[selectedMetricKey.value] ?? null)
const recordCount = computed(() => summary.value?.recordCount ?? 0)
const hasTemperatureWarning = computed(() => selectedMetricKey.value === 'temperature' && rangeStatistics.value?.maximum >= TEMPERATURE_WARNING_CELSIUS)

function formatTime(timestamp) {
  const options =
    selectedRange.value.durationMs >= 24 * 60 * 60 * 1000
      ? {
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          hour12: false,
        }
      : {
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          hour12: false,
        }
  return new Date(timestamp).toLocaleString('ko-KR', options)
}

async function fetchSummary() {
  const requestId = ++summaryRequestId
  isLoading.value = true
  errorMessage.value = ''
  try {
    const response = await api.get('/api/sensors/history/summary', {
      params: {
        from: toKoreanLocalIso(rangeStart.value),
        to: toKoreanLocalIso(rangeEnd.value),
        bucketSeconds: selectedRange.value.bucketSeconds,
      },
    })
    if (requestId !== summaryRequestId) return
    summary.value = response.data
    hasNewData.value = false
  } catch (error) {
    if (requestId !== summaryRequestId) return
    console.error(error)
    errorMessage.value = '선택한 기간의 그래프 데이터를 가져오지 못했습니다.'
  } finally {
    if (requestId === summaryRequestId) isLoading.value = false
  }
}

function syncZoomState(chart) {
  isZoomed.value = chart.isZoomedOrPanned()
}

function resetZoom() {
  const chart = chartRef.value?.chart
  if (chart) chart.resetZoom('none')
  isZoomed.value = false
}

function selectRange(rangeKey) {
  resetZoom()
  selectedRangeKey.value = rangeKey
  if (isCurrentView.value) rangeEnd.value = Date.now()
  void fetchSummary()
}

function movePrevious() {
  resetZoom()
  const nextRange = moveRangeEnd(rangeEnd.value, selectedRange.value.durationMs, 'previous', Date.now())
  rangeEnd.value = nextRange.rangeEnd
  isCurrentView.value = nextRange.isCurrentView
  void fetchSummary()
}

function moveNext() {
  resetZoom()
  const nextRange = moveRangeEnd(rangeEnd.value, selectedRange.value.durationMs, 'next', Date.now())
  rangeEnd.value = nextRange.rangeEnd
  isCurrentView.value = nextRange.isCurrentView
  if (nextRange.isCurrentView) hasNewData.value = false
  void fetchSummary()
}

function returnToCurrent() {
  resetZoom()
  rangeEnd.value = Date.now()
  isCurrentView.value = true
  hasNewData.value = false
  void fetchSummary()
}

function downloadCsv() {
  const downloadUrl = api.getUri({
    url: '/api/sensors/history/export',
    params: {
      from: toKoreanLocalIso(rangeStart.value),
      to: toKoreanLocalIso(rangeEnd.value),
      filename: `pi-monitor-${selectedRangeKey.value}-${new Date().toISOString().slice(0, 10)}.csv`,
    },
  })
  window.location.assign(downloadUrl)
}

watch(
  () => sensorStore.sensorEventVersion,
  () => {
    if (!isCurrentView.value) {
      hasNewData.value = true
      return
    }
    if (liveRefreshTimer != null) window.clearTimeout(liveRefreshTimer)
    liveRefreshTimer = window.setTimeout(() => {
      rangeEnd.value = Date.now()
      void fetchSummary()
    }, LIVE_REFRESH_DELAY_MS)
  },
)

const pointColors = computed(() => chartSensors.value.map((sensor) => (selectedMetricKey.value === 'temperature' && sensor.temperature >= TEMPERATURE_WARNING_CELSIUS ? '#dc2626' : selectedMetric.value.borderColor)))
const chartData = computed(() => ({
  labels: chartSensors.value.map((sensor) => formatTime(sensor.createdAt)),
  datasets: [
    {
      label: selectedMetric.value.datasetLabel,
      data: chartSensors.value.map((sensor) => sensor[selectedMetric.value.field]),
      borderColor: selectedMetric.value.borderColor,
      backgroundColor: selectedMetric.value.backgroundColor,
      pointBackgroundColor: pointColors.value,
      pointBorderColor: '#ffffff',
      pointBorderWidth: 2,
      pointRadius: 3,
      pointHoverRadius: 6,
      borderWidth: 2,
      tension: 0.3,
    },
  ],
}))
const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  animation: false,
  layout: { padding: { top: 22 } },
  interaction: { intersect: false, mode: 'index' },
  plugins: {
    legend: { display: false },
    tooltip: {
      callbacks: {
        label: (context) => `${selectedMetric.value.datasetLabel}: ${context.parsed.y.toFixed(1)}${selectedMetric.value.unit}`,
      },
    },
    zoom: {
      limits: { x: { min: 'original', max: 'original', minRange: 3 } },
      pan: {
        enabled: true,
        mode: 'x',
        modifierKey: 'shift',
        onPanComplete: ({ chart }) => syncZoomState(chart),
      },
      zoom: {
        mode: 'x',
        wheel: { enabled: true, speed: 0.08 },
        pinch: { enabled: true },
        drag: {
          enabled: true,
          threshold: 8,
          borderColor: '#2563eb',
          borderWidth: 1,
          backgroundColor: 'rgba(37, 99, 235, 0.12)',
        },
        onZoomComplete: ({ chart }) => syncZoomState(chart),
      },
    },
    valueLabels: { unit: selectedMetric.value.unit },
    thresholdLine: {
      display: selectedMetricKey.value === 'temperature',
      value: TEMPERATURE_WARNING_CELSIUS,
      color: '#dc2626',
    },
  },
  scales: {
    x: {
      grid: { display: false },
      ticks: {
        maxRotation: 40,
        minRotation: 0,
        autoSkip: true,
        maxTicksLimit: 10,
      },
    },
    y: {
      suggestedMin: selectedMetric.value.suggestedMin,
      suggestedMax: selectedMetric.value.suggestedMax,
      grace: '12%',
      title: { display: true, text: selectedMetric.value.axisTitle },
    },
  },
}))
function formatStatistic(value) {
  return value == null ? '--' : `${value.toFixed(1)}${selectedMetric.value.unit}`
}

onMounted(() => {
  void fetchSummary()
})
onUnmounted(() => {
  summaryRequestId += 1
  if (liveRefreshTimer != null) window.clearTimeout(liveRefreshTimer)
})
</script>

<template>
  <section class="chart-panel">
    <div class="chart-panel__header">
      <div>
        <div class="chart-title-row">
          <h2>{{ selectedMetric.title }}</h2>
          <span v-if="hasTemperatureWarning" class="warning-badge">75°C threshold exceeded</span>
          <span v-if="hasNewData" class="new-data-badge">New data available</span>
        </div>
        <p>{{ formatTime(rangeStart) }} – {{ formatTime(rangeEnd) }} · {{ recordCount.toLocaleString() }} records</p>
      </div>
      <div class="chart-actions">
        <button type="button" class="action-button" @click="movePrevious">Previous</button>
        <button type="button" class="action-button" :disabled="isCurrentView" @click="moveNext">Next</button>
        <button v-if="!isCurrentView" type="button" class="action-button action-button--primary" @click="returnToCurrent">현재로</button>
        <button type="button" class="action-button" :disabled="recordCount === 0" @click="downloadCsv">Download CSV</button>
      </div>
    </div>
    <div class="chart-controls">
      <div class="metric-tabs" role="group" aria-label="Chart metric">
        <button v-for="(metric, key) in metricOptions" :key="key" type="button" class="control-tab" :class="{ 'control-tab--active': selectedMetricKey === key }" :aria-pressed="selectedMetricKey === key" @click="selectedMetricKey = key">
          {{ metric.buttonLabel }}
        </button>
      </div>
      <div class="range-tabs" role="group" aria-label="Chart time range">
        <button v-for="(range, key) in rangeOptions" :key="key" type="button" class="control-tab" :class="{ 'control-tab--active': selectedRangeKey === key }" :aria-pressed="selectedRangeKey === key" :title="`Show ${range.label}`" @click="selectRange(key)">
          {{ range.shortLabel }}
        </button>
      </div>
    </div>
    <p v-if="errorMessage" class="chart-error">{{ errorMessage }}</p>
    <div class="statistics-grid">
      <article>
        <span>Average</span><strong>{{ formatStatistic(rangeStatistics?.average) }}</strong>
      </article>
      <article>
        <span>Minimum</span><strong>{{ formatStatistic(rangeStatistics?.minimum) }}</strong>
      </article>
      <article :class="{ 'statistic--warning': hasTemperatureWarning }">
        <span>Maximum</span><strong>{{ formatStatistic(rangeStatistics?.maximum) }}</strong>
      </article>
    </div>
    <div class="chart-toolbar">
      <span>Drag or scroll to zoom · Shift + drag to move</span>
      <button type="button" class="zoom-reset" :disabled="!isZoomed" @click="resetZoom">Reset zoom</button>
    </div>
    <div class="chart-stage" :aria-busy="isLoading">
      <div v-if="chartSensors.length > 0" class="chart-wrapper">
        <Line ref="chartRef" :data="chartData" :options="chartOptions" />
      </div>
      <div v-else-if="isLoading" class="chart-state">그래프 데이터를 불러오는 중입니다.</div>
      <div v-else class="chart-state">No sensor data exists in the selected time range.</div>
      <span v-if="isLoading && chartSensors.length > 0" class="chart-refresh-indicator">Updating…</span>
    </div>
  </section>
</template>

<style scoped>
.chart-panel { padding: 24px; overflow: hidden; border: 1px solid #e2e8f0; border-radius: 16px; background: white; }
.chart-panel__header, .chart-controls { display: flex; gap: 16px; align-items: flex-start; justify-content: space-between; }.chart-panel__header { margin-bottom: 16px; }.chart-title-row, .chart-actions, .metric-tabs, .range-tabs { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }.chart-panel h2 { margin: 0; font-size: 18px; }.chart-panel p { margin: 6px 0 0; color: #64748b; font-size: 12px; }
.warning-badge, .new-data-badge { padding: 4px 7px; border-radius: 999px; font-size: 11px; font-weight: 700; }.warning-badge { color: #b91c1c; background: #fee2e2; }.new-data-badge { color: #1d4ed8; background: #dbeafe; }.action-button, .control-tab { border: 0; cursor: pointer; font-size: 12px; font-weight: 700; }.action-button { padding: 8px 11px; border: 1px solid #cbd5e1; border-radius: 8px; color: #334155; background: white; }.action-button:hover:not(:disabled) { background: #f8fafc; }.action-button:disabled { cursor: not-allowed; opacity: .45; }.action-button--primary { border-color: #2563eb; color: white; background: #2563eb; }.control-tab { padding: 7px 10px; border-radius: 7px; color: #64748b; background: #f1f5f9; }.control-tab--active { color: white; background: #0f172a; }
.statistics-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 18px 0; }.statistics-grid article { display: grid; gap: 4px; padding: 12px; border-radius: 10px; background: #f8fafc; }.statistics-grid span { color: #64748b; font-size: 12px; }.statistics-grid strong { color: #0f172a; font-size: 17px; }.statistic--warning strong { color: #dc2626; }
.chart-toolbar { display: flex; min-height: 32px; align-items: center; justify-content: flex-end; gap: 12px; color: #64748b; font-size: 11px; }.zoom-reset { padding: 6px 9px; border: 1px solid #cbd5e1; border-radius: 7px; color: #334155; background: white; cursor: pointer; font-size: 11px; font-weight: 700; }.zoom-reset:hover:not(:disabled) { border-color: #94a3b8; background: #f8fafc; }.zoom-reset:disabled { cursor: default; opacity: .45; }
.chart-stage { position: relative; height: 360px; }.chart-wrapper { height: 100%; }.chart-state { display: grid; height: 100%; place-items: center; color: #64748b; font-size: 12px; text-align: center; }.chart-refresh-indicator { position: absolute; z-index: 2; top: 8px; right: 8px; padding: 5px 8px; border: 1px solid #dbeafe; border-radius: 999px; color: #1d4ed8; background: rgba(239, 246, 255, .92); font-size: 10px; font-weight: 700; pointer-events: none; }.chart-error { padding: 10px 0 0; color: #b91c1c !important; }
@media (max-width: 720px) { .chart-panel__header, .chart-controls { flex-direction: column; }.chart-actions { width: 100%; }.statistics-grid { grid-template-columns: 1fr; }.chart-toolbar { justify-content: space-between; }.chart-stage { height: 300px; } }
</style>
