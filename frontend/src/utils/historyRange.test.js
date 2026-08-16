import { describe, expect, it } from 'vitest'

import {
  moveRangeEnd,
  rangeStartFor,
  toKoreanLocalIso,
} from './historyRange'

describe('history range helpers', () => {
  const hour = 60 * 60 * 1000
  const now = new Date('2026-08-16T12:00:00+09:00').getTime()

  it('moves a range backward by its full duration', () => {
    expect(moveRangeEnd(now, hour, 'previous', now)).toEqual({
      rangeEnd: now - hour,
      isCurrentView: false,
    })
  })

  it('returns to the current view instead of moving beyond now', () => {
    expect(moveRangeEnd(now - hour, hour, 'next', now)).toEqual({
      rangeEnd: now,
      isCurrentView: true,
    })
  })

  it('calculates a matching start time and API-safe local ISO value', () => {
    const rangeStart = rangeStartFor(now, hour)
    expect(rangeStart).toBe(now - hour)
    expect(toKoreanLocalIso(rangeStart)).not.toMatch(/Z$/)
  })
})
