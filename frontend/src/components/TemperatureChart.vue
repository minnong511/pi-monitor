<script setup>
import {
  computed,
  onMounted,
  onUnmounted,
  ref,
  watch,
} from 'vue'
import { Line } from 'vue-chartjs'
import {
  CategoryScale,
  Chart as ChartJS,
  Legend,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js'

import { useSensorStore } from '@/stores/sensor'

const CHART_REFRESH_INTERVAL_MS = 10_000
const TEMPERATURE_WARNING_CELSIUS = 75
const MAX_CHART_POINTS = 180

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
  },
  '6h': {
    label: '6 hours',
    shortLabel: '6H',
    durationMs: 6 * 60 * 60 * 1000,
  },
  '24h': {
    label: '24 hours',
    shortLabel: '24H',
    durationMs: 24 * 60 * 60 * 1000,
  },
  '7d': {
    label: '7 days',
    shortLabel: '7D',
    durationMs: 7 * 24 * 60 * 60 * 1000,
  },
}

const valueLabelsPlugin = {
  id: 'valueLabels',
  afterDatasetsDraw(chart, _args, options) {
    const { ctx } = chart

    chart.data.datasets.forEach((dataset, datasetIndex) => {
      const meta = chart.getDatasetMeta(datasetIndex)
      const targetLabelCount = chart.width < 640 ? 6 : 12
      const labelStep = Math.max(
        1,
        Math.ceil(meta.data.length / targetLabelCount),
      )

      meta.data.forEach((point, dataIndex) => {
        const isLastPoint = dataIndex === meta.data.length - 1

        if (dataIndex % labelStep !== 0 && !isLastPoint) {
          return
        }

        const value = Number(dataset.data[dataIndex])

        if (!Number.isFinite(value)) {
          return
        }

        const pointColor = Array.isArray(dataset.pointBackgroundColor)
          ? dataset.pointBackgroundColor[dataIndex]
          : dataset.borderColor

        ctx.save()
        ctx.fillStyle = pointColor
        ctx.font = '600 11px Inter, sans-serif'
        ctx.textAlign = 'center'
        ctx.textBaseline = 'bottom'
        ctx.fillText(
          `${value.toFixed(1)}${options.unit ?? ''}`,
          point.x,
          point.y - 8,
        )
        ctx.restore()
      })
    })
  },
}

const thresholdLinePlugin = {
  id: 'thresholdLine',
  beforeDatasetsDraw(chart, _args, options) {
    if (!options.display) {
      return
    }

    const { ctx, chartArea, scales } = chart
    const y = scales.y.getPixelForValue(options.value)

    if (y < chartArea.top || y > chartArea.bottom) {
      return
    }

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
    ctx.fillText(
      `Warning ${options.value}°C`,
      chartArea.right,
      y - 4,
    )
    ctx.restore()
  },
}

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Legend,
  valueLabelsPlugin,
  thresholdLinePlugin,
)

const sensorStore = useSensorStore()
const selectedMetricKey = ref('temperature')
const selectedRangeKey = ref('1h')
const displayedSensors = ref([])
const chartUpdatedAt = ref(null)
const isPaused = ref(false)
let refreshTimer = null

const selectedMetric = computed(
  () => metricOptions[selectedMetricKey.value],
)

const selectedRange = computed(
  () => rangeOptions[selectedRangeKey.value],
)

