import { useMemo, useState } from 'react'
import type { UseQueryResult } from '@tanstack/react-query'
import { ExternalLink } from 'lucide-react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatRelative } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { useCatalog } from '@/features/auth/register/api'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import { RequireAccess } from './RequireAccess'
import {
  catalogIndex,
  useAcademicianRequests,
  useDecideRegistration,
  useStudentRequests,
  verifyErrorMessage,
  type AcademicianRequest,
  type StudentRequest,
} from './verification'

/** Listede ortak satır modeli: öğrenci ve akademisyen başvurusu aynı düzende. */
type Row = {
  key: string
  id: string | number
  name: string
  lines: string[]
  detail: string | null
  documentUrl: string | null
  documentLabel: string
  emailVerified: boolean
  additional: boolean
  createdAt: string | null
}

const fullName = (r: { firstName: string | null; lastName: string | null }) =>
  [r.firstName, r.lastName].filter(Boolean).join(' ') || 'Ad yok'

/** Öğrenci başvuruları (PERM_STUDENT_VERIFIER): fakülte kapsamıyla; belge, program ve e-posta doğrulaması. */
export function StudentRequestsPage() {
  usePageTitle('Öğrenci başvuruları')
  return (
    <RequireAccess perms={['PERM_STUDENT_VERIFIER']}>
      <StudentRequests />
    </RequireAccess>
  )
}

function StudentRequests() {
  const list = useStudentRequests()
  const catalog = useCatalog()
  const rows = useMemo(() => {
    const index = catalogIndex(catalog.data)
    return (list.data ?? []).map((r: StudentRequest): Row => ({
      key: String(r.id),
      id: r.id,
      name: fullName(r),
      lines: [r.studentNumber, r.email].filter((x): x is string => !!x),
      detail: index.program(r.programId) ?? r.department,
      documentUrl: r.studentDocumentUrl,
      documentLabel: 'Öğrenci belgesi',
      emailVerified: r.emailVerified,
      additional: r.additionalAffiliation,
      createdAt: r.createdAt ?? null,
    }))
  }, [list.data, catalog.data])
  return <RequestsView kind="student" title="Öğrenci başvuruları" query={list} rows={rows} />
}

/** Akademisyen başvuruları (PERM_STAFF_VERIFIER): unvan, bölüm, ofis ve personel kimlik kartı. */
export function AcademicianRequestsPage() {
  usePageTitle('Akademisyen başvuruları')
  return (
    <RequireAccess perms={['PERM_STAFF_VERIFIER']}>
      <AcademicianRequests />
    </RequireAccess>
  )
}

function AcademicianRequests() {
  const list = useAcademicianRequests()
  const rows = useMemo(
    () =>
      (list.data ?? []).map((r: AcademicianRequest): Row => ({
        key: r.userId,
        id: r.userId,
        name: [r.title, fullName(r)].filter(Boolean).join(' '),
        lines: [r.officeNumber ? `Ofis ${r.officeNumber}` : null].filter((x): x is string => !!x),
        detail: r.department,
        documentUrl: r.idCardImageUrl,
        documentLabel: 'Personel kimlik kartı',
        emailVerified: r.emailVerified,
        additional: r.additionalAffiliation,
        createdAt: r.createdAt ?? null,
      })),
    [list.data],
  )
  return <RequestsView kind="academician" title="Akademisyen başvuruları" query={list} rows={rows} />
}

/** Sayfa özeti: onaylanabilir başvurular ile e-posta doğrulaması bekleyenler ayrı söylenir. */
function summary(total: number, ready: number) {
  if (total === 0) return 'Doğrulamanızı bekleyen başvuru yok.'
  if (ready === total) return `${total} başvuru doğrulamanızı bekliyor.`
  if (ready === 0) return `${total} başvuru var; sahipleri e-posta adreslerini doğrulayınca onaylayabilirsiniz.`
  return `${ready} başvuru doğrulamanızı bekliyor; ${total - ready} başvuru e-posta doğrulamasını bekliyor.`
}

