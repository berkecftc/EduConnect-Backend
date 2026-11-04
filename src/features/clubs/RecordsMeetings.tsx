import { useState } from 'react'
import { toast } from 'sonner'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { formatLocal, nowLocalIso } from '@/lib/time'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { Panel } from '@/components/ui/Panel'
import { QueryBoundary } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import { roleLabel, roleRank, useClubBoard, type ClubMember } from './api'
import { APPROVAL_STATUS, can, type ClubAccess } from './manage'
import {
  quorum,
  useDecisionBook,
  useMeetings,
  useSubmitMeeting,
  validateMeetingDraft,
  yearLabel,
  type Meeting,
  type MeetingDraft,
  type MeetingDraftErrors,
} from './records'

const fullName = (m: Pick<ClubMember, 'firstName' | 'lastName'>) => [m.firstName, m.lastName].filter(Boolean).join(' ')

/**
 * Toplantılar: yönetim toplantılarının tutanakları (yeniden eskiye) ve yılın karar defteri.
 * Genel sekreter ya da başkan tutanak yazar; başkan onaylayınca kararlar numaralanıp deftere işlenir.
 */
export function RecordsMeetings({ clubId, year, access, closed }: { clubId: string; year: number; access: ClubAccess; closed: boolean }) {
  const writes = can(access, 'PREPARE_MINUTES') && !closed
  const meetings = useMeetings(clubId, year)
  const book = useDecisionBook(clubId, year)
  const board = useClubBoard(clubId)
  const [writing, setWriting] = useState(false)

  return (
    <div className="grid gap-16 lg:grid-cols-12 lg:gap-10">
      <div className="flex min-w-0 flex-col gap-16 lg:col-span-8">
        {writing && (
          <MeetingForm clubId={clubId} board={board.data ?? []} publishes={access.actingPresident} onDone={() => setWriting(false)} />
        )}
        <Panel
          title="Toplantılar"
          action={
            writes && !writing ? (
              <Button size="sm" onClick={() => setWriting(true)}>
                Tutanak yaz
              </Button>
            ) : undefined
          }
        >
          <QueryBoundary query={meetings} what="Toplantılar" skeletonRows={3}>
            {(list) =>
              list.length === 0 ? (
                <p className="text-ink-3">{yearLabel(year)} akademik yılında tutanağı yazılmış toplantı yok.</p>
              ) : (
                <ul className="border-t border-rule">
                  {[...list]
                    .sort((a, b) => b.meetingAt.localeCompare(a.meetingAt))
                    .map((m) => (
                      <MeetingRow key={m.id} meeting={m} />
                    ))}
                </ul>
              )
            }
          </QueryBoundary>
        </Panel>
      </div>
      <aside className="lg:col-span-4">
        <Panel title="Karar defteri">
          <p className="text-sm text-ink-3">{yearLabel(year)}. Kararlar tutanak onaylanınca sırayla numaralanır.</p>
          <QueryBoundary query={book} what="Karar defteri" skeletonRows={3}>
            {(list) =>
              list.length === 0 ? (
                <p className="mt-4 text-ink-3">Bu yıl deftere işlenmiş karar yok.</p>
              ) : (
                <ol className="mt-4 border-t border-rule">
                  {[...list]
                    .sort((a, b) => a.number - b.number)
                    .map((d) => (
                      <li key={d.label} className="grid grid-cols-[3.5rem_minmax(0,1fr)] gap-x-4 border-b border-rule py-3">
                        <span className="tabular text-2xl leading-none font-heavy">{d.number}</span>
                        <span className="min-w-0">
                          <span className="block text-md whitespace-pre-line">{d.text}</span>
                          <span className="tabular mt-1 block text-sm text-ink-3">
                            {d.label}, {formatLocal(d.meetingAt, 'date')} toplantısı
                          </span>
                        </span>
                      </li>
                    ))}
                </ol>
              )
            }
          </QueryBoundary>
        </Panel>
      </aside>
    </div>
  )
}

