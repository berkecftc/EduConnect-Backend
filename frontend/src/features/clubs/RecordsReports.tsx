import { useState } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatInstant, nowLocalIso } from '@/lib/time'
import { formatNumber } from '@/lib/format'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Panel } from '@/components/ui/Panel'
import { QueryBoundary, Skeleton } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import { can, type ClubAccess } from './manage'
import {
  REPORT_STATUS,
  useAcademicYearOf,
  formatMoney,
  useReportPreview,
  useReports,
  useSubmitReport,
  useWriteReport,
  yearLabel,
  type Report,
  type ReportSnapshot,
  type ReportType,
  type ReportWrite,
} from './records'

type Props = { clubId: string; year: number; access: ClubAccess; closed: boolean }

/**
 * Dönem raporları: faaliyet raporu (genel sekreter ya da başkan yazar, sayman mali değerlendirme ekler; başkan ve
 * danışman onaylar) ve denetim raporu (denetim kurulu yazar, danışman onaylar). Yalnız bu ve geçen akademik yıl
 * için hazırlanır; gönderilen rapor düzenlenemez, reddedilirse taslağa döner.
 */
export function RecordsReports(props: Props) {
  const reports = useReports(props.clubId, props.year)
  return (
    <QueryBoundary query={reports} what="Raporlar" skeletonRows={4}>
      {(list) => (
        <div className="flex flex-col gap-20">
          <ActivityReport {...props} report={list.find((r) => r.type === 'ACTIVITY')} />
          <AuditReport {...props} report={list.find((r) => r.type === 'AUDIT')} />
        </div>
      )}
    </QueryBoundary>
  )
}

/** Rapor yazılabilen yıllar: içinde bulunulan ve bir önceki akademik yıl. */
function useReportableYear(clubId: string) {
  const current = useAcademicYearOf(clubId)(nowLocalIso().slice(0, 10))
  return (year: number) => year === current || year === current - 1
}

function StatusBadge({ report }: { report?: Report }) {
  return report ? <Badge tone={REPORT_STATUS[report.status].tone}>{REPORT_STATUS[report.status].label}</Badge> : <Badge>Hazırlanmadı</Badge>
}

function ActivityReport({ clubId, year, access, closed, report }: Props & { report?: Report }) {
  const reportableYear = useReportableYear(clubId)
  const editable = reportableYear(year) && !closed && (!report || report.status === 'DRAFT')
  const writesSummary = editable && can(access, 'PREPARE_ACTIVITY_REPORT')
  const writesFinance = editable && can(access, 'PREPARE_FINANCE')
  const live = useReportPreview(clubId, year, reportableYear(year) && !report?.snapshot)
  const [dirty, setDirty] = useState({ summary: false, finance: false })
  const route = access.actingPresident ? 'danışman onayına' : 'başkan ve danışman onayına'

  return (
    <Panel title={`${yearLabel(year)} faaliyet raporu`} action={<StatusBadge report={report} />}>
      <Stamp report={report} />
      <div className="mt-6">
        {report?.snapshot ? (
          <Figures snapshot={report.snapshot} caption="Rapor gönderildiği andaki rakamlar." />
        ) : live.isPending && reportableYear(year) ? (
          <Skeleton rows={2} />
        ) : live.data ? (
          <Figures snapshot={live.data} caption="Şimdiki rakamlar; rapor gönderilince sabitlenir." />
        ) : null}
      </div>

      <div className="mt-10 grid gap-10">
        {writesSummary ? (
          <DraftForm
            clubId={clubId}
            year={year}
            fields={[
              {
                key: 'summary',
                label: 'Faaliyet özeti',
                hint: 'Yıl boyunca yapılanlar, öne çıkan etkinlikler, karşılaşılan güçlükler.',
                max: 20000,
                rows: 10,
              },
            ]}
            initial={{ summary: report?.body ?? '' }}
            build={(v) => ({ kind: 'activity', summary: v.summary ?? '' })}
            onDirty={(d) => setDirty((x) => ({ ...x, summary: d }))}
          />
        ) : (
          <Text title="Faaliyet özeti" text={report?.body} empty="Henüz yazılmadı." />
        )}
        {writesFinance ? (
          <DraftForm
            clubId={clubId}
            year={year}
            fields={[
              {
                key: 'financeNote',
                label: 'Mali değerlendirme',
                hint: 'Saymanın notu: gelir ve giderlerin bütçeye göre durumu.',
                max: 5000,
                rows: 4,
              },
            ]}
            initial={{ financeNote: report?.financeNote ?? '' }}
            build={(v) => ({ kind: 'financeNote', financeNote: v.financeNote ?? '' })}
            onDirty={(d) => setDirty((x) => ({ ...x, finance: d }))}
          />
        ) : (
          (report?.financeNote || writesSummary) && (
            <Text title="Mali değerlendirme" text={report?.financeNote} empty="Sayman henüz yazmadı." />
          )
        )}
      </div>

      {writesSummary && report?.status === 'DRAFT' && (
        <SubmitReport
          clubId={clubId}
          year={year}
          type="ACTIVITY"
          ready={!!report.body}
          dirty={dirty.summary || dirty.finance}
          route={route}
        />
      )}
    </Panel>
  )
}

