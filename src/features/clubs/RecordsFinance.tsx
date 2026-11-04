import { useState } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatLocal, nowLocalIso } from '@/lib/time'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { FileField } from '@/components/ui/FileField'
import { Input } from '@/components/ui/Input'
import { Meter } from '@/components/ui/Meter'
import { Panel } from '@/components/ui/Panel'
import { Select } from '@/components/ui/Select'
import { QueryBoundary } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import { APPROVAL_STATUS, can, type ApprovalStatus, type ClubAccess } from './manage'
import { DocumentLink } from './RecordPreview'
import {
  DOCUMENT_MAX_BYTES,
  DOCUMENT_TYPES,
  formatMoney,
  parseMoney,
  useAcademicYearOf,
  useBudgets,
  useFinanceEntries,
  useFinanceSummary,
  useSponsorships,
  useSubmitBudget,
  useSubmitEntry,
  useSubmitSponsorship,
  yearLabel,
  type FinanceEntryType,
} from './records'

const AMOUNT_MAX = 1_000_000
const BUDGET_MAX = 10_000_000

/** Onaylanmamış kaydın durumu (onaylı kayıtta rozet yok). */
function Pending({ status }: { status: ApprovalStatus | null }) {
  if (!status || status === 'APPROVED') return null
  return <Badge tone={APPROVAL_STATUS[status].tone}>{APPROVAL_STATUS[status].label}</Badge>
}

/**
 * Mali işler: yılın özeti (gelir, gider, bakiye, bütçeye göre harcama), gelir-gider kayıtları, bütçeler ve
 * sponsorluklar. Sayman ya da başkan kayıt ve bütçe hazırlar, sponsorluk sorumlusu ya da başkan sponsorluk ekler.
 * Tutarlar onaylanınca hesaba geçer.
 */
export function RecordsFinance({ clubId, year, access, closed }: { clubId: string; year: number; access: ClubAccess; closed: boolean }) {
  const views = can(access, 'VIEW_FINANCE')
  const prepares = can(access, 'PREPARE_FINANCE') && !closed
  const sponsors = can(access, 'PREPARE_SPONSORSHIP') && !closed
  const [dialog, setDialog] = useState<'entry' | 'budget' | 'sponsorship' | null>(null)
  const route = access.actingPresident ? 'danışman onayına' : 'başkan ve danışman onayına'

  return (
    <div className="flex flex-col gap-16">
      {(prepares || sponsors) && (
        <div className="flex flex-wrap gap-3">
          {prepares && (
            <Button variant="primary" onClick={() => setDialog('entry')}>
              Gelir ya da gider ekle
            </Button>
          )}
          {prepares && <Button onClick={() => setDialog('budget')}>Bütçe hazırla</Button>}
          {sponsors && <Button onClick={() => setDialog('sponsorship')}>Sponsorluk ekle</Button>}
        </div>
      )}
      {views && <Summary clubId={clubId} year={year} />}
      <div className="grid gap-16 lg:grid-cols-12 lg:gap-10">
        {views && (
          <div className="min-w-0 lg:col-span-8">
            <Entries clubId={clubId} year={year} />
          </div>
        )}
        <aside className={`flex flex-col gap-16 ${views ? 'lg:col-span-4' : 'lg:col-span-8'}`}>
          {views && <Budgets clubId={clubId} />}
          <Sponsorships clubId={clubId} />
        </aside>
      </div>

      {dialog === 'entry' && <EntryDialog clubId={clubId} presides={access.actingPresident} onClose={() => setDialog(null)} />}
      {dialog === 'budget' && <BudgetDialog clubId={clubId} route={route} onClose={() => setDialog(null)} />}
      {dialog === 'sponsorship' && <SponsorshipDialog clubId={clubId} route={route} onClose={() => setDialog(null)} />}
    </div>
  )
}