/** Toplantı satırı: tarih, gündemin başı, katılım ve yeter sayı; açılınca tutanağın tamamı ve kararlar. */
function MeetingRow({ meeting: m }: { meeting: Meeting }) {
  const [open, setOpen] = useState(false)
  const names = (m.attendees ?? []).flatMap((a) => (a.name ? [a.name] : []))
  const unknown = m.attendeeIds.length - names.length
  const pending = m.status && m.status !== 'APPROVED'
  const id = `toplanti-${m.id}`

  return (
    <li className="border-b border-rule">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen((o) => !o)}
        className="row-fill grid w-full gap-x-6 gap-y-1 px-1 py-4 text-left sm:grid-cols-[8rem_minmax(0,1fr)_auto]"
      >
        <span className="tabular">
          <span className="block text-lg font-heavy">{formatLocal(m.meetingAt, 'short')}</span>
          <span className="block text-sm text-ink-3">{formatLocal(m.meetingAt, 'time')}</span>
        </span>
        <span className="min-w-0">
          <span className="line-clamp-2 font-semibold">{m.agenda}</span>
          <span className="mt-1 block text-sm text-ink-3">
            {[m.location, `${m.attendeeIds.length} katılımcı`, m.decisions.length ? `${m.decisions.length} karar` : null]
              .filter(Boolean)
              .join(', ')}
            .{' '}
            <span className={m.quorumMet ? undefined : 'font-semibold text-warning'}>
              {m.quorumMet ? 'Yeter sayı sağlandı.' : 'Yeter sayı sağlanmadı.'}
            </span>
          </span>
        </span>
        <span className="flex items-start gap-3 sm:justify-end">
          {pending && m.status && <Badge tone={APPROVAL_STATUS[m.status].tone}>{APPROVAL_STATUS[m.status].label}</Badge>}
          <span aria-hidden className="text-sm font-semibold text-ink-2">
            {open ? 'Kapat' : 'Tutanak'}
          </span>
        </span>
      </button>
      {open && (
        <div id={id} className="grid gap-6 px-1 pb-6 sm:pl-[calc(8rem+1.5rem)]">
          <Block title="Gündem" text={m.agenda} />
          <Block title="Tutanak" text={m.minutes} />
          <div>
            <h4 className="text-sm font-semibold text-ink-3">Katılanlar</h4>
            <p className="mt-1 text-md">
              {names.join(', ')}
              {unknown > 0 && `${names.length ? ' ve ' : ''}${unknown} kişi`}
            </p>
            <p className="text-sm text-ink-3">
              Toplantı günü yönetim kurulu {m.boardSize} kişiydi; {m.quorumMet ? 'yeter sayı sağlandı' : 'yeter sayı sağlanmadı'}.
              {m.preparedByName ? ` Tutanağı ${m.preparedByName} hazırladı.` : ''}
            </p>
          </div>
          {m.decisions.length > 0 && (
            <div>
              <h4 className="text-sm font-semibold text-ink-3">Kararlar</h4>
              <ol className="mt-1 border-t border-rule">
                {[...m.decisions]
                  .sort((a, b) => a.order - b.order)
                  .map((d, i) => (
                    <li key={d.id} className="grid grid-cols-[7rem_minmax(0,1fr)] gap-x-4 border-b border-rule py-2">
                      <span className="tabular text-sm font-semibold">{d.label ?? `Taslak ${i + 1}`}</span>
                      <span className="text-md whitespace-pre-line">{d.text}</span>
                    </li>
                  ))}
              </ol>
            </div>
          )}
        </div>
      )}
    </li>
  )
}

function Block({ title, text }: { title: string; text: string }) {
  return (
    <div>
      <h4 className="text-sm font-semibold text-ink-3">{title}</h4>
      <p className="mt-1 max-w-[68ch] text-md whitespace-pre-line">{text}</p>
    </div>
  )
}

const DECISIONS_MAX = 30

/**
 * Tutanak yaz: tarih (geçmiş), yer, gündem, tutanak, katılan görevliler (yeter sayı canlı gösterilir) ve kararlar.
 * Başkan (ya da vekili) yazarsa hemen deftere işlenir; diğerlerinde başkan onayı beklenir.
 */
