import { formatDate } from './dates.js'

export const FREQUENCY_LABELS = {
  WEEKLY: 'Weekly',
  MONTHLY: 'Monthly',
  QUARTERLY: 'Every 3 months',
  YEARLY: 'Yearly',
}

// "Due today", "Due tomorrow", "Due in 5 days", "Expected 3 days ago"
export function dueLabel(daysUntilDue, nextDueOn) {
  if (daysUntilDue === 0) return 'Due today'
  if (daysUntilDue === 1) return 'Due tomorrow'
  if (daysUntilDue > 1 && daysUntilDue <= 14) return `Due in ${daysUntilDue} days`
  if (daysUntilDue < 0) return `Expected ${-daysUntilDue} ${daysUntilDue === -1 ? 'day' : 'days'} ago`
  return `Next on ${formatDate(nextDueOn)}`
}
