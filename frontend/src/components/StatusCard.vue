<!--Status Card 작성 -->

<script setup>
defineProps({
  label: {
    type: String,
    required: true,
  },
  value: {
    type: [String, Number],
    required: true,
  },
  unit: {
    type: String,
    default: '',
  },
  tone: {
    type: String,
    default: 'normal',
  },
  detail: {
    type: String,
    default: '',
  },
})
</script>

<template>
  <article class="status-card" :class="`status-card--${tone}`">
    <span class="status-card__label">{{ label }}</span>

    <strong class="status-card__value">
      {{ value }}
      <small v-if="unit">{{ unit }}</small>
    </strong>

    <span v-if="detail" class="status-card__detail">
      {{ detail }}
    </span>
  </article>
</template>

<style scoped>
.status-card {
  padding: 22px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: white;
  box-shadow: 0 8px 24px rgb(15 23 42 / 6%);
}

.status-card__label {
  color: #64748b;
  font-size: 14px;
}

.status-card__value {
  display: block;
  margin-top: 12px;
  color: #0f172a;
  font-size: 30px;
}

.status-card__value small {
  font-size: 16px;
  font-weight: 500;
}

.status-card__detail {
  display: block;
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.status-card--online {
  border-color: #86efac;
  background: #f0fdf4;
}

.status-card--offline {
  border-color: #fecaca;
  background: #fef2f2;
}

.status-card--warning {
  border-color: #fca5a5;
  background: #fff1f2;
}

.status-card--warning .status-card__value,
.status-card--warning .status-card__detail {
  color: #b91c1c;
}
</style>
