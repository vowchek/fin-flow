import { useLayoutEffect, useMemo, useRef, useState } from 'react'

type Props = {
  symbols: string[]
}

export function AssetTickerStrip({ symbols }: Props) {
  const ref = useRef<HTMLDivElement>(null)
  const key = useMemo(() => symbols.join('|'), [symbols])
  const [visible, setVisible] = useState(symbols.length)

  useLayoutEffect(() => {
    const box = ref.current
    if (!box || symbols.length === 0) {
      setVisible(symbols.length)
      return
    }

    function measure() {
      if (!box) return
      const tickers = Array.from(box.querySelectorAll<HTMLElement>('[data-ticker]'))
      const more = box.querySelector<HTMLElement>('[data-more]')
      if (tickers.length === 0) {
        setVisible(0)
        return
      }

      for (const el of tickers) el.hidden = false
      if (more) more.hidden = true

      const max = box.clientWidth
      let fit = tickers.length
      for (let i = 0; i < tickers.length; i++) {
        const right = tickers[i].offsetLeft + tickers[i].offsetWidth
        if (right > max + 0.5) {
          fit = i
          break
        }
      }

      if (fit < tickers.length && fit >= 0) {
        if (more) more.hidden = false
        const moreWidth = more?.offsetWidth ?? 28
        const gap = 10
        while (fit > 0) {
          const right = tickers[fit - 1].offsetLeft + tickers[fit - 1].offsetWidth
          if (right + gap + moreWidth <= max + 0.5) break
          fit -= 1
        }
        if (more) more.hidden = true
      }

      setVisible((prev) => (prev === fit ? prev : fit))
    }

    measure()
    const ro = new ResizeObserver(() => measure())
    ro.observe(box)
    return () => ro.disconnect()
  }, [key, symbols.length])

  if (symbols.length === 0) {
    return (
      <div className="pf-card-assets">
        <span className="muted">Нет позиций</span>
      </div>
    )
  }

  const hiddenCount = Math.max(0, symbols.length - visible)

  return (
    <div ref={ref} className="pf-card-assets">
      {symbols.map((symbol, index) => (
        <span key={`${symbol}-${index}`} className="pf-ticker" data-ticker hidden={index >= visible}>
          {symbol}
        </span>
      ))}
      <span className="pf-ticker more" data-more hidden={hiddenCount <= 0}>
        +{hiddenCount}
      </span>
    </div>
  )
}
