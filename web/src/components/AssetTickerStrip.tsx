import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

type Props = {
  symbols: string[]
}

export function AssetTickerStrip({ symbols }: Props) {
  const ref = useRef<HTMLDivElement>(null)
  const tipId = useId()
  const key = useMemo(() => symbols.join('|'), [symbols])
  const [visible, setVisible] = useState(symbols.length)
  const [tip, setTip] = useState<{ left: number; top: number } | null>(null)

  useLayoutEffect(() => {
    const box = ref.current
    if (!box || symbols.length === 0) {
      setVisible(symbols.length)
      return
    }

    const card = box.closest('.pf-card') as HTMLElement | null

    function measure() {
      if (!box) return
      const tickers = Array.from(box.querySelectorAll<HTMLElement>('[data-ticker]'))
      const more = box.querySelector<HTMLElement>('[data-more]')
      if (tickers.length === 0) {
        setVisible(0)
        return
      }

      // Reveal all items for width sampling (ignore React hidden for a moment).
      for (const el of tickers) {
        el.hidden = false
        el.style.display = ''
      }
      if (more) {
        more.hidden = false
        more.style.display = ''
        more.textContent = `+${tickers.length}`
      }

      const gap = parseFloat(getComputedStyle(box).columnGap || getComputedStyle(box).gap) || 0
      const boxPad =
        parseFloat(getComputedStyle(box).paddingLeft) + parseFloat(getComputedStyle(box).paddingRight)
      // Prefer card width — the strip can grow with content and lie about its own clientWidth.
      const outer = card?.clientWidth ?? box.clientWidth
      const available = Math.max(0, outer - boxPad)

      const widths = tickers.map((el) => el.offsetWidth)
      const moreWidth = more?.offsetWidth ?? 36

      if (more) more.hidden = true

      let used = 0
      let fit = 0
      for (let i = 0; i < tickers.length; i++) {
        const w = widths[i] || 0
        const withTicker = used + (fit > 0 ? gap : 0) + w
        const remaining = tickers.length - i - 1
        if (remaining > 0) {
          if (withTicker + gap + moreWidth > available + 0.5) break
        } else if (withTicker > available + 0.5) {
          break
        }
        used = withTicker
        fit = i + 1
      }

      // Hide overflow in DOM until React commits (avoids a flash of wrapped chips).
      for (let i = 0; i < tickers.length; i++) {
        tickers[i].hidden = i >= fit
      }
      if (more) {
        const left = tickers.length - fit
        more.hidden = left <= 0
        if (left > 0) more.textContent = `+${left}`
      }

      setVisible((prev) => (prev === fit ? prev : fit))
    }

    measure()
    const ro = new ResizeObserver(() => measure())
    ro.observe(box)
    if (card) ro.observe(card)
    return () => ro.disconnect()
  }, [key, symbols.length])

  useEffect(() => {
    if (!tip) return
    const close = () => setTip(null)
    window.addEventListener('scroll', close, true)
    window.addEventListener('resize', close)
    return () => {
      window.removeEventListener('scroll', close, true)
      window.removeEventListener('resize', close)
    }
  }, [tip])

  if (symbols.length === 0) {
    return (
      <div className="pf-card-assets">
        <span className="muted">Нет позиций</span>
      </div>
    )
  }

  const hiddenCount = Math.max(0, symbols.length - visible)
  const hiddenSymbols = symbols.slice(visible)

  function openTip(el: HTMLElement) {
    const r = el.getBoundingClientRect()
    setTip({ left: r.left + r.width / 2, top: r.top - 8 })
  }

  return (
    <div ref={ref} className="pf-card-assets">
      {symbols.map((symbol, index) => (
        <span key={`${symbol}-${index}`} className="pf-ticker" data-ticker hidden={index >= visible}>
          {symbol}
        </span>
      ))}
      <span
        className="pf-ticker more"
        data-more
        hidden={hiddenCount <= 0}
        tabIndex={hiddenCount > 0 ? 0 : -1}
        aria-label={`Ещё ${hiddenCount}: ${hiddenSymbols.join(', ')}`}
        aria-describedby={tip ? tipId : undefined}
        onMouseEnter={(e) => openTip(e.currentTarget)}
        onFocus={(e) => openTip(e.currentTarget)}
        onMouseLeave={() => setTip(null)}
        onBlur={() => setTip(null)}
      >
        +{hiddenCount}
      </span>
      {tip && hiddenCount > 0
        ? createPortal(
            <span
              id={tipId}
              className="pf-ticker-tip"
              role="tooltip"
              style={{ left: tip.left, top: tip.top }}
            >
              {hiddenSymbols.join(', ')}
            </span>,
            document.body,
          )
        : null}
    </div>
  )
}