function AuditReport({ clubId, year, access, closed, report }: Props & { report?: Report }) {
  const auditor = can(access, 'PREPARE_AUDIT_REPORT')
  const reportableYear = useReportableYear(clubId)
  const editable = auditor && reportableYear(year) && !closed && (!report || report.status === 'DRAFT')
  const [dirty, setDirty] = useState(false)

  return (
    <Panel title={`${yearLabel(year)} denetim raporu`} action={<StatusBadge report={report} />}>
      <Stamp report={report} />
      {editable ? (
        <div className="mt-6 grid gap-10">
          <DraftForm
            clubId={clubId}
            year={year}
            fields={[
              { key: 'findings', label: 'Bulgular', hint: 'Mali kayıtların, kararların ve tutanakların denetimi.', max: 20000, rows: 8 },
              { key: 'recommendations', label: 'Öneriler', hint: 'İsteğe bağlı.', max: 5000, rows: 4 },
            ]}
            initial={{ findings: report?.body ?? '', recommendations: report?.recommendations ?? '' }}
            build={(v) => ({ kind: 'audit', findings: v.findings ?? '', recommendations: v.recommendations ?? '' })}
            onDirty={setDirty}
          />
          {report?.status === 'DRAFT' && (
            <SubmitReport clubId={clubId} year={year} type="AUDIT" ready={!!report.body} dirty={dirty} route="danışman onayına" />
          )}
        </div>
      ) : report ? (
        <div className="mt-6 grid gap-8">
          <Text title="Bulgular" text={report.body} empty="Yazılmadı." />
          {report.recommendations && <Text title="Öneriler" text={report.recommendations} empty="" />}
        </div>
      ) : (
        <p className="mt-4 text-ink-3">Denetim kurulu bu yıl için rapor göndermedi.</p>
      )}
    </Panel>
  )
}

/** Gönderilme ve onay tarihleri. */
function Stamp({ report }: { report?: Report }) {
  if (!report || report.status === 'DRAFT') return null
  return (
    <p className="text-sm text-ink-3">
      {formatInstant(report.submittedAt, 'date')} tarihinde gönderildi
      {report.approvedAt ? `, ${formatInstant(report.approvedAt, 'date')} tarihinde onaylandı` : ''}.
    </p>
  )
}

function Text({ title, text, empty }: { title: string; text: string | null | undefined; empty: string }) {
  return (
    <section>
      <h3 className="text-lg font-heavy">{title}</h3>
      {text ? (
        <p className="mt-2 max-w-[68ch] text-md whitespace-pre-line text-ink-2">{text}</p>
      ) : (
        <p className="mt-2 text-ink-3">{empty}</p>
      )}
    </section>
  )
}

/** Raporun rakamları: dev rakamlar, para tutarları ayrı satırda. */
function Figures({ snapshot: s, caption }: { snapshot: ReportSnapshot; caption: string }) {
  const counts: [string, number | null, string?][] = [
    ['Üye', s.activeMembers],
    ['Ayrılan üye', s.endedMemberships],
    ['Etkinlik', s.events, s.completedEvents != null ? `${s.completedEvents} tamamlandı, ${s.cancelledEvents ?? 0} iptal` : undefined],
    ['Etkinlik kaydı', s.registrations],
    ['Katılım', s.attendances],
    ['Duyuru', s.announcements],
    ['Toplantı', s.meetings],
    ['Karar', s.decisions],
  ]
  const money: [string, number | null][] = [
    ['Gelir', s.income],
    ['Gider', s.expense],
    ['Bakiye', s.balance],
    ['Planlanan gelir', s.plannedIncome],
    ['Planlanan gider', s.plannedExpense],
  ]
  return (
    <figure className="border-t-2 border-ink pt-5">
      <dl className="grid grid-cols-2 gap-x-8 gap-y-6 sm:grid-cols-4">
        {counts.map(([k, v, note]) => (
          <div key={k}>
            <dt className="text-sm text-ink-3">{k}</dt>
            <dd className="tabular mt-1 text-4xl leading-none font-heavy">{v == null ? '–' : formatNumber(v)}</dd>
            {note && <dd className="mt-1 text-sm text-ink-3">{note}</dd>}
          </div>
        ))}
      </dl>
      <dl className="mt-8 grid grid-cols-2 gap-x-8 gap-y-4 border-t border-rule pt-5 sm:grid-cols-5">
        {money.map(([k, v]) => (
          <div key={k}>
            <dt className="text-sm text-ink-3">{k}</dt>
            <dd className="tabular mt-1 text-lg font-heavy">{formatMoney(v)}</dd>
          </div>
        ))}
      </dl>
      <figcaption className="mt-4 text-sm text-ink-3">{caption}</figcaption>
    </figure>
  )
}

