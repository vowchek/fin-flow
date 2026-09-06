import type { PortfolioKind } from '../api/types'

type NavKind = PortfolioKind | 'expense'

type Props = {
  kind: NavKind
  size?: number
  className?: string
  title?: string
}

export function PortfolioKindIcon({ kind, size = 18, className, title }: Props) {
  const label =
    title ?? (kind === 'crypto' ? 'Криптопортфель' : kind === 'expense' ? 'Учёт трат' : 'Фондовый портфель')

  if (kind === 'stock') {
    return (
      <svg className={className} width={size} height={size} viewBox="0 0 24 24" fill="none"
        aria-hidden={title ? undefined : true} role={title ? 'img' : undefined}>
        {title ? <title>{label}</title> : null}
        <polyline points="3 17 9 11 13 15 21 7" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
        <polyline points="15 7 21 7 21 13" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    )
  }

  if (kind === 'crypto') {
    return (
      <svg className={className} width={size} height={size} viewBox="0 0 24 24" fill="none"
        aria-hidden={title ? undefined : true} role={title ? 'img' : undefined}>
        {title ? <title>{label}</title> : null}
        <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.7" />
        <path d="M14.5 9.5c-.5-1-1.5-1.5-3-1.5-2.2 0-4 1.3-4 3s1.8 3 4 3c1.5 0 2.5-.5 3-1.5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M12 6.5v1.5M12 16v1.5M10 9.5h4M10 14.5h4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      </svg>
    )
  }

  // expense — receipt
  return (
    <svg className={className} width={size} height={size} viewBox="0 0 24 24" fill="none"
      aria-hidden={title ? undefined : true} role={title ? 'img' : undefined}>
      {title ? <title>{label}</title> : null}
      <path d="M4 2v20l3-2 3 2 3-2 3 2 3-2 3 2V2l-3 2-3-2-3 2-3-2-3 2-3-2z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
      <path d="M9 10h6M9 14h4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  )
}