function MeetingForm({
  clubId,
  board,
  publishes,
  onDone,
}: {
  clubId: string
  board: ClubMember[]
  publishes: boolean
  onDone: () => void
}) {
  const submit = useSubmitMeeting(clubId)
  const now = nowLocalIso().slice(0, 16)
  const [d, setD] = useState<MeetingDraft>({
    meetingAt: '',
    location: '',
    agenda: '',
    minutes: '',
    attendeeIds: [],
    decisions: [''],
    note: '',
  })
  const [errors, setErrors] = useState<MeetingDraftErrors>({})
  const [failure, setFailure] = useState<ApiError | null>(null)
  const set = <K extends keyof MeetingDraft>(k: K, v: MeetingDraft[K]) => {
    setD((x) => ({ ...x, [k]: v }))
    setErrors((e) => (k in e ? { ...e, [k]: undefined } : e))
    setFailure(null)
  }
  const people = [...board].sort((a, b) => roleRank(a.role) - roleRank(b.role))
  const q = quorum(board, d.attendeeIds)

  const onSubmit = (ev: React.FormEvent) => {
    ev.preventDefault()
    const next = validateMeetingDraft(d, nowLocalIso())
    setErrors(next)
    if (Object.keys(next).length) return
    submit.mutate(d, {
      onSuccess: (r) => {
        toast.success(r.status === 'APPROVED' ? 'Tutanak deftere işlendi' : 'Tutanak başkan onayına gönderildi')
        onDone()
      },
      onError: (e) => setFailure(toApiError(e)),
    })
  }

  return (
    <Panel title="Yeni tutanak">
      <p className="max-w-[64ch] text-md text-ink-2">
        {publishes
          ? 'Başkan olarak yazdığınız tutanak hemen deftere işlenir ve kararlar numaralanır.'
          : 'Tutanak başkan onayından sonra deftere işlenir; kararlar o zaman numaralanır.'}
      </p>
      <form noValidate onSubmit={onSubmit} className="mt-6 flex max-w-[44rem] flex-col gap-5">
        <div className="grid gap-5 sm:grid-cols-[16rem_minmax(0,1fr)]">
          <Field label="Tarih ve saat" required error={errors.meetingAt}>
            <Input type="datetime-local" max={now} value={d.meetingAt} onChange={(e) => set('meetingAt', e.target.value)} />
          </Field>
          <Field label="Yer" hint="İsteğe bağlı.">
            <Input maxLength={200} value={d.location} onChange={(e) => set('location', e.target.value)} />
          </Field>
        </div>
        <Field label="Gündem" required error={errors.agenda}>
          <Textarea
            rows={4}
            maxLength={5000}
            valueLength={d.agenda.length}
            value={d.agenda}
            onChange={(e) => set('agenda', e.target.value)}
          />
        </Field>
        <Field label="Tutanak" required error={errors.minutes} hint="Görüşülenler ve kimin ne dediği.">
          <Textarea
            rows={8}
            maxLength={20000}
            valueLength={d.minutes.length}
            value={d.minutes}
            onChange={(e) => set('minutes', e.target.value)}
          />
        </Field>

        <fieldset aria-describedby="yeter-sayi">
          <legend className="text-md font-semibold">
            Katılanlar <span className="text-danger">*</span>
          </legend>
          <ul className={`mt-2 border-t ${errors.attendeeIds ? 'border-danger' : 'border-rule'}`}>
            {people.map((m) => {
              const on = d.attendeeIds.includes(m.studentId)
              return (
                <li key={m.studentId} className="border-b border-rule">
                  <label className="row-fill flex cursor-pointer items-center gap-4 px-1 py-2.5">
                    <input
                      type="checkbox"
                      checked={on}
                      onChange={(e) =>
                        set(
                          'attendeeIds',
                          e.target.checked ? [...d.attendeeIds, m.studentId] : d.attendeeIds.filter((x) => x !== m.studentId),
                        )
                      }
                      className="size-4 accent-[var(--ec-kulup)]"
                    />
                    <span className="font-semibold">{fullName(m) || 'Görevli'}</span>
                    <span className="text-sm text-ink-3">{roleLabel(m.role)}</span>
                  </label>
                </li>
              )
            })}
          </ul>
          <p id="yeter-sayi" className={`mt-2 text-sm ${q.met ? 'text-ink-2' : 'text-ink-3'}`}>
            Yönetim kurulundan {q.present}/{q.size} kişi seçili:{' '}
            <span className={q.met ? 'font-semibold text-success' : 'font-semibold'}>
              {q.met ? 'yeter sayı sağlanıyor.' : 'yeter sayı için kurulun yarısından fazlası gerekir.'}
            </span>
          </p>
          {errors.attendeeIds && <p className="mt-1 text-sm font-semibold text-danger">{errors.attendeeIds}</p>}
        </fieldset>

        <fieldset>
          <legend className="text-md font-semibold">Kararlar</legend>
          <p className="text-sm text-ink-3">İsteğe bağlı. Her karar ayrı yazılır; boş bırakılanlar gönderilmez.</p>
          <ol className="mt-3 flex flex-col gap-3">
            {d.decisions.map((text, i) => (
              <li key={i} className="grid grid-cols-[2rem_minmax(0,1fr)_auto] items-start gap-x-3">
                <span className="tabular pt-2.5 text-md font-heavy">{i + 1}</span>
                <Textarea
                  aria-label={`${i + 1}. karar`}
                  rows={2}
                  maxLength={2000}
                  value={text}
                  onChange={(e) =>
                    set(
                      'decisions',
                      d.decisions.map((x, j) => (j === i ? e.target.value : x)),
                    )
                  }
                />
                <button
                  type="button"
                  onClick={() =>
                    set(
                      'decisions',
                      d.decisions.filter((_, j) => j !== i),
                    )
                  }
                  className="pt-2.5 text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline"
                >
                  Kaldır<span className="sr-only">: {i + 1}. karar</span>
                </button>
              </li>
            ))}
          </ol>
          {d.decisions.length < DECISIONS_MAX && (
            <button
              type="button"
              onClick={() => set('decisions', [...d.decisions, ''])}
              className="mt-3 text-sm font-semibold underline-offset-4 hover:underline"
            >
              Karar ekle
            </button>
          )}
          {errors.decisions && <p className="mt-1 text-sm font-semibold text-danger">{errors.decisions}</p>}
        </fieldset>

        <Field label={publishes ? 'Not' : 'Başkana not'} hint="İsteğe bağlı.">
          <Textarea rows={2} maxLength={1000} valueLength={d.note.length} value={d.note} onChange={(e) => set('note', e.target.value)} />
        </Field>

        {failure && (
          <Notice variant="line" tone="danger" title="Tutanak gönderilemedi">
            {failure.fieldErrors[0]?.message ?? failure.message}
          </Notice>
        )}
        <div className="flex flex-wrap items-center gap-5">
          <Button type="submit" variant="primary" loading={submit.isPending}>
            {publishes ? 'Deftere işle' : 'Onaya gönder'}
          </Button>
          <button
            type="button"
            onClick={onDone}
            className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
          >
            Vazgeç
          </button>
        </div>
      </form>
    </Panel>
  )
}
