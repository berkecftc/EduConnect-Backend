import { Link } from 'react-router-dom'
import { formatLocalRange } from '@/lib/time'
import { Panel } from '@/components/ui/Panel'
import { useMyStaffEvents } from './staff'

/** Biletlerim'in üstünde: görevli olduğunuz yaklaşan etkinlikler ve kapı ekranına giriş. Görev yoksa hiç görünmez. */
export function StaffDuties() {
  const mine = useMyStaffEvents()
  const list = [...(mine.data ?? [])].sort((a, b) => a.startsAt.localeCompare(b.startsAt))
  if (list.length === 0) return null
  return (
    <Panel title="Görevli olduğunuz etkinlikler" className="mt-8 mb-16">
      <ul className="border-t border-rule">
        {list.map((e) => (
          <li key={e.id} className="grid gap-x-6 gap-y-2 border-b border-rule py-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
            <span className="min-w-0 border-l-[3px] border-etkinlik pl-4">
              <Link to={`/events/${e.id}`} className="block text-lg font-heavy underline-offset-4 hover:underline">
                {e.title}
              </Link>
              <span className="tabular block text-sm text-ink-3">
                {formatLocalRange(e.startsAt, e.endsAt)}
                {e.location ? `, ${e.location}` : ''}
                {e.clubName ? `. ${e.clubName}` : ''}
              </span>
            </span>
            <Link
              to={`/events/${e.id}/kapi`}
              className="justify-self-start text-md font-semibold underline-offset-4 hover:underline sm:justify-self-end"
            >
              Kapı ekranı
            </Link>
          </li>
        ))}
      </ul>
    </Panel>
  )
}
