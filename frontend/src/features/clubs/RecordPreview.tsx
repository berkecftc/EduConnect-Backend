import type { ReactNode } from 'react'
import { toast } from 'sonner'
import { downloadFile } from '@/lib/api/client'
import { toApiError } from '@/lib/api/problem'
import { formatLocal } from '@/lib/time'
import { useClubBoard } from './api'
import type { ApprovalRequest } from './manage'
import { formatMoney, quorum, type FinanceEntry, type ReportSnapshot, type Sponsorship } from './records'

/**
 * Onay kaydındaki kulüp kaydının önizlemesi: tutanak, bütçe, gelir-gider, sponsorluk ve dönem raporu.
 * Onaylayan, neyi onayladığını kaydın kendisinden görür. Tür bunlardan biri değilse ya da kayıt gelmediyse null.
 */
export function RecordPreview({ request: r }: { request: ApprovalRequest }) {
  switch (r.type) {
    case 'CLUB_MEETING_MINUTES':
      return r.meeting ? <MeetingPreview request={r} /> : null
    case 'CLUB_BUDGET':
      return r.budget ? (
        <div>
          <Rows
            rows={[
              ['Akademik yıl', r.budget.academicYearLabel],
              ['Planlanan gelir', <Money key="g" value={r.budget.plannedIncome} />],
              ['Planlanan gider', <Money key="d" value={r.budget.plannedExpense} />],
              ['Açıklama', r.budget.description],
            ]}
          />
          <Note text={r.note} />
        </div>
      ) : null
    case 'CLUB_FINANCE_ENTRY':
      return r.financeEntry ? <EntryPreview clubId={r.clubId} entry={r.financeEntry} note={r.note} /> : null
    case 'CLUB_SPONSORSHIP':
      return r.sponsorship ? <SponsorshipPreview clubId={r.clubId} sponsorship={r.sponsorship} note={r.note} /> : null
    case 'CLUB_ACTIVITY_REPORT':
    case 'CLUB_AUDIT_REPORT':
      return r.report ? (
        <div>
          <p className="text-lg font-semibold">{r.report.academicYearLabel}</p>
          {r.report.body && <p className="mt-1 line-clamp-5 max-w-[64ch] whitespace-pre-line text-ink-2">{r.report.body}</p>}
          {r.report.recommendations && (
            <p className="mt-2 line-clamp-3 max-w-[64ch] whitespace-pre-line text-ink-2">
              <span className="font-semibold text-ink">Öneriler: </span>
              {r.report.recommendations}
            </p>
          )}
          {r.report.financeNote && (
            <p className="mt-2 line-clamp-3 max-w-[64ch] whitespace-pre-line text-ink-2">
              <span className="font-semibold text-ink">Mali değerlendirme: </span>
              {r.report.financeNote}
            </p>
          )}
          {r.report.snapshot && r.type === 'CLUB_ACTIVITY_REPORT' && <SnapshotLine snapshot={r.report.snapshot} />}
          <Note text={r.note} />
        </div>
      ) : null
    default:
      return null
  }
}

function MeetingPreview({ request: r }: { request: ApprovalRequest }) {
  const m = r.meeting!
  const board = useClubBoard(r.clubId)
  const names = (m.attendees ?? []).flatMap((a) => (a.name ? [a.name] : []))
  const q = board.data ? quorum(board.data, m.attendeeIds) : null
  return (
    <div>
      <p className="text-lg font-semibold">
        {formatLocal(m.meetingAt, 'datetime')}
        {m.location ? `, ${m.location}` : ''}
      </p>
      <p className="mt-1 line-clamp-3 max-w-[64ch] whitespace-pre-line text-ink-2">{m.agenda}</p>
      <p className="mt-2 text-sm text-ink-3">
        {m.attendeeIds.length} katılımcı{names.length ? `: ${names.join(', ')}` : ''}.{' '}
        <span className={m.quorumMet ? 'text-ink-2' : 'font-semibold text-warning'}>
          {m.quorumMet ? 'Yeter sayı sağlandı' : 'Yeter sayı sağlanmadı'}
          {q ? ` (${q.present}/${m.boardSize})` : ''}.
        </span>
      </p>
      {m.decisions.length > 0 && (
        <ol className="mt-3 max-w-[64ch] list-decimal pl-5 text-md text-ink-2">
          {m.decisions.map((d) => (
            <li key={d.id}>{d.text}</li>
          ))}
        </ol>
      )}
      <Note text={r.note} />
    </div>
  )
}

