import { useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatNumber } from '@/lib/format'
import { formatLocalRange } from '@/lib/time'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Textarea } from '@/components/ui/Textarea'
import { AUDIENCE_LABEL, type CampusEvent } from './api'
import { EVENT_STATUS, useDecideEvent } from './manage'

const REASON_MAX = 1000

/**
 * Etkinlik onayları: başkanlık yaptığınız kulüplerin başkan onayındaki, danışmanı olduğunuz kulüplerin
 * danışman onayındaki etkinlikleri. Başkan reddinde gerekçe zorunlu, danışmanda isteğe bağlı.
 */
export function EventApprovals({ president, advisor }: { president: CampusEvent[]; advisor: CampusEvent[] }) {
  const items = [...president.map((e) => ({ e, role: 'president' as const })), ...advisor.map((e) => ({ e, role: 'advisor' as const }))].sort((a, b) =>
    a.e.startsAt.localeCompare(b.e.startsAt),
  )
  if (items.length === 0) return null
  return (
    <section aria-labelledby="etkinlik-onaylari">
      <h2 id="etkinlik-onaylari" className="flex items-baseline gap-4 border-b-2 border-ink pb-3">
        <span className="text-3xl leading-none font-heavy tracking-[-0.02em]">Etkinlikler</span>
        <span className="tabular text-md text-ink-3">{items.length} etkinlik</span>
      </h2>
      <ul>
        {items.map(({ e, role }) => (
          <Item key={e.id} event={e} role={role} />
        ))}
      </ul>
    </section>
  )
}

function Item({ event: e, role }: { event: CampusEvent; role: 'president' | 'advisor' }) {
  const decide = useDecideEvent()
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const fail = (err: unknown) => toast.error(toApiError(err).message)
  const status = EVENT_STATUS[e.status]
  const reasonRequired = role === 'president'

  return (
    <li className="@container border-b border-rule py-6">
      <div className="grid gap-x-8 gap-y-3 @lg:grid-cols-[minmax(0,1fr)_11rem] @4xl:grid-cols-[10rem_minmax(0,1fr)_13rem]">
        <div>
          <p className="font-semibold">{role === 'president' ? 'Başkan onayı' : 'Danışman onayı'}</p>
          <p className="text-sm text-ink-3">{e.clubName}</p>
          <Badge tone={status.tone} className="mt-2">
            {status.label}
          </Badge>
        </div>
        <div className="min-w-0">
          <Link to={`/events/${e.id}/yonetim`} className="text-lg font-semibold underline-offset-4 hover:underline">
            {e.title}
          </Link>
          <p className="mt-1 flex flex-wrap gap-x-4 text-md text-ink-2">
            <span className="tabular">{formatLocalRange(e.startsAt, e.endsAt)}</span>
            {e.location && <span>{e.location}</span>}
          </p>
          <p className="mt-1 flex flex-wrap gap-x-4 text-sm text-ink-3">
            {e.audience && <span>{AUDIENCE_LABEL[e.audience]}</span>}
            {e.admission && <span>{e.admission === 'APPROVAL_REQUIRED' ? 'Onaylı kabul' : 'Otomatik kabul'}</span>}
            <span>{e.capacity ? `${formatNumber(e.capacity)} kişilik` : 'Sınırsız kontenjan'}</span>
          </p>
          {e.description && <p className="mt-2 line-clamp-3 max-w-[64ch] text-md text-ink-2">{e.description}</p>}
        </div>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2 @lg:col-start-2 @lg:row-span-2 @lg:row-start-1 @lg:flex-col @lg:items-end @lg:text-right @4xl:col-start-3 @4xl:row-span-1">
          <div className="flex items-center gap-4">
            <button type="button" onClick={() => setRejecting(true)} className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline">
              Reddet
            </button>
            <Button
              size="sm"
              variant="primary"
              loading={decide.isPending && !rejecting}
              onClick={() =>
                decide.mutate(
                  { role, eventId: e.id, kind: 'approve' },
                  { onSuccess: () => toast.success(role === 'president' ? 'Etkinlik danışman onayına gönderildi' : 'Etkinlik yayımlandı'), onError: fail },
                )
              }
            >
              Onayla
            </Button>
          </div>
        </div>
      </div>
      <ConfirmDialog
        open={rejecting}
        onOpenChange={setRejecting}
        title={`${e.title} reddedilsin mi?`}
        description="Gerekçe etkinliği hazırlayana iletilir; düzeltip yeniden onaya gönderebilir."
        confirmLabel="Reddet"
        destructive
        loading={decide.isPending}
        onConfirm={() =>
          reasonRequired && !reason.trim()
            ? toast.error('Ret gerekçesi yazın.')
            : decide.mutate(
                { role, eventId: e.id, kind: 'reject', reason },
                {
                  onSuccess: () => {
                    toast.success('Etkinlik reddedildi')
                    setRejecting(false)
                  },
                  onError: fail,
                },
              )
        }
      >
        <Field label="Gerekçe" required={reasonRequired} hint={reasonRequired ? undefined : 'İsteğe bağlı.'}>
          <Textarea maxLength={REASON_MAX} valueLength={reason.length} value={reason} onChange={(ev) => setReason(ev.target.value)} rows={3} />
        </Field>
      </ConfirmDialog>
    </li>
  )
}
