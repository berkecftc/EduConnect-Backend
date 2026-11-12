import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { formatLocal, nowLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import type { CampusEvent } from '@/features/events/api'
import { EventForm } from '@/features/events/EventForm'
import { EMPTY_DRAFT, EVENT_STATUS, useCampusEvents, useCreateCampusEvent } from '@/features/events/manage'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import { RequireAccess } from './RequireAccess'

const DAY = new Intl.DateTimeFormat('tr-TR', { day: 'numeric', timeZone: 'UTC' })
const MONTH = new Intl.DateTimeFormat('tr-TR', { month: 'short', timeZone: 'UTC' })

/** Kampüs etkinlikleri (PERM_CAMPUS_PUBLISHER, F-64): kulüp dışı, onaysız yayımlanan etkinlikler. */
export function CampusEventsPage() {
  usePageTitle('Kampüs etkinlikleri')
  return (
    <RequireAccess perms={['PERM_CAMPUS_PUBLISHER']}>
      <CampusEvents />
    </RequireAccess>
  )
}

function CampusEvents() {
  const list = useCampusEvents()
  const now = nowLocalIso()
  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader
        title="Kampüs etkinlikleri"
        meta="Kariyer günleri, söyleşiler, kampüs şenlikleri: kulüplere bağlı olmayan etkinlikler onaya gitmeden yayımlanır."
        actions={
          <Button asChild variant="primary">
            <Link to="/manage/campus-events/new">Etkinlik oluştur</Link>
          </Button>
        }
      />
      <div className="mt-8">
        <QueryBoundary query={list} what="Kampüs etkinlikleri">
          {(items) => {
            const upcoming = items
              .filter((e) => e.status === 'ACTIVE' && (e.endsAt ?? e.startsAt) >= now)
              .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
            const past = items.filter((e) => !upcoming.includes(e)).sort((a, b) => b.startsAt.localeCompare(a.startsAt))
            if (items.length === 0) {
              return (
                <EmptyState title="Henüz kampüs etkinliği yok">
                  Oluşturduğunuz etkinlik hemen yayımlanır ve etkinlik listesinde “Kampüs” etiketiyle görünür.
                </EmptyState>
              )
            }
            return (
              <div className="flex flex-col gap-14">
                <EventList label="Yaklaşan" items={upcoming} empty="Yaklaşan kampüs etkinliği yok." />
                {past.length > 0 && <EventList label="Geçmiş ve iptal edilen" items={past} muted />}
              </div>
            )
          }}
        </QueryBoundary>
      </div>
    </div>
  )
}

function EventList({ label, items, empty, muted }: { label: string; items: CampusEvent[]; empty?: string; muted?: boolean }) {
  return (
    <section aria-label={label}>
      <h2 className="text-2xl">{label}</h2>
      {items.length === 0 ? (
        <p className="mt-3 text-md text-ink-2">{empty}</p>
      ) : (
        <ul className="mt-3 border-t border-ink">
          {items.map((e) => {
            const at = new Date(`${e.startsAt.slice(0, 10)}T12:00:00Z`)
            const status = EVENT_STATUS[e.status]
            return (
              <li key={e.id} className="border-b border-rule">
                <Link
                  to={`/events/${e.id}/yonetim`}
                  className={`row-fill grid grid-cols-[3.5rem_minmax(0,1fr)] gap-x-5 gap-y-1 py-4 sm:grid-cols-[4rem_minmax(0,1fr)_auto] ${muted ? 'text-ink-2' : ''}`}
                >
                  <span className="row-span-2 sm:row-span-1">
                    <span className="tabular block text-3xl leading-none font-heavy">{DAY.format(at)}</span>
                    <span className="mt-1 block text-xs text-ink-3">
                      {MONTH.format(at)}, {formatLocal(e.startsAt, 'time')}
                    </span>
                  </span>
                  <span className="min-w-0">
                    <span className="block text-lg font-semibold">{e.title}</span>
                    <span className="mt-0.5 flex flex-wrap gap-x-4 text-sm text-ink-3">
                      {e.organizerName && <span>{e.organizerName}</span>}
                      {e.location && <span>{e.location}</span>}
                      {e.capacity && <span className="tabular">{e.capacity} kişilik</span>}
                      {e.admission === 'APPROVAL_REQUIRED' && <span>onaylı katılım</span>}
                    </span>
                  </span>
                  <span className="col-start-2 sm:col-start-auto sm:text-right">
                    {e.status !== 'ACTIVE' && <Badge tone={status.tone}>{status.label}</Badge>}
                  </span>
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}

/** Kampüs etkinliği oluştur: düzenleyen birim zorunlu; kitle seçimi yok (herkese açık). */
export function CampusEventCreatePage() {
  usePageTitle('Kampüs etkinliği oluştur')
  return (
    <RequireAccess perms={['PERM_CAMPUS_PUBLISHER']}>
      <CampusEventCreate />
    </RequireAccess>
  )
}

function CampusEventCreate() {
  const navigate = useNavigate()
  const create = useCreateCampusEvent()
  const [failure, setFailure] = useState<ApiError | null>(null)
  return (
    <div className="mx-auto max-w-[72rem]">
      <p className="flex items-center gap-3 text-md">
        <span aria-hidden className="h-5 w-[4px] bg-etkinlik" />
        <Link to="/manage/campus-events" className="font-semibold underline-offset-4 hover:underline">
          Kampüs etkinlikleri
        </Link>
      </p>
      <PageHeader
        title="Kampüs etkinliği oluştur"
        meta="Kaydettiğinizde etkinlik hemen yayımlanır; kayıt, açılış zamanı geldiğinde başlar."
      />
      <div className="mt-8">
        <EventForm
          initial={EMPTY_DRAFT}
          campus
          submitLabel="Yayımla"
          busy={create.isPending}
          failure={failure}
          withPoster
          onSubmit={(draft, poster) => {
            setFailure(null)
            create.mutate(
              { draft, poster },
              {
                onSuccess: (e) => {
                  toast.success(`${e.title} yayımlandı`)
                  navigate(`/events/${e.id}/yonetim`)
                },
                onError: (err) => setFailure(toApiError(err)),
              },
            )
          }}
        />
      </div>
    </div>
  )
}