/** Yılın özeti: dört büyük rakam, altında onaylı bütçeye göre harcama çizgisi. */
function Summary({ clubId, year }: { clubId: string; year: number }) {
  const summary = useFinanceSummary(clubId, year)
  return (
    <QueryBoundary query={summary} what="Mali özet" skeletonRows={2}>
      {(s) => {
        const b = s.budget
        const over = !!b && s.expense > b.plannedExpense
        return (
          <section aria-label={`${s.academicYearLabel} mali özeti`} className="border-t-2 border-ink pt-5">
            <dl className="grid grid-cols-2 gap-x-8 gap-y-6 md:grid-cols-4">
              {(
                [
                  ['Gelir', s.income],
                  ['Gider', s.expense],
                  [`${s.academicYearLabel} bakiyesi`, s.balance],
                  ['Toplam bakiye', s.overallBalance],
                ] as [string, number][]
              ).map(([k, v]) => (
                <div key={k}>
                  <dt className="text-sm text-ink-3">{k}</dt>
                  <dd className="tabular mt-1 text-2xl leading-none font-heavy whitespace-nowrap sm:text-3xl xl:text-4xl">
                    {formatMoney(v)}
                  </dd>
                </div>
              ))}
            </dl>
            <div className="mt-8 max-w-[40rem]">
              {b ? (
                <>
                  <p className="flex flex-wrap items-baseline justify-between gap-x-6 text-md">
                    <span>Bütçeye göre harcama</span>
                    <span className={`tabular ${over ? 'font-semibold text-danger' : 'text-ink-2'}`}>
                      {formatMoney(s.expense)} / {formatMoney(b.plannedExpense)}
                    </span>
                  </p>
                  <div className="mt-2">
                    <Meter
                      value={s.expense}
                      max={Math.max(1, b.plannedExpense)}
                      color={over ? 'var(--ec-danger)' : 'var(--ec-kulup)'}
                      label={`Planlanan ${formatMoney(b.plannedExpense)} giderin ${formatMoney(s.expense)} kadarı harcandı`}
                    />
                  </div>
                  <p className="mt-2 text-sm text-ink-3">
                    Planlanan gelir {formatMoney(b.plannedIncome)}.{over ? ' Harcama bütçeyi aştı.' : ''}
                  </p>
                </>
              ) : (
                <p className="text-md text-ink-3">{yearLabel(year)} için onaylanmış bütçe yok.</p>
              )}
            </div>
          </section>
        )
      }}
    </QueryBoundary>
  )
}

function Entries({ clubId, year }: { clubId: string; year: number }) {
  const entries = useFinanceEntries(clubId, year)
  return (
    <Panel title="Gelir ve gider">
      <QueryBoundary query={entries} what="Kayıtlar" skeletonRows={3}>
        {(list) =>
          list.length === 0 ? (
            <p className="text-ink-3">{yearLabel(year)} için kayıt yok.</p>
          ) : (
            <ul className="border-t border-rule">
              {list.map((e) => (
                <li
                  key={e.id}
                  className={`grid gap-x-6 gap-y-1 border-b border-rule py-3.5 sm:grid-cols-[6.5rem_minmax(0,1fr)_auto] sm:items-baseline ${e.status === 'REJECTED' || e.status === 'WITHDRAWN' ? 'text-ink-3' : ''}`}
                >
                  <span className="tabular text-sm text-ink-3">{formatLocal(e.occurredOn, 'short')}</span>
                  <span className="min-w-0">
                    <span className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                      <span className="font-semibold">{e.description}</span>
                      <Pending status={e.status} />
                      {e.budgetExceeded && <span className="text-sm font-semibold text-warning">Bütçeyi aşıyor</span>}
                      {e.sponsorshipId && <span className="text-sm text-ink-3">Sponsorluk</span>}
                    </span>
                    {e.preparedByName && <span className="block text-sm text-ink-3">Hazırlayan {e.preparedByName}</span>}
                    {e.hasDocument && <DocumentLink url={`/clubs/${clubId}/finance/entries/${e.id}/document`} name={e.documentName} />}
                  </span>
                  <span className="tabular text-lg font-heavy sm:text-right">
                    <span className="sr-only">{e.type === 'INCOME' ? 'Gelir' : 'Gider'} </span>
                    {e.type === 'INCOME' ? '+' : '−'}
                    {formatMoney(e.amount)}
                  </span>
                </li>
              ))}
            </ul>
          )
        }
      </QueryBoundary>
    </Panel>
  )
}

function Budgets({ clubId }: { clubId: string }) {
  const budgets = useBudgets(clubId)
  return (
    <Panel title="Bütçeler">
      <QueryBoundary query={budgets} what="Bütçeler" skeletonRows={2}>
        {(list) =>
          list.length === 0 ? (
            <p className="text-ink-3">Henüz bütçe hazırlanmadı.</p>
          ) : (
            <ul>
              {list.map((b) => (
                <li key={b.id} className="border-b border-rule py-3 first:pt-0">
                  <span className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                    <span className="tabular font-semibold">{b.academicYearLabel}</span>
                    <Pending status={b.status} />
                  </span>
                  <span className="tabular block text-sm text-ink-2">
                    Gelir {formatMoney(b.plannedIncome)}, gider {formatMoney(b.plannedExpense)}
                  </span>
                  {b.description && <span className="mt-1 line-clamp-2 block text-sm text-ink-3">{b.description}</span>}
                </li>
              ))}
            </ul>
          )
        }
      </QueryBoundary>
    </Panel>
  )
}

