import { describe, expect, it } from 'vitest'
import { dueLabel } from './recurring.js'

describe('dueLabel', () => {
  it('describes how soon a payment is due', () => {
    expect(dueLabel(0, '2026-09-27')).toBe('Due today')
    expect(dueLabel(1, '2026-09-28')).toBe('Due tomorrow')
    expect(dueLabel(5, '2026-10-02')).toBe('Due in 5 days')
    expect(dueLabel(20, '2026-10-17')).toBe('Next on 17 Oct 2026')
  })

  it('says when an expected payment has not shown up yet', () => {
    expect(dueLabel(-1, '2026-09-26')).toBe('Expected 1 day ago')
    expect(dueLabel(-3, '2026-09-24')).toBe('Expected 3 days ago')
  })
})
