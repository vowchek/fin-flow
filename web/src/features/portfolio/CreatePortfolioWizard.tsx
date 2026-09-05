import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import * as portfoliosApi from '../../api/portfolios'
import type { PortfolioKind } from '../../api/types'
import { Modal } from '../../components/Modal'
import { PortfolioModeIcon } from '../../components/PortfolioModeIcon'
import { LazySeedModal } from './LazySeedModal'

export type CreateMode = 'lazy' | 'manual' | 'broker-report' | 'broker-api'

type ModeOption = {
  id: CreateMode
  title: string
  description: string
  available: boolean
}

type Props = {
  open: boolean
  kind: PortfolioKind
  basePath: string
  onClose: () => void
  onCreated: () => void
}

function modesFor(kind: PortfolioKind): ModeOption[] {
  if (kind === 'stock') {
    return [
      {
        id: 'lazy',
        title: 'Ленивый ввод',
        description: 'Активы, средняя цена и дата первой покупки — как в брокере. Потом докупки и продажи вручную.',
        available: true,
      },
      {
        id: 'manual',
        title: 'Ручной учёт сделок',
        description: 'Каждая покупка и продажа отдельно: цена, количество и дата. Удобно для небольшого портфеля.',
        available: true,
      },
      {
        id: 'broker-report',
        title: 'Отчёт брокера',
        description: 'Загрузка отчёта из кабинета брокера.',
        available: false,
      },
      {
        id: 'broker-api',
        title: 'API брокера',
        description: 'Синхронизация сделок по API брокера.',
        available: false,
      },
    ]
  }
  return [
    {
      id: 'lazy',
      title: 'Ленивый ввод',
      description: 'Сумма вложений и количества активов — среднюю цену входа посчитаем сами. Дальше ручные операции.',
      available: true,
    },
    {
      id: 'manual',
      title: 'Ручной учёт сделок',
      description: 'Каждая покупка и продажа отдельно: цена, количество и дата.',
      available: true,
    },
  ]
}

export function CreatePortfolioWizard({ open, kind, basePath, onClose, onCreated }: Props) {
  const navigate = useNavigate()
  const [step, setStep] = useState<'details' | 'mode'>('details')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [mode, setMode] = useState<CreateMode | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [seedPortfolioId, setSeedPortfolioId] = useState<string | null>(null)

  const modes = modesFor(kind)

  function reset() {
    setStep('details')
    setName('')
    setDescription('')
    setMode(null)
    setError(null)
    setSaving(false)
  }

  function handleClose() {
    if (saving) return
    reset()
    onClose()
  }

  function onDetailsNext(e: FormEvent) {
    e.preventDefault()
    if (!name.trim()) {
      setError('Укажите название')
      return
    }
    setError(null)
    setStep('mode')
  }

  async function onCreate() {
    if (!mode || !modes.find((m) => m.id === mode)?.available) {
      setError('Выберите доступный режим')
      return
    }
    setSaving(true)
    setError(null)
    try {
      const created = await portfoliosApi.createPortfolio(
        kind,
        name.trim(),
        description.trim() || undefined,
        mode,
      )
      if (mode === 'lazy') {
        setSeedPortfolioId(created.id)
        reset()
        onClose()
        onCreated()
      } else {
        reset()
        onClose()
        onCreated()
        navigate(`${basePath}/${created.id}`)
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось создать')
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <Modal
        open={open && !seedPortfolioId}
        wide
        title={step === 'details' ? 'Новый портфель' : 'Как наполним портфель?'}
        onClose={handleClose}
      >
        {step === 'details' ? (
          <form className="stack" onSubmit={onDetailsNext}>
            <div className="field">
              <label htmlFor="pf-name">Название</label>
              <input
                id="pf-name"
                required
                autoFocus
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="pf-desc">Описание</label>
              <input id="pf-desc" value={description} onChange={(e) => setDescription(e.target.value)} />
            </div>
            {error ? <p className="error">{error}</p> : null}
            <div className="modal-actions">
              <button type="button" className="btn btn-ghost" onClick={handleClose}>
                Отмена
              </button>
              <button type="submit" className="btn">
                Далее
              </button>
            </div>
          </form>
        ) : (
          <div className="stack">
            <p className="muted" style={{ marginTop: 0 }}>
              Портфель «{name.trim()}». Выберите способ первого наполнения.
            </p>
            <div className="mode-grid">
              {modes.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={`mode-card${mode === item.id ? ' selected' : ''}${item.available ? '' : ' disabled'}`}
                  disabled={!item.available}
                  onClick={() => item.available && setMode(item.id)}
                >
                  <span className="mode-card-title">
                    <span className="mode-card-icon" aria-hidden>
                      <PortfolioModeIcon mode={item.id} size={20} />
                    </span>
                    {item.title}
                    {!item.available ? <span className="mode-badge">Скоро</span> : null}
                  </span>
                  <span className="mode-card-desc">{item.description}</span>
                </button>
              ))}
            </div>
            {error ? <p className="error">{error}</p> : null}
            <div className="modal-actions">
              <button type="button" className="btn btn-ghost" disabled={saving} onClick={() => setStep('details')}>
                Назад
              </button>
              <button type="button" className="btn" disabled={saving || !mode} onClick={() => void onCreate()}>
                {saving ? 'Создаём…' : mode === 'lazy' ? 'Создать и открыть ввод' : 'Создать портфель'}
              </button>
            </div>
          </div>
        )}
      </Modal>

      <LazySeedModal
        open={Boolean(seedPortfolioId)}
        kind={kind}
        portfolioId={seedPortfolioId ?? ''}
        onClose={() => {
          setSeedPortfolioId(null)
          onCreated()
        }}
        onDone={() => {
          const id = seedPortfolioId
          setSeedPortfolioId(null)
          onCreated()
          if (id) navigate(`${basePath}/${id}`)
        }}
      />
    </>
  )
}
