import { useState } from 'react'
import { formatInstant } from '@/lib/time'
import { Panel } from '@/components/ui/Panel'
import { EmptyState, QueryBoundary } from '@/components/ui/States'
import { POSITION_END_LABEL, roleLabel, roleRank } from './api'
import { APPROVAL_TYPE_LABEL } from './manage'
import { DECISION_ACTION_LABEL, useDecisionLog, usePositionHistory, type DecisionLogEntry, type PositionTerm } from './records'
import { PersonLink } from '@/features/people/PersonLink'

const PAGE = 40

/** Günlük satırının metni: "Görev değişikliği: onaylandı" ya da yalnız eylem ("Seçim açıldı"). */
function describe(e: DecisionLogEntry): string {
  const action = DECISION_ACTION_LABEL[e.action] ?? e.action
  const type = e.requestType ? APPROVAL_TYPE_LABEL[e.requestType] : null
  return type ? `${type}: ${action.toLocaleLowerCase('tr-TR')}` : action
}

/**
 * Karar günlüğü: kulüpte verilen her kararın değiştirilemez izi (gönderme, başkan onayı, onay, ret, seçim, devir…),
 * yeniden eskiye. Uzun günlük parça parça açılır.
 */
export function DecisionLog({ clubId }: { clubId: string }) {
  const log = useDecisionLog(clubId)
  const [shown, setShown] = useState(PAGE)
  return (
    <QueryBoundary query={log} what="Karar günlüğü" skeletonRows={5}>
      {(list) => {
        if (list.length === 0) return <EmptyState title="Günlük boş">Kulüpte karar verildikçe burada iz kalır.</EmptyState>
        const sorted = [...list].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        return (
          <div className="max-w-[60rem]">
            <ol className="border-t-2 border-ink">
              {sorted.slice(0, shown).map((e) => (
                <li key={e.id} className="grid gap-x-8 gap-y-0.5 border-b border-rule py-3 sm:grid-cols-[10rem_minmax(0,1fr)]">
                  <span className="tabular text-sm text-ink-3">{formatInstant(e.createdAt, 'datetime')}</span>
                  <span className="min-w-0">
                    <span className="font-semibold">{describe(e)}</span>
                    {(e.actorName || e.subjectUserName) && (
                      <span className="ml-2 text-sm text-ink-3">
                        {[e.actorName, e.subjectUserName && e.subjectUserName !== e.actorName ? `hakkında: ${e.subjectUserName}` : null]
                          .filter(Boolean)
                          .join(', ')}
                      </span>
                    )}
                    {e.detail && <span className="block text-md text-ink-2">{e.detail}</span>}
                  </span>
                </li>
              ))}
            </ol>
            {sorted.length > shown && (
              <button
                type="button"
                onClick={() => setShown((n) => n + PAGE)}
                className="mt-5 text-sm font-semibold underline-offset-4 hover:underline"
              >
                Daha eski kayıtlar ({sorted.length - shown})
              </button>
            )}
          </div>
        )
      }}
    </QueryBoundary>
  )
}

const name = (t: PositionTerm) => [t.firstName, t.lastName].filter(Boolean).join(' ') || 'Üye'

/** Görev geçmişi: şimdiki görevliler (göreve başlama tarihiyle) ve biten görevler (süre ve bitiş nedeni). */
export function PositionHistory({ clubId }: { clubId: string }) {
  const history = usePositionHistory(clubId)
  return (
    <QueryBoundary query={history} what="Görev geçmişi" skeletonRows={5}>
      {(list) => {
        const current = list.filter((t) => !t.endedAt).sort((a, b) => roleRank(a.position) - roleRank(b.position))
        const past = list.filter((t) => t.endedAt).sort((a, b) => (b.endedAt ?? '').localeCompare(a.endedAt ?? ''))
        if (list.length === 0) return <EmptyState title="Görev kaydı yok">Kulüpte görev verildikçe burada iz kalır.</EmptyState>
        return (
          <div className="grid gap-16 lg:grid-cols-12 lg:gap-10">
            <div className="lg:col-span-5">
              <Panel title="Şimdiki görevliler">
                {current.length === 0 ? (
                  <p className="text-ink-3">Şu anda görevli yok.</p>
                ) : (
                  <ul>
                    {current.map((t) => (
                      <li key={`${t.studentId}-${t.startedAt}`} className="border-b border-rule py-3 first:pt-0">
                        <PersonLink id={t.studentId} className="block font-semibold">
                          {name(t)}
                        </PersonLink>
                        <span className="block text-sm text-ink-3">
                          {t.positionName || roleLabel(t.position)}, {formatInstant(t.startedAt, 'date')} tarihinden beri
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </Panel>
            </div>
            <div className="min-w-0 lg:col-span-7">
              <Panel title="Biten görevler">
                {past.length === 0 ? (
                  <p className="text-ink-3">Henüz biten görev yok.</p>
                ) : (
                  <ul className="border-t border-rule">
                    {past.map((t) => (
                      <li
                        key={`${t.studentId}-${t.position}-${t.startedAt}`}
                        className="grid gap-x-6 gap-y-0.5 border-b border-rule py-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-baseline"
                      >
                        <span className="min-w-0">
                          <PersonLink id={t.studentId} className="font-semibold">
                            {name(t)}
                          </PersonLink>
                          <span className="ml-3 text-sm text-ink-3">{t.positionName || roleLabel(t.position)}</span>
                        </span>
                        <span className="tabular text-sm text-ink-2 sm:text-right">
                          {formatInstant(t.startedAt, 'date')} – {formatInstant(t.endedAt, 'date')}
                          {t.endReason && <span className="block text-ink-3">{POSITION_END_LABEL[t.endReason] ?? ''}</span>}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </Panel>
            </div>
          </div>
        )
      }}
    </QueryBoundary>
  )
}
