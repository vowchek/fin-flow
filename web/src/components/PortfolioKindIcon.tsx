import type { PortfolioKind } from '../api/types'

type Props = {
  kind: PortfolioKind
  size?: number
  className?: string
  title?: string
}

/** Stock: candlestick chart. Crypto: hexagon coin. */
export function PortfolioKindIcon({ kind, size = 18, className, title }: Props) {
  const label = title ?? (kind === 'crypto' ? 'Криптопортфель' : 'Фондовый портфель')
  if (kind === 'crypto') {
    return (
      <svg
        className={className}
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden={title ? undefined : true}
        role={title ? 'img' : undefined}
      >
        {title ? <title>{label}</title> : null}
        <path
          d="M12 3.2l7 4v9.6l-7 4-7-4V7.2l7-4z"
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinejoin="round"
        />
        <path
          d="M12 8.2v7.6M9.6 10.2h3.2a1.6 1.6 0 010 3.2H9.6"
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    )
  }
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden={title ? undefined : true}
      role={title ? 'img' : undefined}
    >
      {title ? <title>{label}</title> : null}
      <path d="M4 19h16" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      <path d="M7 16V11" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      <path d="M12 16V7" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      <path d="M17 16v-3" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      <path
        d="M7 9.5V8M12 5.5V4M17 11.5V10"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
    </svg>
  )
}
