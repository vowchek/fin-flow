type Props = {
  page: number
  totalPages: number
  totalElements?: number
  onChange: (page: number) => void
}

export function Pagination({ page, totalPages, totalElements, onChange }: Props) {
  if (totalPages <= 0) return null
  const showControls = totalPages > 1
  const prev = () => onChange(Math.max(0, page - 1))
  const next = () => onChange(Math.min(totalPages - 1, page + 1))

  // компактный ряд страниц: первая, текущая ±1, последняя
  const pages = new Set<number>([0, totalPages - 1, page - 1, page, page + 1])
  const sorted = [...pages].filter((p) => p >= 0 && p < totalPages).sort((a, b) => a - b)

  return (
    <div
      className="row"
      style={{
        gap: '0.4rem',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginTop: '0.75rem',
        flexWrap: 'wrap',
      }}
    >
      <span style={{ minWidth: '1px', flex: '1 1 0' }} />
      {showControls ? (
        <div className="row" style={{ gap: '0.4rem', alignItems: 'center', justifyContent: 'center', flexWrap: 'wrap' }}>
          <button type="button" className="btn btn-ghost btn-sm" disabled={page <= 0} onClick={prev}>
            ← Назад
          </button>
          {sorted.map((p, i) => (
            <span key={p} className="row" style={{ gap: '0.4rem' }}>
              {i > 0 && sorted[i - 1] !== p - 1 ? <span className="muted">…</span> : null}
              <button
                type="button"
                className={`btn btn-sm${p === page ? '' : ' btn-ghost'}`}
                disabled={p === page}
                onClick={() => onChange(p)}
              >
                {p + 1}
              </button>
            </span>
          ))}
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={page >= totalPages - 1}
            onClick={next}
          >
            Вперёд →
          </button>
        </div>
      ) : (
        <span />
      )}
      <span
        className="muted"
        style={{ fontSize: '0.8rem', flex: '1 1 0', textAlign: 'right', whiteSpace: 'nowrap' }}
      >
        {totalElements != null
          ? `${totalElements} шт${showControls ? ` · стр. ${page + 1}/${totalPages}` : ''}`
          : ''}
      </span>
    </div>
  )
}
