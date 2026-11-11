import { cn } from '@/lib/cn'
import { formatNumber } from '@/lib/format'
import { formatLocal } from '@/lib/time'
import type { MyAssignment } from './api'
import type { Phase } from './model'

type Segment = { phase: Phase; label: string; detail: string; end?: { label: string; at: string } }

function segments(a: MyAssignment): Segment[] {
  const extended = a.effectiveDueDate !== a.dueDate
  const list: Segment[] = [
    {
      phase: 'open',
      label: 'Teslim açık',
      detail: extended ? `Size özel uzatıldı; ilk son tarih ${formatLocal(a.dueDate, 'datetime')}.` : 'Teslimler zamanında sayılır.',
      end: { label: 'Son tarih', at: a.effectiveDueDate },
    },
  ]
  if (a.effectiveLateUntil) {
    list.push({
      phase: 'late',
      label: 'Geç teslim',
      detail: a.latePenaltyPercent > 0 ? `Puandan %${formatNumber(a.latePenaltyPercent)} kesilir.` : 'Kesinti uygulanmaz.',
      end: { label: 'Geç teslim bitişi', at: a.effectiveLateUntil },
    })
  }
  list.push({ phase: 'closed', label: 'Kapalı', detail: 'Teslim alınmaz.' })
  return list
}

/**
 * Teslim takvimi: hat üzerindeki duraklar gibi açık, geç teslim ve kapalı dönemler.
 * Şu anki dönem kalın hat rengiyle, geçenler ince koyu, gelecek olanlar ince açık çizgiyle; durak saatleri sınırların altında.
 */
export function DeadlineTrack({ assignment: a, phase, className }: { assignment: MyAssignment; phase: Phase; className?: string }) {
  const list = segments(a)
  const current = list.findIndex((s) => s.phase === phase)
  return (
    <ol
      aria-label="Teslim takvimi"
      className={cn('grid gap-y-8', list.length === 3 ? 'sm:grid-cols-[2fr_2fr_1fr]' : 'sm:grid-cols-[3fr_1fr]', className)}
    >
      {list.map((s, i) => {
        const state = i < current ? 'past' : i === current ? 'current' : 'next'
        return (
          <li key={s.phase} aria-current={state === 'current' ? 'step' : undefined} className="relative min-w-0 pt-6 sm:pr-6">
            <span
              aria-hidden
              className={cn(
                'absolute inset-x-0 top-0',
                state === 'current' ? 'h-[5px] bg-ders' : state === 'past' ? 'top-[2px] h-px bg-ink' : 'top-[2px] h-px bg-rule-strong',
              )}
            />
            {s.end && <span aria-hidden className="absolute -top-2 right-0 hidden h-[21px] w-[3px] bg-ink sm:block" />}
            <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-3">
              <div className="min-w-0">
                <p className="flex items-baseline gap-3">
                  <span className={cn('text-lg', state === 'current' ? 'font-heavy text-ink' : 'font-semibold text-ink-2')}>{s.label}</span>
                  {state === 'current' && <span className="text-sm font-semibold text-ders">Şu an</span>}
                </p>
                <p className="mt-0.5 text-sm text-ink-3">{s.detail}</p>
              </div>
              {s.end && (
                <p className="shrink-0 sm:text-right">
                  <span className="block text-sm text-ink-3">{s.end.label}</span>
                  <span className="tabular block text-md font-semibold">
                    {formatLocal(s.end.at, 'short')}, {formatLocal(s.end.at, 'time')}
                  </span>
                </p>
              )}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
