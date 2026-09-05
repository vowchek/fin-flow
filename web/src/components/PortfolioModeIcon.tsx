import type { PortfolioEntryMode } from '../api/types'

type Props = {
  mode: PortfolioEntryMode | string
  size?: number
  className?: string
}

function normalize(mode: string): PortfolioEntryMode {
  const m = mode.toUpperCase().replace(/-/g, '_')
  if (m === 'LAZY' || m === 'MANUAL' || m === 'BROKER_REPORT' || m === 'BROKER_API') return m
  return 'MANUAL'
}

export function portfolioModeLabel(mode: PortfolioEntryMode | string) {
  switch (normalize(mode)) {
    case 'LAZY':
      return 'Ленивый ввод'
    case 'BROKER_REPORT':
      return 'Отчёт брокера'
    case 'BROKER_API':
      return 'API брокера'
    default:
      return 'Ручной учёт'
  }
}

/** Icons for portfolio entry subtype: lazy / manual / broker. */
export function PortfolioModeIcon({ mode, size = 18, className }: Props) {
  const m = normalize(mode)
  if (m === 'LAZY') {
    return (
      <svg className={className} width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden>
        <path
          d="M4 14.5c2.2-3.2 4.3-4.8 8-4.8s5.8 1.6 8 4.8"
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinecap="round"
        />
        <circle cx="12" cy="9.5" r="2.2" stroke="currentColor" strokeWidth="1.7" />
        <path d="M8 18h8" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      </svg>
    )
  }
  if (m === 'BROKER_REPORT' || m === 'BROKER_API') {
    return (
      <svg className={className} width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden>
        <path
          d="M7 4h7l4 4v12a1 1 0 01-1 1H7a1 1 0 01-1-1V5a1 1 0 011-1z"
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinejoin="round"
        />
        <path d="M14 4v4h4M9 13h6M9 16h4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      </svg>
    )
  }
  return (
    <svg className={className} width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden>
      <path
        d="M5 7h14M5 12h14M5 17h10"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
      <circle cx="17.5" cy="17" r="2.2" stroke="currentColor" strokeWidth="1.7" />
    </svg>
  )
}