function formatTime(timestamp) {
  const options = selectedRange.value.durationMs >= 24 * 60 * 60 * 1000
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

function refreshChart({ force = false } = {}) {
  if (isPaused.value && !force) {
    return
  }

  const cutoffTime = Date.now() - selectedRange.value.durationMs

  displayedSensors.value = sensorStore.sensorHistory.filter(
    (sensor) => new Date(sensor.createdAt).getTime() >= cutoffTime,
  )
  chartUpdatedAt.value = new Date()
}

function selectRange(rangeKey) {
  selectedRangeKey.value = rangeKey
  refreshChart({ force: true })
}

function togglePause() {
  isPaused.value = !isPaused.value

  if (!isPaused.value) {
    refreshChart({ force: true })
  }
}

watch(
  () => sensorStore.sensorHistory.length,
  (sensorCount) => {
    if (sensorCount > 0 && displayedSensors.value.length === 0) {
      refreshChart({ force: true })
    }
  },
)

onMounted(() => {
  refreshChart({ force: true })
  refreshTimer = window.setInterval(
    refreshChart,
    CHART_REFRESH_INTERVAL_MS,
  )
})

onUnmounted(() => {
  if (refreshTimer != null) {
    window.clearInterval(refreshTimer)
  }
})

const chartSensors = computed(() => {
  const source = displayedSensors.value

  if (source.length <= MAX_CHART_POINTS) {
    return source
  }

  const result = []
  const lastIndex = source.length - 1
  const step = lastIndex / (MAX_CHART_POINTS - 1)

  for (let index = 0; index < MAX_CHART_POINTS; index += 1) {
    result.push(source[Math.round(index * step)])
  }

  return result
})

const rangeStatistics = computed(() => {
  const values = displayedSensors.value
    .map((sensor) => Number(sensor[selectedMetric.value.field]))
    .filter(Number.isFinite)

  if (values.length === 0) {
    return null
  }

  const summary = values.reduce(
    (result, value) => ({
      total: result.total + value,
      minimum: Math.min(result.minimum, value),
      maximum: Math.max(result.maximum, value),
    }),
    {
      total: 0,
      minimum: Number.POSITIVE_INFINITY,
      maximum: Number.NEGATIVE_INFINITY,
    },
  )

  return {
    average: summary.total / values.length,
    minimum: summary.minimum,
    maximum: summary.maximum,
    count: values.length,
  }
})

const hasTemperatureWarning = computed(() => (
  selectedMetricKey.value === 'temperature'
  && rangeStatistics.value?.maximum >= TEMPERATURE_WARNING_CELSIUS
))

const pointColors = computed(() => chartSensors.value.map((sensor) => {
  if (
    selectedMetricKey.value === 'temperature'
    && sensor.temperature >= TEMPERATURE_WARNING_CELSIUS
  ) {
    return '#dc2626'
  }

  return selectedMetric.value.borderColor
}))

const chartData = computed(() => ({
  labels: chartSensors.value.map((sensor) =>
    formatTime(sensor.createdAt),
  ),
  datasets: [
    {
      label: selectedMetric.value.datasetLabel,
      data: chartSensors.value.map(
        (sensor) => sensor[selectedMetric.value.field],
      ),
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
  animation: {
    duration: 900,
    easing: 'easeOutQuart',
  },
  layout: {
    padding: {
      top: 22,
    },
  },
  interaction: {
    intersect: false,
    mode: 'index',
  },
  plugins: {
    legend: {
      display: false,
    },
    tooltip: {
      callbacks: {
        label: (context) => (
          `${selectedMetric.value.datasetLabel}: `
          + `${context.parsed.y.toFixed(1)}${selectedMetric.value.unit}`
        ),
      },
    },
    valueLabels: {
      unit: selectedMetric.value.unit,
    },
    thresholdLine: {
      display: selectedMetricKey.value === 'temperature',
      value: TEMPERATURE_WARNING_CELSIUS,
      color: '#dc2626',
    },
  },
  scales: {
    x: {
      grid: {
        display: false,
      },
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
      title: {
        display: true,
        text: selectedMetric.value.axisTitle,
      },
    },
  },
}))

const updateStatus = computed(() => {
  if (isPaused.value) {
    return 'Live chart paused'
  }

  if (!chartUpdatedAt.value) {
    return 'Waiting for data'
  }

  return `Updated ${formatTime(chartUpdatedAt.value)}`
})

function formatStatistic(value) {
  if (value == null) {
    return '--'
  }

  return `${value.toFixed(1)}${selectedMetric.value.unit}`
}

function csvCell(value) {
  return `"${String(value ?? '').replaceAll('"', '""')}"`
}

function downloadCsv() {
  if (displayedSensors.value.length === 0) {
    return
  }

  const header = [
    'Timestamp',
    'Temperature (°C)',
    'CPU Usage (%)',
    'Memory Usage (%)',
  ]
  const rows = displayedSensors.value.map((sensor) => [
    new Date(sensor.createdAt).toISOString(),
    sensor.temperature,
    sensor.cpuUsage,
    sensor.memoryUsage,
  ])
  const csv = [header, ...rows]
    .map((row) => row.map(csvCell).join(','))
    .join('\n')
  const blob = new Blob([`\uFEFF${csv}`], {
    type: 'text/csv;charset=utf-8',
  })
  const downloadUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  const dateText = new Date().toISOString().slice(0, 10)

  link.href = downloadUrl
  link.download = `pi-monitor-${selectedRangeKey.value}-${dateText}.csv`
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(downloadUrl), 0)
}
</script>

<template>
  <section class="chart-panel">
    <div class="chart-panel__header">
      <div>
        <div class="chart-title-row">
          <h2>{{ selectedMetric.title }}</h2>
          <span
            v-if="hasTemperatureWarning"
            class="warning-badge"
          >
            75°C threshold exceeded
          </span>
        </div>
        <p>
          {{ updateStatus }} · {{ displayedSensors.length.toLocaleString() }}
          records in the selected range
        </p>
      </div>

      <div class="chart-actions">
        <button
          type="button"
          class="action-button"
          :class="{ 'action-button--paused': isPaused }"
          @click="togglePause"
        >
          {{ isPaused ? 'Resume live' : 'Pause live' }}
        </button>
        <button
          type="button"
          class="action-button"
          :disabled="displayedSensors.length === 0"
          @click="downloadCsv"
        >
          Download CSV
        </button>
      </div>
    </div>

    <div class="chart-controls">
      <div
        class="metric-tabs"
        role="group"
        aria-label="Chart metric"
      >
        <button
          v-for="(metric, key) in metricOptions"
          :key="key"
          type="button"
          class="control-tab"
          :class="{ 'control-tab--active': selectedMetricKey === key }"
          :aria-pressed="selectedMetricKey === key"
          @click="selectedMetricKey = key"
        >
          {{ metric.buttonLabel }}
        </button>
      </div>

      <div
        class="range-tabs"
        role="group"
        aria-label="Chart time range"
      >
        <button
          v-for="(range, key) in rangeOptions"
          :key="key"
          type="button"
          class="control-tab"
          :class="{ 'control-tab--active': selectedRangeKey === key }"
          :aria-pressed="selectedRangeKey === key"
          :title="`Show the last ${range.label}`"
          @click="selectRange(key)"
        >
          {{ range.shortLabel }}
        </button>
      </div>
    </div>

    <div class="statistics-grid">
      <article>
        <span>Average</span>
        <strong>{{ formatStatistic(rangeStatistics?.average) }}</strong>
      </article>
      <article>
        <span>Minimum</span>
        <strong>{{ formatStatistic(rangeStatistics?.minimum) }}</strong>
      </article>
      <article :class="{ 'statistic--warning': hasTemperatureWarning }">
        <span>Maximum</span>
        <strong>{{ formatStatistic(rangeStatistics?.maximum) }}</strong>
      </article>
    </div>

    <div v-if="chartSensors.length > 0" class="chart-wrapper">
      <Line
        :data="chartData"
        :options="chartOptions"
      />
    </div>

    <div v-else class="chart-empty">
      No sensor data exists in the selected time range.
    </div>
  </section>
</template>

<style scoped>
.chart-panel {
  padding: 24px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: white;
}

.chart-panel__header,
.chart-controls {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.chart-panel__header {
  margin-bottom: 16px;
}

.chart-title-row,
.chart-actions,
.metric-tabs,
.range-tabs {
  display: flex;
  gap: 6px;
  align-items: center;
}

.chart-panel h2 {
  margin: 0;
  font-size: 18px;
}

.chart-panel p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
}

.warning-badge {
  padding: 4px 7px;
  border-radius: 999px;
  color: #b91c1c;
  background: #fee2e2;
  font-size: 11px;
  font-weight: 700;
}

.action-button,
.control-tab {
  border: 0;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}

.action-button {
  padding: 8px 11px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #334155;
  background: #ffffff;
}

.action-button:hover:not(:disabled) {
  border-color: #94a3b8;
  background: #f8fafc;
}

.action-button--paused {
  border-color: #f59e0b;
  color: #92400e;
  background: #fffbeb;
}

.action-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.chart-controls {
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
}

.metric-tabs,
.range-tabs {
  padding: 3px;
  border-radius: 9px;
  background: #e2e8f0;
}

.control-tab {
  padding: 7px 10px;
  border-radius: 7px;
  color: #475569;
  background: transparent;
}

.control-tab:hover,
.control-tab--active {
  color: #0f172a;
  background: #ffffff;
}

.action-button:focus-visible,
.control-tab:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}

.statistics-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 14px 0 4px;
}

.statistics-grid article {
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
}

.statistics-grid span {
  display: block;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}

.statistics-grid strong {
  display: block;
  margin-top: 5px;
  color: #0f172a;
  font-size: 18px;
}

.statistics-grid .statistic--warning {
  border-color: #fca5a5;
  background: #fff1f2;
}

.statistics-grid .statistic--warning strong {
  color: #b91c1c;
}

.chart-wrapper {
  height: 360px;
  margin-top: 6px;
}

.chart-empty {
  display: grid;
  height: 260px;
  place-items: center;
  color: #94a3b8;
  font-size: 14px;
}

@media (max-width: 720px) {
  .chart-panel {
    padding: 18px;
  }

  .chart-panel__header,
  .chart-controls {
    align-items: stretch;
    flex-direction: column;
  }

  .chart-title-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .chart-actions,
  .metric-tabs,
  .range-tabs {
    overflow-x: auto;
  }

  .action-button,
  .control-tab {
    flex: 1 0 auto;
  }

  .statistics-grid {
    grid-template-columns: 1fr;
  }

  .chart-wrapper {
    height: 320px;
  }
}
</style>
