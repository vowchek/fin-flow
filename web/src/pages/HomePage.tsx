import { Link } from 'react-router-dom'
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
        <div className="row home-cta">
          <Link className="btn" to="/stocks">
            Фондовые портфели
          </Link>
          <Link className="btn btn-ghost" to="/crypto">
            Крипта
          </Link>
          <Link className="btn btn-ghost" to="/expenses">
            Траты
          </Link>
        </div>
      </section>

      <section className="home-points" aria-label="Возможности">
        <article className="home-point">
          <h2>Фонд</h2>
          <p>Собирайте портфели акций и ETF, следите за составом и обновляйте позиции без лишних шагов.</p>
        </article>
        <article className="home-point">
          <h2>Крипта</h2>
          <p>Отдельный контур под цифровые активы — та же ясность, своя специфика и дальнейший рост.</p>
        </article>
        <article className="home-point">
          <h2>Траты</h2>
          <p>Фиксируйте расходы по месяцам и категориям, чтобы динамика была видна, а не терялась в памяти.</p>
        </article>
      </section>
    </div>
  )
}
