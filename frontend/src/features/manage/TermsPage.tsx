import { useState } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatLocal, localStamp, nowLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { useTerms, type Term } from '@/features/courses/api'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ChoiceGroup } from '@/components/ui/ChoiceGroup'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Dialog } from '@/components/ui/Dialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import { RequireAccess } from './RequireAccess'
import {
  SEASON_LABEL,
  termLabel,
  termPhase,
  termToDraft,
  useDeleteTerm,
  useSaveTerm,
  validateTermDraft,
  type Season,
  type TermDraft,
  type TermDraftErrors,
} from './terms'

const LINK = 'text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline'
const PHASE: Record<ReturnType<typeof termPhase>, { label: string; tone: 'success' | 'info' | 'neutral' }> = {
  current: { label: 'Şu anki dönem', tone: 'success' },
  upcoming: { label: 'Gelecek', tone: 'info' },
  past: { label: 'Geçti', tone: 'neutral' },
}

/** Dönemler (yönetici, F-41, F-44): ders açmanın ve kayıt penceresinin dayandığı akademik takvim. */
export function TermsPage() {
  usePageTitle('Dönemler')
  return (
    <RequireAccess>
      <Terms />
    </RequireAccess>
  )
}

function Terms() {
  const terms = useTerms()
  const [editing, setEditing] = useState<Term | 'new' | null>(null)
  const today = nowLocalIso().slice(0, 10)
  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader
        title="Dönemler"
        meta="Dersler bir döneme açılır; kayıt penceresi tanımlıysa öğrenciler derslere yalnız o aralıkta başvurabilir."
        actions={<Button onClick={() => setEditing('new')}>Dönem ekle</Button>}
      />
      <div className="mt-8">
        <QueryBoundary query={terms} what="Dönemler">
          {(list) =>
            list.length === 0 ? (
              <EmptyState title="Tanımlı dönem yok">Akademisyenler ders açabilmek için en az bir dönem gerekir.</EmptyState>
            ) : (
              <ul className="border-t border-ink">
                {list.map((t) => (
                  <TermRow key={t.id} term={t} today={today} onEdit={() => setEditing(t)} />
                ))}
              </ul>
            )
          }
        </QueryBoundary>
      </div>
      {editing && <TermDialog term={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

/** Dönem çizgisi: bütün uzunluk dönem; ders rengi kayıt penceresi; dikey işaret bugün. */
function TermBar({ term: t, today }: { term: Term; today: string }) {
  const start = localStamp(`${t.startsOn}T00:00:00`)
  const span = localStamp(`${t.endsOn}T23:59:59`) - start
  const pct = (d: string) => Math.max(0, Math.min(100, ((localStamp(`${d}T12:00:00`) - start) / span) * 100))
  const open = t.enrollmentOpensOn ?? t.startsOn
  const close = t.enrollmentClosesOn ?? t.endsOn
  const hasWindow = !!(t.enrollmentOpensOn || t.enrollmentClosesOn)
  const inside = today >= t.startsOn && today <= t.endsOn
  return (
    <div aria-hidden className="relative mt-3 h-[3px] w-full max-w-[36rem] bg-rule">
      {hasWindow && (
        <div className="absolute inset-y-0 bg-ders" style={{ left: `${pct(open)}%`, width: `${Math.max(1, pct(close) - pct(open))}%` }} />
      )}
      {inside && <div className="absolute -top-1.5 h-[15px] w-[2px] bg-ink" style={{ left: `${pct(today)}%` }} />}
    </div>
  )
}

function TermRow({ term: t, today, onEdit }: { term: Term; today: string; onEdit: () => void }) {
  const remove = useDeleteTerm()
  const [confirm, setConfirm] = useState(false)
  const phase = PHASE[termPhase(t, today)]
  const enrollment =
    t.enrollmentOpensOn || t.enrollmentClosesOn
      ? `Ders kaydı ${t.enrollmentOpensOn ? formatLocal(t.enrollmentOpensOn, 'date') : 'dönem başından'} – ${t.enrollmentClosesOn ? formatLocal(t.enrollmentClosesOn, 'date') : 'dönem sonuna'}`
      : 'Kayıt penceresi yok; dersler dönem boyunca başvuruya açık.'
  return (
    <li className="grid gap-x-8 gap-y-3 border-b border-rule py-6 md:grid-cols-[minmax(0,1fr)_auto] md:items-start">
      <div className="min-w-0">
        <p className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
          <span className="tabular text-3xl leading-none font-heavy">{t.label}</span>
          <Badge tone={phase.tone}>{phase.label}</Badge>
        </p>
        <p className="tabular mt-2 text-md">
          {formatLocal(t.startsOn, 'date')} – {formatLocal(t.endsOn, 'date')}
        </p>
        <p className="mt-0.5 text-sm text-ink-2">{enrollment}</p>
        <TermBar term={t} today={today} />
      </div>
      <p className="flex gap-x-5">
        <button type="button" onClick={onEdit} className={LINK}>
          Düzenle<span className="sr-only">: {t.label}</span>
        </button>
        <button type="button" onClick={() => setConfirm(true)} className={`${LINK} hover:text-danger`}>
          Sil<span className="sr-only">: {t.label}</span>
        </button>
      </p>
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        destructive
        title="Dönemi sil"
        description={`${t.label} silinir. Bu döneme ders açılmışsa silinemez.`}
        confirmLabel="Sil"
        loading={remove.isPending}
        onConfirm={() =>
          remove.mutate(t.id, {
            onSuccess: () => {
              toast.success(`${t.label} silindi`)
              setConfirm(false)
            },
            onError: (e) => {
              setConfirm(false)
              toast.error(toApiError(e).message)
            },
          })
        }
      />
    </li>
  )
}

/** Akademik yıl seçenekleri: bu yıldan iki önce ile üç sonrası; düzenlenen dönemin yılı da her zaman listede. */
function yearOptions(current?: number) {
  const now = Number(nowLocalIso().slice(0, 4))
  const years = new Set<number>()
  for (let y = now - 1; y <= now + 4; y++) years.add(y)
  if (current) years.add(current)
  return [...years].sort((a, b) => a - b)
}

function TermDialog({ term, onClose }: { term?: Term; onClose: () => void }) {
  const save = useSaveTerm()
  const [d, setD] = useState<TermDraft>(
    term
      ? termToDraft(term)
      : {
          academicYear: String(Number(nowLocalIso().slice(0, 4)) + 1),
          season: 'FALL',
          startsOn: '',
          endsOn: '',
          enrollmentOpensOn: '',
          enrollmentClosesOn: '',
        },
  )
  const [errors, setErrors] = useState<TermDraftErrors & { general?: string }>({})
  const set = (k: keyof TermDraft) => (e: { target: { value: string } }) => {
    setD((x) => ({ ...x, [k]: e.target.value }))
    setErrors((x) => ({ ...x, [k]: undefined, general: undefined }))
  }

  const submit = () => {
    const next = validateTermDraft(d)
    setErrors(next)
    if (Object.keys(next).length) return
    save.mutate(
      { id: term?.id, draft: d },
      {
        onSuccess: (t) => {
          toast.success(term ? `${t.label} güncellendi` : `${t.label} eklendi`)
          onClose()
        },
        onError: (e) => {
          const ae = toApiError(e)
          if (ae.code === 'TERM_EXISTS') setErrors({ season: ae.message })
          else setErrors({ general: ae.message })
        },
      },
    )
  }

  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={term ? `${term.label} dönemini düzenle` : 'Dönem ekle'}
      description={
        term
          ? 'Bu döneme ders açıldıysa akademik yıl ve dönem türü değiştirilemez; tarihler değiştirilebilir.'
          : 'Etiket, akademik yıl ve dönem türünden oluşur.'
      }
      footer={
        <>
          {errors.general && <p className="mr-auto text-sm font-semibold text-danger">{errors.general}</p>}
          <Button variant="ghost" onClick={onClose}>
            Vazgeç
          </Button>
          <Button variant="primary" loading={save.isPending} onClick={submit}>
            {term ? 'Kaydet' : 'Dönemi ekle'}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-6">
        <div className="grid gap-5 sm:grid-cols-[12rem_minmax(0,1fr)]">
          <Field label="Akademik yıl" required error={errors.academicYear}>
            <Select value={d.academicYear} onChange={set('academicYear')}>
              {yearOptions(term?.academicYear).map((y) => (
                <option key={y} value={y}>
                  {y - 1}-{y}
                </option>
              ))}
            </Select>
          </Field>
          <ChoiceGroup
            layout="rows"
            label="Dönem"
            value={d.season}
            onChange={(season: Season) => {
              setD((x) => ({ ...x, season }))
              setErrors((x) => ({ ...x, season: undefined }))
            }}
            choices={(Object.keys(SEASON_LABEL) as Season[]).map((s) => ({ value: s, title: SEASON_LABEL[s] }))}
          />
        </div>
        {errors.season && <p className="-mt-3 text-sm font-semibold text-danger">{errors.season}</p>}
        <p className="-mt-2 text-md text-ink-2">
          Etiket: <span className="font-semibold text-ink">{termLabel(Number(d.academicYear), d.season)}</span>
        </p>
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Dönem başlangıcı" required error={errors.startsOn}>
            <Input type="date" value={d.startsOn} onChange={set('startsOn')} />
          </Field>
          <Field label="Dönem bitişi" required error={errors.endsOn}>
            <Input type="date" value={d.endsOn} onChange={set('endsOn')} />
          </Field>
        </div>
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Ders kaydı açılışı" hint="İsteğe bağlı.">
            <Input type="date" value={d.enrollmentOpensOn} onChange={set('enrollmentOpensOn')} />
          </Field>
          <Field label="Ders kaydı kapanışı" hint="İsteğe bağlı." error={errors.enrollmentClosesOn}>
            <Input type="date" value={d.enrollmentClosesOn} onChange={set('enrollmentClosesOn')} />
          </Field>
        </div>
        <p className="text-sm text-ink-3">
          Kayıt penceresi tanımlıysa öğrenciler derslere yalnız bu aralıkta başvurabilir. İkisi de boşsa dersler dönem boyunca başvuruya
          açıktır; yalnız biri doluysa diğer uç sınırsızdır.
        </p>
      </div>
    </Dialog>
  )
}
