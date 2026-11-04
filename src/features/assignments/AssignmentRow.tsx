import { Link } from 'react-router-dom'
import { formatNumber } from '@/lib/format'
import { formatLocal } from '@/lib/time'
import { Badge } from '@/components/ui/Badge'
import { TYPE_LABEL, type MyAssignment } from './api'
import { viewAssignment } from './model'

const DAY = new Intl.DateTimeFormat('tr-TR', { day: 'numeric', timeZone: 'UTC' })
const MONTH = new Intl.DateTimeFormat('tr-TR', { month: 'short', timeZone: 'UTC' })

const ASSIGNMENT_ROW = 'grid grid-cols-[3.5rem_minmax(0,1fr)] gap-x-5 gap-y-1.5 sm:grid-cols-[4rem_6.5rem_minmax(0,1fr)_12rem]'

/**
 * Ödev satırı (tarife): solda dev gün rakamı ve ay, hat çizgisiyle ders kodu, başlık ve tür, sağda durum ya da puan.
 * Bağlantı bildirimlerin kullandığı biçimde: /courses/{courseId}/assignments/{id} (F-76).
 */
export function AssignmentRow({
  assignment: a,
  courseCode,
  showCourse = true,
  now,
}: {
  assignment: MyAssignment
  courseCode?: string
  showCourse?: boolean
  now?: string
}) {
  const v = viewAssignment(a, now)
  const at = new Date(`${v.actionAt.slice(0, 10)}T12:00:00Z`)
  return (
    <li className="border-b border-rule">
      <Link to={`/courses/${a.courseId}/assignments/${a.id}`} className={`${ASSIGNMENT_ROW} row-fill py-5`}>
        <span className="row-span-2 sm:row-span-1">
          <span className="tabular block text-3xl leading-none font-heavy">{DAY.format(at)}</span>
          <span className="mt-1 block text-xs text-ink-3">
            {MONTH.format(at)}, {formatLocal(v.actionAt, 'time')}
          </span>
        </span>
        <span className="hidden items-center gap-2 pt-1 sm:flex">
          {showCourse && (
            <>
              <span aria-hidden className="h-4 w-[3px] bg-ders" />
              <span className="tabular truncate font-heavy">{courseCode ?? 'Ders'}</span>
            </>
          )}
        </span>
        <span className="min-w-0">
          <span className="block text-lg font-semibold">{a.title}</span>
          <span className="mt-0.5 flex flex-wrap gap-x-4 text-sm text-ink-3">
            {showCourse && <span className="tabular font-semibold text-ders sm:hidden">{courseCode ?? 'Ders'}</span>}
            <span>{TYPE_LABEL[a.type]}</span>
            {a.weight > 0 && <span>ağırlık %{formatNumber(a.weight)}</span>}
            {a.groupName && <span>grup {a.groupName}</span>}
            {v.needsGroup && <span className="font-semibold text-warning">grup seçilmedi</span>}
          </span>
        </span>
        <span className="col-start-2 flex flex-wrap items-center gap-x-3 gap-y-1 sm:col-start-auto sm:flex-col sm:items-end sm:text-right">
          {v.gradeText ? (
            <span className="tabular text-2xl leading-none font-heavy">{v.gradeText}</span>
          ) : (
            <Badge tone={v.status.tone}>{v.status.label}</Badge>
          )}
          {v.gradeText ? <span className="text-sm text-ink-3">{v.status.label}</span> : v.timing && <span className="tabular text-sm text-ink-3">{v.timing}</span>}
        </span>
      </Link>
    </li>
  )
}