function RequestsView({
  kind,
  title,
  query,
  rows,
}: {
  kind: 'student' | 'academician'
  title: string
  query: UseQueryResult<unknown[]>
  rows: Row[]
}) {
  const [q, setQ] = useState('')
  const needle = q.trim().toLocaleLowerCase('tr-TR')
  const shown = needle ? rows.filter((r) => [r.name, ...r.lines].join(' ').toLocaleLowerCase('tr-TR').includes(needle)) : rows
  const waiting = rows.filter((r) => r.emailVerified).length

  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader title={title} meta={query.data ? summary(rows.length, waiting) : undefined} />
      <p className="mt-3 max-w-[68ch] text-md text-ink-2">
        {kind === 'student'
          ? 'Belgedeki ad, öğrenci numarası ve programın başvuruyla eşleştiğini kontrol edin. Yalnız yetkili olduğunuz fakültelerin başvuruları listelenir.'
          : 'Kimlik kartındaki ad ve unvanın başvuruyla eşleştiğini kontrol edin.'}{' '}
        Belge bağlantıları 15 dakika geçerlidir; sayfa açıkken kendiliğinden yenilenir.
      </p>

      <div className="mt-8">
        <QueryBoundary query={query} what="Başvurular">
          {() =>
            rows.length === 0 ? (
              <EmptyState title="Bekleyen başvuru yok">Yeni başvurular geldiğinde burada listelenir.</EmptyState>
            ) : (
              <>
                {rows.length > 6 && (
                  <label className="mb-5 block max-w-[22rem]">
                    <span className="sr-only">Ad, numara ya da e-posta ile ara</span>
                    <Input type="search" placeholder="Ad, numara ya da e-posta" value={q} onChange={(e) => setQ(e.target.value)} />
                  </label>
                )}
                {shown.length === 0 ? (
                  <p className="text-md text-ink-2">“{q}” ile eşleşen başvuru yok.</p>
                ) : (
                  <ul className="border-t border-ink">
                    {shown.map((r) => (
                      <RequestRow key={r.key} kind={kind} row={r} />
                    ))}
                  </ul>
                )}
              </>
            )
          }
        </QueryBoundary>
      </div>
    </div>
  )
}

function RequestRow({ kind, row: r }: { kind: 'student' | 'academician'; row: Row }) {
  const decide = useDecideRegistration()
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const fail = (e: unknown) => {
    const ae = toApiError(e)
    toast.error(verifyErrorMessage(ae.code, ae.message))
  }
  const busy = decide.isPending && decide.variables?.id === r.id

  return (
    <li className="grid gap-x-8 gap-y-3 border-b border-rule py-5 md:grid-cols-[minmax(0,1fr)_14rem_auto] md:items-center">
      <div className="min-w-0">
        <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <span className="text-lg font-semibold">{r.name}</span>
          {r.additional && <Badge tone="info">Mevcut hesaba ek</Badge>}
          {!r.emailVerified && <Badge tone="warning">E-posta doğrulanmadı</Badge>}
        </p>
        {(r.lines.length > 0 || r.createdAt) && (
          <p className="mt-0.5 flex flex-wrap gap-x-4 text-sm text-ink-3">
            {r.lines.map((l) => (
              <span key={l} className="tabular">
                {l}
              </span>
            ))}
            {r.createdAt && <span>{formatRelative(r.createdAt)} başvurdu</span>}
          </p>
        )}
        {r.detail && <p className="mt-1 text-md text-ink-2">{r.detail}</p>}
      </div>
      <div>
        {r.documentUrl ? (
          <a
            href={r.documentUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 text-md font-semibold underline-offset-4 hover:underline"
          >
            <ExternalLink className="size-4" aria-hidden />
            {r.documentLabel}
            <span className="sr-only"> (yeni sekmede)</span>
          </a>
        ) : (
          <span className="text-md text-ink-3">Belge yüklenmemiş</span>
        )}
      </div>
      <div className="flex flex-col items-start gap-1.5 md:items-end">
        <div className="flex items-center gap-3">
          <Button
            size="sm"
            variant="primary"
            disabled={!r.emailVerified || busy}
            loading={busy && decide.variables?.approve === true}
            onClick={() =>
              decide.mutate(
                { kind, id: r.id, approve: true },
                {
                  onSuccess: () =>
                    toast.success(r.additional ? `${r.name}: kayıt mevcut hesaba eklendi` : `${r.name} onaylandı; hesap açıldı`),
                  onError: fail,
                },
              )
            }
          >
            Onayla<span className="sr-only">: {r.name}</span>
          </Button>
          <Button size="sm" disabled={busy} onClick={() => setRejecting(true)}>
            Reddet<span className="sr-only">: {r.name}</span>
          </Button>
        </div>
        {!r.emailVerified && <span className="text-sm text-ink-3">E-posta doğrulanınca onaylanabilir.</span>}
      </div>
      <ConfirmDialog
        open={rejecting}
        onOpenChange={setRejecting}
        destructive
        title="Başvuruyu reddet"
        description={kind === 'student' ? `${r.name} başvurusu reddedilir ve yüklenen belge silinir.` : `${r.name} başvurusu reddedilir.`}
        confirmLabel="Reddet"
        loading={busy}
        onConfirm={() =>
          decide.mutate(
            { kind, id: r.id, approve: false, reason },
            {
              onSuccess: () => {
                toast.success('Başvuru reddedildi')
                setRejecting(false)
              },
              onError: (e) => {
                setRejecting(false)
                fail(e)
              },
            },
          )
        }
      >
        <Field label="Gerekçe" hint="İsteğe bağlı; ör. belge okunmuyor, numara eşleşmiyor.">
          <Textarea rows={3} maxLength={500} valueLength={reason.length} value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
      </ConfirmDialog>
    </li>
  )
}
