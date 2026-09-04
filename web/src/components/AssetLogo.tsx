import { useState } from 'react'

type Props = {
  symbol: string
  name?: string | null
  logoUrl?: string | null
  size?: number
}

export function AssetLogo({ symbol, name, logoUrl, size = 28 }: Props) {
  const [broken, setBroken] = useState(false)
  if (logoUrl && !broken) {
    return (
      <img
        className="asset-logo"
        src={logoUrl}
        alt={name || symbol}
        width={size}
        height={size}
        loading="lazy"
        referrerPolicy="no-referrer"
        onError={() => setBroken(true)}
      />
    )
  }
  return (
    <span className="asset-logo fallback" style={{ width: size, height: size, fontSize: size * 0.38 }} aria-hidden>
      {symbol.slice(0, 2)}
    </span>
  )
}