function EntryPreview({ clubId, entry: e, note }: { clubId: string; entry: FinanceEntry; note: string | null }) {
  return (
    <div>
      <p className="flex flex-wrap items-baseline gap-x-4">
        <span className="tabular text-2xl font-heavy">
          {e.type === 'INCOME' ? '+' : '−'}
          {formatMoney(e.amount)}
        </span>
        <span className="text-md text-ink-2">{e.type === 'INCOME' ? 'Gelir' : 'Gider'}</span>
        {e.budgetExceeded && <span className="text-sm font-semibold text-warning">Bütçeyi aşıyor</span>}
      </p>
      <p className="mt-1 text-md">{e.description}</p>
      <p className="mt-1 text-sm text-ink-3">{formatLocal(e.occurredOn, 'date')}</p>
      {e.hasDocument && <DocumentLink url={`/clubs/${clubId}/finance/entries/${e.id}/document`} name={e.documentName} />}
      <Note text={note} />
    </div>
  )
}

function SponsorshipPreview({ clubId, sponsorship: s, note }: { clubId: string; sponsorship: Sponsorship; note: string | null }) {
  return (
    <div>
      <p className="text-lg font-semibold">{s.sponsorName}</p>
      <p className="mt-1 line-clamp-3 max-w-[64ch] whitespace-pre-line text-ink-2">{s.description}</p>
      <Rows
        rows={[
          ['Nakit', s.cashAmount != null ? <Money key="n" value={s.cashAmount} /> : null],
          ['Ayni katkı', s.inKind],
          ['Dönem', `${formatLocal(s.startsOn, 'date')}${s.endsOn ? ` – ${formatLocal(s.endsOn, 'date')}` : ''}`],
        ]}
      />
      {s.hasDocument && <DocumentLink url={`/clubs/${clubId}/finance/sponsorships/${s.id}/document`} name={s.documentName} />}
      <Note text={note} />
    </div>
  )
}

/** Faaliyet raporunun gönderildiği andaki rakamları, tek satırda. */
function SnapshotLine({ snapshot: s }: { snapshot: ReportSnapshot }) {
  const parts = [
    s.activeMembers != null && `${s.activeMembers} üye`,
    s.events != null && `${s.events} etkinlik`,
    s.meetings != null && `${s.meetings} toplantı`,
    s.decisions != null && `${s.decisions} karar`,
    s.balance != null && `bakiye ${formatMoney(s.balance)}`,
  ].filter(Boolean)
  return parts.length ? <p className="tabular mt-2 text-sm text-ink-3">{parts.join(', ')}</p> : null
}

export function DocumentLink({ url, name }: { url: string; name: string | null }) {
  return (
    <button
      type="button"
      onClick={() => downloadFile(url, name ?? 'belge').catch((e) => toast.error(toApiError(e).message))}
      className="mt-2 text-sm font-semibold underline-offset-4 hover:underline"
    >
      Belgeyi indir{name ? ` (${name})` : ''}
    </button>
  )
}

export function Money({ value, className }: { value: number | null | undefined; className?: string }) {
  return <span className={`tabular ${className ?? ''}`}>{formatMoney(value)}</span>
}

function Rows({ rows }: { rows: [string, ReactNode][] }) {
  const shown = rows.filter(([, v]) => v)
  if (shown.length === 0) return null
  return (
    <dl className="mt-2 grid max-w-[64ch] gap-x-6 gap-y-1.5 sm:grid-cols-[9rem_minmax(0,1fr)]">
      {shown.map(([k, v]) => (
        <div key={k} className="contents">
          <dt className="text-sm text-ink-3">{k}</dt>
          <dd className="text-md break-words whitespace-pre-line">{v}</dd>
        </div>
      ))}
    </dl>
  )
}

function Note({ text }: { text: string | null | undefined }) {
  if (!text) return null
  return <p className="mt-2 max-w-[64ch] text-md whitespace-pre-line text-ink-2">{text}</p>
}