function Sponsorships({ clubId }: { clubId: string }) {
  const sponsorships = useSponsorships(clubId)
  return (
    <Panel title="Sponsorluklar">
      <QueryBoundary query={sponsorships} what="Sponsorluklar" skeletonRows={2}>
        {(list) =>
          list.length === 0 ? (
            <p className="text-ink-3">Sponsorluk kaydı yok.</p>
          ) : (
            <ul>
              {list.map((s) => (
                <li key={s.id} className="border-b border-rule py-3 first:pt-0">
                  <span className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                    <span className="font-semibold">{s.sponsorName}</span>
                    <Pending status={s.status} />
                  </span>
                  <span className="tabular block text-sm text-ink-2">
                    {[s.cashAmount != null ? formatMoney(s.cashAmount) : null, s.inKind].filter(Boolean).join(' ve ')}
                  </span>
                  <span className="tabular block text-sm text-ink-3">
                    {formatLocal(s.startsOn, 'date')}
                    {s.endsOn ? ` – ${formatLocal(s.endsOn, 'date')}` : ''}
                  </span>
                  {s.hasDocument && <DocumentLink url={`/clubs/${clubId}/finance/sponsorships/${s.id}/document`} name={s.documentName} />}
                </li>
              ))}
            </ul>
          )
        }
      </QueryBoundary>
    </Panel>
  )
}

// ——— Formlar ———

/** Tutar alanı: Türkçe biçim kabul edilir; geçersizse hata metni. */
function amountError(raw: string, max: number, required: boolean, min = 0.01): string | undefined {
  if (!raw.trim()) return required ? 'Tutarı yazın.' : undefined
  const v = parseMoney(raw)
  if (v === null) return 'Tutarı rakamla yazın, ör. 1.250,50.'
  if (v < min) return min > 0 ? 'Tutar sıfırdan büyük olmalı.' : 'Tutar eksi olamaz.'
  if (v > max) return `Tutar en fazla ${formatMoney(max)} olabilir.`
  return undefined
}

function EntryDialog({ clubId, presides, onClose }: { clubId: string; presides: boolean; onClose: () => void }) {
  const submit = useSubmitEntry(clubId)
  const today = nowLocalIso().slice(0, 10)
  const yearOf = useAcademicYearOf(clubId)
  const threshold = useFinanceSummary(clubId, yearOf(today)).data?.advisorThreshold
  const large = threshold != null ? `${formatMoney(threshold)} üstü tutarlar` : 'Büyük tutarlar'
  const [type, setType] = useState<FinanceEntryType>('EXPENSE')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [occurredOn, setOccurredOn] = useState(today)
  const [note, setNote] = useState('')
  const [document, setDocument] = useState<File | null>(null)
  const [errors, setErrors] = useState<Record<string, string | undefined>>({})
  const [failure, setFailure] = useState<string | null>(null)

  const onConfirm = () => {
    const next = {
      amount: amountError(amount, AMOUNT_MAX, true),
      description: description.trim() ? undefined : 'Neye ait olduğunu yazın.',
      occurredOn: !occurredOn ? 'Tarihi seçin.' : occurredOn > today ? 'İleri bir tarih seçilemez.' : undefined,
    }
    setErrors(next)
    setFailure(null)
    if (Object.values(next).some(Boolean)) return
    submit.mutate(
      { type, amount: parseMoney(amount)!, description, occurredOn, note, document },
      {
        onSuccess: (r) => {
          toast.success(r.status === 'APPROVED' ? 'Kayıt hesaba işlendi' : 'Kayıt onaya gönderildi')
          onClose()
        },
        onError: (e) => setFailure(toApiError(e).message),
      },
    )
  }

  return (
    <ConfirmDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Gelir ya da gider ekle"
      description={
        presides
          ? `${large} danışman onayına gider; diğerleri hemen hesaba işlenir.`
          : `Kayıt başkan onayına gider; ${large.toLocaleLowerCase('tr-TR')} ayrıca danışman onayından geçer.`
      }
      confirmLabel="Kaydet"
      loading={submit.isPending}
      onConfirm={onConfirm}
    >
      <div className="flex max-h-[60vh] flex-col gap-5 overflow-y-auto pr-1">
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Tür" required>
            <Select value={type} onChange={(e) => setType(e.target.value as FinanceEntryType)}>
              <option value="EXPENSE">Gider</option>
              <option value="INCOME">Gelir</option>
            </Select>
          </Field>
          <Field label="Tutar (₺)" required error={errors.amount} hint="Ör. 1.250,50">
            <Input inputMode="decimal" autoComplete="off" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </Field>
        </div>
        <Field label="Açıklama" required error={errors.description} hint="Ör. Turnuva ödülleri, salon kirası.">
          <Input maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <Field
          label="Tarih"
          required
          error={errors.occurredOn}
          hint={`Hangi akademik yıla yazılacağı tarihe göre belirlenir (${yearLabel(yearOf(occurredOn || today))}).`}
        >
          <Input type="date" max={today} value={occurredOn} onChange={(e) => setOccurredOn(e.target.value)} className="max-w-[12rem]" />
        </Field>
        <FileField
          label="Belge"
          hint="İsteğe bağlı. Fatura ya da makbuz; PDF, JPEG, PNG, GIF ya da WEBP, en fazla 10 MB."
          accept={DOCUMENT_TYPES.join(',')}
          maxBytes={DOCUMENT_MAX_BYTES}
          value={document}
          onChange={setDocument}
        />
        <Field label="Not" hint="İsteğe bağlı.">
          <Textarea rows={2} maxLength={1000} valueLength={note.length} value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
        {failure && <p className="text-sm font-semibold text-danger">{failure}</p>}
      </div>
    </ConfirmDialog>
  )
}

