export function formatMoney(value: number | null | undefined, currency = 'RUB') {
  if (value == null || Number.isNaN(Number(value))) return '—'
  return new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency,
    maximumFractionDigits: Math.abs(Number(value)) < 1 ? 6 : 2,
  }).format(Number(value))
}

export function formatPct(value: number | null | undefined) {
  if (value == null || Number.isNaN(Number(value))) return '—'
  const n = Number(value)
  const sign = n > 0 ? '+' : ''
  return `${sign}${n.toFixed(2)}%`
}

export function changeClass(value: number | null | undefined) {
  if (value == null || Number.isNaN(Number(value)) || value === 0) return 'chg flat'
  return value > 0 ? 'chg up' : 'chg down'
}

