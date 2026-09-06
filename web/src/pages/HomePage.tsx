import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useAuthModal } from '../auth/AuthModalContext'
import { Footer } from '../layout/Footer'
import './HomePage.css'

const slides = [
  {
    title: 'Все финансы в одном месте',
    desc: 'Фондовые и криптопортфели рядом с месячными тратами — видите картину целиком, а не собираете её по таблицам.',
    icon: '📊',
  },
  {
    title: 'Следите за прибылью',
    desc: 'Текущая оценка, вложенная сумма и изменение — по портфелю целиком и по каждой позиции.',
    icon: '📈',
  },
  {
    title: 'Учёт расходов',
    desc: 'Фиксируйте траты по месяцам и категориям, чтобы видеть, куда уходит бюджет.',
    icon: '💡',
  },
  {
    title: 'Пассивный доход',
    desc: 'Календарь выплат, прогнозы дивидендов и реальная доходность портфеля.',
    icon: '💰',
  },
]

export function HomePage() {
  const { user } = useAuth()
  const { openLogin } = useAuthModal()
  const [current, setCurrent] = useState(0)

  useEffect(() => {
    const timer = setInterval(() => {
      setCurrent((c) => (c + 1) % slides.length)
    }, 5000)
    return () => clearInterval(timer)
  }, [])

  return (
    <div className="home-landing-root">
      <div className="page home-landing">
        <section className="landing-hero">
          <h1 className="landing-title">
            <span className="landing-brand">Fin Flow</span>
            <span className="landing-accent">.</span>
          </h1>
          <p className="landing-subtitle">
            Личное пространство для капитала и быта. Всё в одном сервисе.
          </p>

          <div className="landing-slideshow" aria-live="polite">
            <div className="slide-card">
              <span className="slide-icon" aria-hidden>
                {slides[current].icon}
              </span>
              <h2 className="slide-title">{slides[current].title}</h2>
              <p className="slide-desc">{slides[current].desc}</p>
            </div>
            <div className="slide-dots">
              {slides.map((_, i) => (
                <button
                  key={i}
                  type="button"
                  className={`slide-dot ${i === current ? 'active' : ''}`}
                  aria-label={`Слайд ${i + 1}`}
                  onClick={() => setCurrent(i)}
                />
              ))}
            </div>
          </div>

          {user ? (
            <Link to="/stocks" className="btn landing-btn-primary">
              Приступить
            </Link>
          ) : (
            <button type="button" className="btn landing-btn-primary" onClick={openLogin}>
              Приступить
            </button>
          )}
        </section>

        <section className="landing-features" aria-label="Возможности">
          <div className="feature-grid">
            <div className="feature-card">
              <div className="feature-icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden>
                  <rect x="3" y="3" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.7" />
                  <rect x="14" y="3" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.7" />
                  <rect x="3" y="14" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.7" />
                  <rect x="14" y="14" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.7" />
                </svg>
              </div>
              <h3>Портфели</h3>
              <p>Фондовые и крипто — создавайте, добавляйте позиции, отслеживайте изменения.</p>
            </div>
            <div className="feature-card">
              <div className="feature-icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden>
                  <polyline points="22,6 13.5,15.5 8.5,10.5 2,17" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
                  <polyline points="16,6 22,6 22,12" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </div>
              <h3>Аналитика</h3>
              <p>Стоимость, прибыль, дивиденды — всё наглядно в графиках и цифрах.</p>
            </div>
            <div className="feature-card">
              <div className="feature-icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden>
                  <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
                  <rect x="9" y="3" width="6" height="4" rx="1" stroke="currentColor" strokeWidth="1.7" />
                </svg>
              </div>
              <h3>Расходы</h3>
              <p>Учёт трат по месяцам и категориям — бюджет под контролем.</p>
            </div>
            <div className="feature-card">
              <div className="feature-icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden>
                  <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.7" />
                  <path d="M12 7v5l3.5 2" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </div>
              <h3>Пассивный доход</h3>
              <p>Календарь выплат и прогноз по каждому инструменту.</p>
            </div>
          </div>
        </section>
      </div>
      <Footer />
    </div>
  )
}
