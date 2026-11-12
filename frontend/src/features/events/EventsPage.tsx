import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { hasRole, useSession } from '@/lib/auth/session'
import { cn } from '@/lib/cn'
import { formatNumber } from '@/lib/format'
import { dayLabel, formatLocal, localDiffMs, nowLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { isActiveMembership, useMyMemberships } from '@/features/clubs/api'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import { myEventState, useEvents, useMyParticipationRequests, useMyRegistrations, type CampusEvent, type MyEventState } from './api'

type Filter = 'tumu' | 'hafta' | 'kulupler' | 'kampus' | 'kayitli'

const ROW = 'grid grid-cols-[4.5rem_minmax(0,1fr)] gap-x-5 gap-y-2 sm:grid-cols-[6rem_11rem_minmax(0,1fr)_11rem]'

/**
 * Etkinlikler (kalkış tablosu): yaklaşan etkinlikler günlere göre; satırda dev başlangıç saati,
 * düzenleyen (kulüp ya da kampüs birimi), başlık ve yer, sağda sizin durumunuz ya da kontenjan.
 */
export function EventsPage() {
  usePageTitle('Etkinlikler')
  const session = useSession()
  const student = hasRole(session, 'ROLE_STUDENT', 'ROLE_CLUB_OFFICIAL')
  const events = useEvents()
  const registrations = useMyRegistrations(student)
  const requests = useMyParticipationRequests(student)
  const memberships = useMyMemberships(student)
  const [params, setParams] = useSearchParams()
  const [q, setQ] = useState('')
  const filter = (params.get('goster') as Filter | null) ?? 'tumu'
  const now = nowLocalIso()

  const myClubs = useMemo(() => new Set((memberships.data ?? []).filter(isActiveMembership).map((m) => m.clubId)), [memberships.data])
  const stateOf = (e: CampusEvent) => myEventState(e.id, registrations.data, requests.data)

  const setFilter = (f: Filter) => {
    const next = new URLSearchParams(params)
    if (f === 'tumu') next.delete('goster')
    else next.set('goster', f)
    setParams(next, { replace: true })
  }

  return (
    <div className="mx-auto max-w-[80rem]">
      <PageHeader
        title="Etkinlikler"
        actions={
          student ? (
            <Button asChild>
              <Link to="/me/tickets">Biletlerim</Link>
            </Button>
          ) : undefined
        }
      />
      <QueryBoundary query={events} what="Etkinlikler" skeletonRows={6}>
        {(list) => {
          // Bitmemiş etkin etkinlikler, başlangıca göre.
          const upcoming = list
            .filter((e) => e.status === 'ACTIVE' && localDiffMs(now, e.endsAt ?? e.startsAt) >= 0)
            .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
          const week = (e: CampusEvent) => localDiffMs(now, e.startsAt) <= 7 * 86_400_000
          const matches: Record<Filter, (e: CampusEvent) => boolean> = {
            tumu: () => true,
            hafta: week,
            kulupler: (e) => !!e.clubId && myClubs.has(e.clubId),
            kampus: (e) => !e.clubId,
            kayitli: (e) => stateOf(e).kind !== 'none' && stateOf(e).kind !== 'rejected',
          }
          const items: [Filter, string][] = [
            ['tumu', 'Tümü'],
            ['hafta', 'Bu hafta'],
            ...(student ? ([['kulupler', 'Kulüplerim']] as [Filter, string][]) : []),
            ['kampus', 'Kampüs'],
            ...(student ? ([['kayitli', 'Kayıtlı olduklarım']] as [Filter, string][]) : []),
          ]
          const needle = q.trim().toLocaleLowerCase('tr-TR')
          const shown = upcoming
            .filter(matches[filter] ?? matches.tumu)
            .filter((e) => !needle || [e.title, e.clubName ?? '', e.organizerName ?? '', e.location ?? '', e.speakers ?? ''].some((s) => s.toLocaleLowerCase('tr-TR').includes(needle)))

          return (
            <>
              <nav aria-label="Etkinlik süzgeçleri" className="mt-8 border-y border-rule">
                <ul className="-mx-3 flex flex-wrap">
                  {items.map(([f, label]) => (
                    <li key={f}>
                      <button
                        type="button"
                        aria-pressed={filter === f}
                        onClick={() => setFilter(f)}
                        className={cn('relative flex h-12 items-center gap-2 px-3 text-md font-semibold transition-colors', filter === f ? 'text-ink' : 'text-ink-3 hover:text-ink')}
                      >
                        {label}
                        <span className="tabular text-sm font-normal text-ink-3">{upcoming.filter(matches[f]).length}</span>
                        {filter === f && <span aria-hidden className="absolute inset-x-3 -bottom-px h-[3px] bg-etkinlik" />}
                      </button>
                    </li>
                  ))}
                </ul>
              </nav>
              <div className="mt-6 max-w-[32rem]">
                <Field label="Ara">
                  <Input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Etkinlik, kulüp, konuşmacı ya da yer" />
                </Field>
              </div>
              <div className="mt-8">
                {shown.length === 0 ? (
                  <EmptyState
                    title={upcoming.length === 0 ? 'Yaklaşan etkinlik yok' : 'Eşleşen etkinlik yok'}
                    action={
                      upcoming.length > 0 ? (
                        <Button
                          onClick={() => {
                            setQ('')
                            setFilter('tumu')
                          }}
                        >
                          Süzgeçleri temizle
                        </Button>
                      ) : undefined
                    }
                  >
                    {upcoming.length === 0 ? 'Kulüpler ve kampüs birimleri yeni etkinlik yayımladığında burada görünür.' : 'Başka bir süzgeç seçin ya da aramayı değiştirin.'}
                  </EmptyState>
                ) : (
                  <Board events={shown} now={now} stateOf={stateOf} />
                )}
              </div>
            </>
          )
        }}
      </QueryBoundary>
    </div>
  )
}

function Board({ events, now, stateOf }: { events: CampusEvent[]; now: string; stateOf: (e: CampusEvent) => MyEventState }) {
  const groups: [string, CampusEvent[]][] = []
  for (const e of events) {
    const key = e.startsAt.slice(0, 10)
    const last = groups[groups.length - 1]
    if (last && last[0] === key) last[1].push(e)
    else groups.push([key, [e]])
  }
  return (
    <div className="flex flex-col gap-12">
      {groups.map(([day, list]) => {
        const label = dayLabel(list[0]!.startsAt, now)
        const full = formatLocal(list[0]!.startsAt, 'long')
        return (
          <section key={day} aria-label={label === full ? label : `${label}, ${full}`}>
            <h2 className="flex items-baseline gap-3 border-b-2 border-ink pb-2">
              <span className="text-3xl leading-none font-heavy tracking-[-0.02em]">{label}</span>
              {label !== full && <span className="text-md text-ink-3">{full}</span>}
            </h2>
            <ul>
              {list.map((e) => (
                <EventRow key={e.id} event={e} state={stateOf(e)} />
              ))}
            </ul>
          </section>
        )
      })}
    </div>
  )
}

const STATE_BADGE: Partial<Record<MyEventState['kind'], { label: string; tone: 'success' | 'warning' | 'info' }>> = {
  registered: { label: 'Kayıtlısınız', tone: 'success' },
  waitlisted: { label: 'Bekleme listesindesiniz', tone: 'info' },
  pending: { label: 'Onay bekleniyor', tone: 'warning' },
}

function EventRow({ event: e, state }: { event: CampusEvent; state: MyEventState }) {
  const badge = STATE_BADGE[state.kind]
  return (
    <li className="border-b border-rule">
      <Link to={`/events/${e.id}`} className={`${ROW} row-fill group py-6`}>
        <span className="row-span-2 sm:row-span-1">
          <span className="tabular block text-3xl leading-none font-heavy">{formatLocal(e.startsAt, 'time')}</span>
          {e.endsAt && <span className="tabular mt-1 block text-sm text-ink-3">{formatLocal(e.endsAt, 'time')} bitiş</span>}
        </span>
        <span className="hidden min-w-0 items-start gap-2 pt-1 sm:flex">
          <span aria-hidden className="mt-1 h-4 w-[3px] shrink-0 bg-etkinlik" />
          <span className="min-w-0 text-md font-semibold">
            {e.clubName ?? e.organizerName ?? 'Kampüs'}
            {!e.clubId && <span className="block text-sm font-normal text-ink-3">Kampüs etkinliği</span>}
          </span>
        </span>
        <span className="min-w-0">
          <span className="block text-xl leading-tight font-heavy text-balance transition-transform duration-300 ease-rail group-hover:translate-x-1">{e.title}</span>
          <span className="mt-1 flex flex-wrap gap-x-4 text-md text-ink-3">
            <span className="font-semibold text-etkinlik sm:hidden">{e.clubName ?? e.organizerName ?? 'Kampüs'}</span>
            {e.location && <span>{e.location}</span>}
            {e.speakers && <span>{e.speakers}</span>}
          </span>
        </span>
        <span className="col-start-2 flex flex-wrap items-center gap-x-3 sm:col-start-auto sm:flex-col sm:items-end sm:text-right">
          {badge ? (
            <Badge tone={badge.tone}>{badge.label}</Badge>
          ) : (
            <span className="text-sm text-ink-3">{e.capacity ? `${formatNumber(e.capacity)} kişilik` : 'Sınırsız kontenjan'}</span>
          )}
        </span>
      </Link>
    </li>
  )
}