type DraftField = { key: string; label: string; hint?: string; max: number; rows: number }

/** Taslak metni: yazılan yerelde tutulur, "Taslağı kaydet" sunucuya yazar. Kaydedilmemiş değişiklik varken gönderme kapalı. */
function DraftForm({
  clubId,
  year,
  fields,
  initial,
  build,
  onDirty,
}: {
  clubId: string
  year: number
  fields: DraftField[]
  initial: Record<string, string>
  build: (values: Record<string, string>) => ReportWrite
  onDirty: (dirty: boolean) => void
}) {
  const write = useWriteReport(clubId, year)
  const [values, setValues] = useState(initial)
  const [saved, setSaved] = useState(initial)
  const differs = (v: Record<string, string>) => fields.some((f) => (v[f.key] ?? '') !== (saved[f.key] ?? ''))
  const dirty = differs(values)

  return (
    <form
      noValidate
      onSubmit={(e) => {
        e.preventDefault()
        write.mutate(build(values), {
          onSuccess: () => {
            setSaved(values)
            onDirty(false)
            toast.success('Taslak kaydedildi')
          },
          onError: (x) => toast.error(toApiError(x).message),
        })
      }}
      className="flex max-w-[48rem] flex-col gap-5"
    >
      {fields.map((f) => (
        <Field key={f.key} label={f.label} hint={f.hint}>
          <Textarea
            rows={f.rows}
            maxLength={f.max}
            valueLength={(values[f.key] ?? '').length}
            value={values[f.key] ?? ''}
            onChange={(e) => {
              const next = { ...values, [f.key]: e.target.value }
              setValues(next)
              onDirty(differs(next))
            }}
          />
        </Field>
      ))}
      <div className="flex items-center gap-4">
        <Button type="submit" size="sm" disabled={!dirty} loading={write.isPending}>
          Taslağı kaydet
        </Button>
        {dirty && <span className="text-sm text-ink-3">Kaydedilmemiş değişiklik var.</span>}
      </div>
    </form>
  )
}

function SubmitReport({
  clubId,
  year,
  type,
  ready,
  dirty,
  route,
}: {
  clubId: string
  year: number
  type: ReportType
  ready: boolean
  dirty: boolean
  route: string
}) {
  const submit = useSubmitReport(clubId, year)
  const [open, setOpen] = useState(false)
  const [note, setNote] = useState('')
  const what = type === 'ACTIVITY' ? 'Faaliyet raporu' : 'Denetim raporu'
  return (
    <div className="mt-10 border-t border-rule pt-6">
      <Button variant="primary" disabled={!ready || dirty} onClick={() => setOpen(true)}>
        Onaya gönder
      </Button>
      <p className="mt-2 text-sm text-ink-3">
        {!ready
          ? type === 'ACTIVITY'
            ? 'Göndermek için önce faaliyet özetini yazıp kaydedin.'
            : 'Göndermek için önce bulguları yazıp kaydedin.'
          : dirty
            ? 'Önce değişiklikleri kaydedin.'
            : `Rapor ${route} gider; gönderdikten sonra düzenlenemez, reddedilirse taslağa döner.`}
      </p>
      <ConfirmDialog
        open={open}
        onOpenChange={setOpen}
        title={`${what} gönderilsin mi?`}
        description={`Rapor ${route} gider ve rakamlar bu anki haliyle sabitlenir. Gönderdikten sonra düzenleyemezsiniz.`}
        confirmLabel="Onaya gönder"
        loading={submit.isPending}
        onConfirm={() =>
          submit.mutate(
            { type, note },
            {
              onSuccess: (r) => {
                toast.success(r.status === 'APPROVED' ? `${what} onaylandı` : `${what} ${route} gönderildi`)
                setOpen(false)
              },
              onError: (e) => toast.error(toApiError(e).message),
            },
          )
        }
      >
        <Field label="Not" hint="İsteğe bağlı.">
          <Textarea rows={2} maxLength={1000} valueLength={note.length} value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
      </ConfirmDialog>
    </div>
  )
}