function BudgetDialog({ clubId, route, onClose }: { clubId: string; route: string; onClose: () => void }) {
  const submit = useSubmitBudget(clubId)
  const current = useAcademicYearOf(clubId)(nowLocalIso().slice(0, 10))
  const [year, setYear] = useState(String(current))
  const [income, setIncome] = useState('')
  const [expense, setExpense] = useState('')
  const [description, setDescription] = useState('')
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState<Record<string, string | undefined>>({})
  const [failure, setFailure] = useState<string | null>(null)

  const onConfirm = () => {
    const next = { income: amountError(income, BUDGET_MAX, true, 0), expense: amountError(expense, BUDGET_MAX, true, 0) }
    setErrors(next)
    setFailure(null)
    if (Object.values(next).some(Boolean)) return
    submit.mutate(
      { academicYear: Number(year), plannedIncome: parseMoney(income)!, plannedExpense: parseMoney(expense)!, description, note },
      {
        onSuccess: () => {
          toast.success(`Bütçe ${route} gönderildi`)
          onClose()
        },
        onError: (e) => setFailure(toApiError(e).message),
      },
    )
  }

  return (
    <ConfirmDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Bütçe hazırla"
      description={`Bütçe ${route} gider. Onaylanan son bütçe yılın planı olur; giderler buna göre izlenir.`}
      confirmLabel="Onaya gönder"
      loading={submit.isPending}
      onConfirm={onConfirm}
    >
      <div className="flex max-h-[60vh] flex-col gap-5 overflow-y-auto pr-1">
        <Field label="Akademik yıl" required>
          <Select value={year} onChange={(e) => setYear(e.target.value)} className="max-w-[12rem]">
            {[current, current + 1].map((y) => (
              <option key={y} value={y}>
                {yearLabel(y)}
              </option>
            ))}
          </Select>
        </Field>
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Planlanan gelir (₺)" required error={errors.income}>
            <Input inputMode="decimal" autoComplete="off" value={income} onChange={(e) => setIncome(e.target.value)} />
          </Field>
          <Field label="Planlanan gider (₺)" required error={errors.expense}>
            <Input inputMode="decimal" autoComplete="off" value={expense} onChange={(e) => setExpense(e.target.value)} />
          </Field>
        </div>
        <Field label="Açıklama" hint="İsteğe bağlı. Gelir ve giderlerin kalem kalem dökümü.">
          <Textarea
            rows={4}
            maxLength={5000}
            valueLength={description.length}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </Field>
        <Field label="Not" hint="İsteğe bağlı.">
          <Textarea rows={2} maxLength={1000} valueLength={note.length} value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
        {failure && <p className="text-sm font-semibold text-danger">{failure}</p>}
      </div>
    </ConfirmDialog>
  )
}

