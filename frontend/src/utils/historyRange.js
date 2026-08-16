export const CURRENT_VIEW_TOLERANCE_MS = 10_000

export function rangeStartFor(rangeEnd, durationMs) {
  return rangeEnd - durationMs
}

export function moveRangeEnd(rangeEnd, durationMs, direction, now) {
  if (direction === 'previous') {
    return { rangeEnd: rangeEnd - durationMs, isCurrentView: false }
  }

  const nextEnd = rangeEnd + durationMs
  if (nextEnd >= now - CURRENT_VIEW_TOLERANCE_MS) {
    return { rangeEnd: now, isCurrentView: true }
  }

  return { rangeEnd: nextEnd, isCurrentView: false }
}

export function toKoreanLocalIso(timestamp) {
  const date = new Date(timestamp)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, -1)
}
