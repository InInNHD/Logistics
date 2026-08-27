const pad = (value: number) => String(value).padStart(2, '0')

/** Element Plus datetime value using the browser's local timezone, not UTC. */
export function toLocalDateTimeValue(date = new Date()): string {
  return [
    date.getFullYear(),
    '-',
    pad(date.getMonth() + 1),
    '-',
    pad(date.getDate()),
    'T',
    pad(date.getHours()),
    ':',
    pad(date.getMinutes()),
    ':',
    pad(date.getSeconds()),
  ].join('')
}

export function recentDayLabels(count: number, now = new Date()): string[] {
  const formatter = new Intl.DateTimeFormat('zh-CN', { weekday: 'short' })
  return Array.from({ length: Math.max(count, 0) }, (_, index) => {
    const offset = count - index - 1
    if (offset === 0) return '今天'
    const date = new Date(now)
    date.setDate(now.getDate() - offset)
    return formatter.format(date).replace('星期', '周')
  })
}
