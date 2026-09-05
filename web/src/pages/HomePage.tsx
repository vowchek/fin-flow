import { useAuth } from '../auth/AuthContext'
import './HomePage.css'

export function HomePage() {
  const { user } = useAuth()
  const name = user?.displayName?.trim() || user?.email

  return (
    <div className="page home">
      <section className="home-hero">
        <p className="home-hello">Привет{name ? `, ${name}` : ''}</p>
        <h1 className="home-brand">Fin Flow</h1>
        <p className="home-tagline">
          Личное пространство для капитала и быта: фондовые и криптопортфели рядом с месячными тратами —
          чтобы видеть картину целиком, а не собирать её по таблицам.
        </p>
      </section>

      <section className="home-overview" aria-label="Обзор">
        <h2 className="home-section-title">Как это устроено</h2>
        <p className="home-overview-text">
          Разделы в шапке ведут к портфелям и учёту расходов. На главной — только общая картина: что хранится
          в сервисе и какие действия обычно делают после входа.
        </p>
        <ul className="home-actions">
          <li>
            <strong>Портфели</strong>
            <span>Создают один или несколько портфелей, добавляют позиции и обновляют количество после покупок и продаж.</span>
          </li>
          <li>
            <strong>Стоимость и прибыль</strong>
            <span>Смотрят текущую оценку, вложенную сумму и изменение — по портфелю целиком и по каждой позиции.</span>
          </li>
          <li>
            <strong>Траты</strong>
            <span>Фиксируют расходы по месяцам и категориям, чтобы видеть, куда уходит бюджет рядом с капиталом.</span>
          </li>
        </ul>
      </section>
    </div>
  )
}
