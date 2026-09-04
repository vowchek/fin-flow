import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'

import type { Holding } from '../api/types'

import './PortfolioChart.css'



const COLORS = ['#0F3D32', '#2F6B5A', '#6FA894', '#A3C4B8', '#C4A574', '#8B6B4A', '#4A6670', '#7A8B74']

const COLORS_DARK = ['#5fbfa3', '#8ad4bf', '#c4a574', '#7aa2c4', '#b18ad4', '#d48a8a', '#a3c46f', '#8a9ad4']



type Props = {

  holdings: Holding[]

  compact?: boolean

}



export function PortfolioChart({ holdings, compact }: Props) {

  const dark = document.documentElement.dataset.theme === 'dark'

  const palette = dark ? COLORS_DARK : COLORS

  const active = holdings.filter((h) => Number(h.quantity) > 0)

  const valued = active.filter((h) => h.marketValue != null && Number(h.marketValue) > 0)

  const useValue = valued.length > 0

  const source = useValue ? valued : active



  if (source.length === 0) {

    return <div className={`empty${compact ? ' compact' : ''}`}>Нет активов</div>

  }



  const total = source.reduce(

    (sum, h) => sum + Number(useValue ? h.marketValue : h.quantity),

    0,

  )

  const data = source.map((h) => {

    const value = Number(useValue ? h.marketValue : h.quantity)

    return {

      name: h.symbol,

      value,

      pct: total > 0 ? (value / total) * 100 : 0,

    }

  })



  const height = compact ? 180 : 260

  const inner = compact ? 42 : 58

  const outer = compact ? 72 : 92



  return (

    <div className={`chart-wrap${compact ? ' compact' : ''}`}>

      <ResponsiveContainer width="100%" height={height}>

        <PieChart>

          <Pie

            data={data}

            dataKey="value"

            nameKey="name"

            innerRadius={inner}

            outerRadius={outer}

            paddingAngle={2}

            stroke="none"

          >

            {data.map((item, i) => (

              <Cell key={item.name} fill={palette[i % palette.length]} />

            ))}

          </Pie>

          <Tooltip

            contentStyle={{

              background: 'var(--bg-elevated)',

              border: '1px solid var(--line)',

              borderRadius: 10,

              color: 'var(--ink)',

            }}

            formatter={(value, name) => {

              const num = Number(value ?? 0)

              const pct = total > 0 ? ((num / total) * 100).toFixed(1) : '0'

              return [`${num.toLocaleString('ru-RU')} (${pct}%)`, String(name)]

            }}

          />

        </PieChart>

      </ResponsiveContainer>

      <ul className="chart-legend">

        {data.map((item, i) => (

          <li key={item.name}>

            <span className="swatch" style={{ background: palette[i % palette.length] }} />

            <span>

              {item.name} · {item.pct.toFixed(1)}%

            </span>

          </li>

        ))}

      </ul>

    </div>

  )

}