function SponsorshipDialog({ clubId, route, onClose }: { clubId: string; route: string; onClose: () => void }) {
  const submit = useSubmitSponsorship(clubId)
  const today = nowLocalIso().slice(0, 10)
  const [d, setD] = useState({ sponsorName: '', description: '', cash: '', inKind: '', startsOn: today, endsOn: '', note: '' })
  const [document, setDocument] = useState<File | null>(null)
  const [errors, setErrors] = useState<Record<string, string | undefined>>({})
  const [failure, setFailure] = useState<string | null>(null)
  const set = (k: keyof typeof d) => (e: { target: { value: string } }) => setD((x) => ({ ...x, [k]: e.target.value }))

  const onConfirm = () => {
    const next = {
      sponsorName: d.sponsorName.trim() ? undefined : 'Sponsorun adını yazın.',
      description: d.description.trim() ? undefined : 'Sponsorluğun kapsamını yazın.',
      cash:
        amountError(d.cash, BUDGET_MAX, false, 0) ??
        (!d.cash.trim() && !d.inKind.trim() ? 'Nakit tutar ya da ayni katkıdan en az birini yazın.' : undefined),
      startsOn: d.startsOn ? undefined : 'Başlangıç tarihini seçin.',
      endsOn: d.endsOn && d.startsOn && d.endsOn < d.startsOn ? 'Bitiş başlangıçtan önce olamaz.' : undefined,
    }
    setErrors(next)
    setFailure(null)
    if (Object.values(next).some(Boolean)) return
    submit.mutate(
      {
        sponsorName: d.sponsorName,
        description: d.description,
        cashAmount: d.cash.trim() ? parseMoney(d.cash) : null,
        inKind: d.inKind,
        startsOn: d.startsOn,
        endsOn: d.endsOn,
        note: d.note,
        document,
      },
      {
        onSuccess: () => {
          toast.success(`Sponsorluk ${route} gönderildi`)
          onClose()
        },
        onError: (e) => setFailure(toApiError(e).message),
      },
    )
  }

  return (
    <ConfirmDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Sponsorluk ekle"
      description={`Sponsorluk ${route} gider. Onaylanınca nakit tutar kulübün gelirine işlenir.`}
      confirmLabel="Onaya gönder"
      loading={submit.isPending}
      onConfirm={onConfirm}
    >
      <div className="flex max-h-[60vh] flex-col gap-5 overflow-y-auto pr-1">
        <Field label="Sponsor" required error={errors.sponsorName}>
          <Input maxLength={200} value={d.sponsorName} onChange={set('sponsorName')} />
        </Field>
        <Field label="Kapsam" required error={errors.description} hint="Ne karşılığında, hangi etkinlik ya da dönem için.">
          <Textarea rows={3} maxLength={5000} valueLength={d.description.length} value={d.description} onChange={set('description')} />
        </Field>
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Nakit tutar (₺)" error={errors.cash}>
            <Input inputMode="decimal" autoComplete="off" value={d.cash} onChange={set('cash')} />
          </Field>
          <Field label="Ayni katkı" hint="Ör. 20 satranç takımı.">
            <Input maxLength={1000} value={d.inKind} onChange={set('inKind')} />
          </Field>
        </div>
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Başlangıç" required error={errors.startsOn}>
            <Input type="date" value={d.startsOn} onChange={set('startsOn')} />
          </Field>
          <Field label="Bitiş" hint="İsteğe bağlı." error={errors.endsOn}>
            <Input type="date" min={d.startsOn || undefined} value={d.endsOn} onChange={set('endsOn')} />
          </Field>
        </div>
        <FileField
          label="Sözleşme ya da yazışma"
          hint="İsteğe bağlı. PDF, JPEG, PNG, GIF ya da WEBP, en fazla 10 MB."
          accept={DOCUMENT_TYPES.join(',')}
          maxBytes={DOCUMENT_MAX_BYTES}
          value={document}
          onChange={setDocument}
        />
        <Field label="Not" hint="İsteğe bağlı.">
          <Textarea rows={2} maxLength={1000} valueLength={d.note.length} value={d.note} onChange={set('note')} />
        </Field>
        {failure && <p className="text-sm font-semibold text-danger">{failure}</p>}
      </div>
    </ConfirmDialog>
  )
}
