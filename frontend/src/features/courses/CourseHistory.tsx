import { useState } from 'react'
import { Link } from 'react-router-dom'
import { formatInstant } from '@/lib/time'
import { Badge } from '@/components/ui/Badge'
import { Panel } from '@/components/ui/Panel'
import { Skeleton } from '@/components/ui/States'
import { MY_ENROLLMENT_LABEL, useMyEnrollmentHistory } from './api'

const SHOWN = 8

/** Ders geçmişim (F-44): kayıt, çekilme ve çıkarılma izi; kayıt döneminden sonraki çekilme "W" olarak işaretlenir. */
export function CourseHistory() {
  const history = useMyEnrollmentHistory()
  const [all, setAll] = useState(false)
  const list = history.data ?? []
  const shown = all ? list : list.slice(0, SHOWN)

  return (
    <Panel title="Ders geçmişim">
      {history.isPending ? (
        <Skeleton rows={3} />
      ) : list.length === 0 ? (
        <p className="text-ink-3">{history.isError ? 'Ders geçmişi şu an yüklenemiyor.' : 'Henüz bir derse kaydınız olmadı.'}</p>
      ) : (
        <>
          <ul className="border-t border-rule">
            {shown.map((e) => (
              <li key={e.id} className="grid gap-x-6 gap-y-1 border-b border-rule py-3.5 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-baseline">
                <span className="min-w-0">
                  <Link to={`/courses/${e.courseId}`} className="font-semibold underline-offset-4 hover:underline">
                    {[e.courseCode, e.courseTitle].filter(Boolean).join(' ') || 'Ders'}
                  </Link>
                  {e.termLabel && <span className="ml-3 text-sm text-ink-3">{e.termLabel}</span>}
                  {e.reason && <span className="mt-0.5 block text-md text-ink-2">Gerekçe: {e.reason}</span>}
                </span>
                <span className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ink-2 sm:justify-end">
                  {e.afterEnrollmentPeriod && <Badge tone="warning">Geç çekilme (W)</Badge>}
                  <span>
                    {MY_ENROLLMENT_LABEL[e.type]}, <span className="tabular">{formatInstant(e.occurredAt, 'date')}</span>
                  </span>
                </span>
              </li>
            ))}
          </ul>
          {list.length > SHOWN && (
            <button type="button" onClick={() => setAll((a) => !a)} className="mt-3 text-sm font-semibold underline-offset-4 hover:underline">
              {all ? 'Daha az göster' : `Tümünü göster (${list.length})`}
            </button>
          )}
        </>
      )}
    </Panel>
  )
}
