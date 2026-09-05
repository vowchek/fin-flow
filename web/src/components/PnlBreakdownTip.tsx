import { useEffect, useId, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { changeClass, formatMoney } from '../lib/format'

type Props = {
  price: number | null | undefined
  income: number | null | undefined
  /** Remainder: commissions, sells vs invested, cash lag, etc. */
  other?: number | null | undefined
  total: number | null | undefined
  currency: string
  children: ReactNode
  className?: string
}

export function PnlBreakdownTip({ price, income, other, total, currency, children, className }: Props) {
  const tipId = useId()
  const [anchor, setAnchor] = useState<{ left: number; top: number } | null>(null)
  const showOther = other != null

  function open(el: HTMLElement) {
    const r = el.getBoundingClientRect()
    setAnchor({ left: r.left, top: r.top - 8 })
  }

  function close() {
    setAnchor(null)
  }

  useEffect(() => {
    if (!anchor) return
    const onScroll = () => setAnchor(null)
    window.addEventListener('scroll', onScroll, true)
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll, true)
      window.removeEventListener('resize', onScroll)
    }
  }, [anchor])

  return (
    <span
      className={`pnl-tip${className ? ` ${className}` : ''}`}
      tabIndex={0}
      aria-describedby={anchor ? tipId : undefined}
      onMouseEnter={(e) => open(e.currentTarget)}
      onFocus={(e) => open(e.currentTarget)}
      onMouseLeave={close}
      onBlur={close}
    >
      <span className="pnl-tip-value">{children}</span>
      {anchor
        ? createPortal(
            <span
              id={tipId}
              className="pnl-tip-box pnl-tip-box--portal"
              role="tooltip"
              style={{ left: anchor.left, top: anchor.top }}
            >
              <div>
                Разница цены:{' '}
                <span className={changeClass(price)}>{formatMoney(price, currency)}</span>
              </div>
              <div>
                Дивиденды/купоны:{' '}
                <span className={changeClass(income)}>{formatMoney(income ?? 0, currency)}</span>
              </div>
              {showOther ? (
                <div>
                  Комиссии и прочее:{' '}
                  <span className={changeClass(other)}>{formatMoney(other, currency)}</span>
                </div>
              ) : null}
              <div className="pnl-tip-total">
                Всего: <span className={changeClass(total)}>{formatMoney(total, currency)}</span>
              </div>
            </span>,
            document.body,
          )
        : null}
    </span>
  )
}
