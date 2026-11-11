import { useState } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatInstant, formatRelative } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { useCatalog } from '@/features/auth/register/api'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import { Tabs } from '@/components/ui/Tabs'
import { Textarea } from '@/components/ui/Textarea'
import { RequireAccess } from './RequireAccess'
import { catalogIndex, useChangeRequests, useDecideChange, verifyErrorMessage, type ChangeRequest, type ChangeStatus } from './verification'

/**
 * Profil talepleri (F-55, F-58): resmî alan değişikliği (ad, soyad, unvan, program, bölüm) gerekçeyle istenir;
 * doğrulayıcı onaylayınca profil güncellenir. Akademisyen talepleri personel doğrulayıcısına düşer.
 */
export function ChangeRequestsPage() {
  usePageTitle('Profil talepleri')
  return (
    <RequireAccess perms={['PERM_STUDENT_VERIFIER', 'PERM_STAFF_VERIFIER']}>
      <ChangeRequests />
    </RequireAccess>
  )
}

function ChangeRequests() {
  const pending = useChangeRequests('PENDING')
  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader
        title="Profil talepleri"
        meta={
          pending.data
            ? pending.data.length
              ? `${pending.data.length} talep kararınızı bekliyor.`
              : 'Kararınızı bekleyen talep yok.'
            : undefined
        }
      />
      <div className="mt-8">
        <Tabs
          label="Talepler"
          tabs={[
            { value: 'bekleyen', label: 'Bekleyenler', count: pending.data?.length, content: <RequestList status="PENDING" /> },
            { value: 'onaylanan', label: 'Onaylananlar', content: <RequestList status="APPROVED" /> },
            { value: 'reddedilen', label: 'Reddedilenler', content: <RequestList status="REJECTED" /> },
          ]}
        />
      </div>
    </div>
  )
}

function RequestList({ status }: { status: ChangeStatus }) {
  const list = useChangeRequests(status)
  return (
    <QueryBoundary query={list} what="Talepler">
      {(items) =>
        items.length === 0 ? (
          <EmptyState title={status === 'PENDING' ? 'Bekleyen talep yok' : 'Kayıt yok'}>
            {status === 'PENDING'
              ? 'Öğrenciler ve akademisyenler profil bilgisi değişikliği istediğinde burada listelenir.'
              : 'Sonuçlanan talepler burada listelenir.'}
          </EmptyState>
        ) : (
          <ul className="border-t border-ink">
            {[...items]
              .sort((a, b) =>
                status === 'PENDING' ? a.createdAt.localeCompare(b.createdAt) : (b.reviewedAt ?? '').localeCompare(a.reviewedAt ?? ''),
              )
              .map((r) => (
                <RequestRow key={r.id} request={r} />
              ))}
          </ul>
        )
      }
    </QueryBoundary>
  )
}

function RequestRow({ request: r }: { request: ChangeRequest }) {
  const catalog = useCatalog()
  const index = catalogIndex(catalog.data)
  const decide = useDecideChange()
  const [rejecting, setRejecting] = useState(false)
  const [note, setNote] = useState('')
  const pending = r.status === 'PENDING'
  const fail = (e: unknown) => {
    const ae = toApiError(e)
    toast.error(verifyErrorMessage(ae.code, ae.message))
  }

  const changes: [string, string][] = []
  if (r.firstName) changes.push(['Ad', r.firstName])
  if (r.lastName) changes.push(['Soyad', r.lastName])
  if (r.titleLabel || r.academicTitle) changes.push(['Unvan', r.titleLabel ?? r.academicTitle!])
  if (r.programId) changes.push(['Program', index.program(r.programId) ?? 'Katalogda bulunamadı'])
  if (r.departmentId) changes.push(['Bölüm', index.department(r.departmentId) ?? 'Katalogda bulunamadı'])

  return (
    <li className="grid gap-x-8 gap-y-4 border-b border-rule py-5 md:grid-cols-[minmax(0,1fr)_auto] md:items-start">
      <div className="min-w-0">
        <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <span className="text-lg font-semibold">{r.currentName ?? 'Ad yok'}</span>
          {r.status !== 'PENDING' && (
            <Badge tone={r.status === 'APPROVED' ? 'success' : 'danger'}>{r.status === 'APPROVED' ? 'Onaylandı' : 'Reddedildi'}</Badge>
          )}
        </p>
        <p className="mt-0.5 text-sm text-ink-3">
          {formatRelative(r.createdAt)} istendi
          {r.reviewedAt && `, ${formatInstant(r.reviewedAt, 'date')} sonuçlandı`}
        </p>
        <dl className="mt-3 grid max-w-[40rem] grid-cols-[7rem_minmax(0,1fr)] gap-y-1.5 text-md">
          {changes.map(([k, v]) => (
            <div key={k} className="contents">
              <dt className="text-ink-3">Yeni {k.toLocaleLowerCase('tr-TR')}</dt>
              <dd className="font-semibold">{v}</dd>
            </div>
          ))}
        </dl>
        <p className="mt-3 max-w-[68ch] border-l-[3px] border-rule pl-4 text-md text-ink-2">{r.reason}</p>
        {r.reviewNote && (
          <p className="mt-2 max-w-[68ch] text-sm text-ink-2">
            <span className="font-semibold text-ink">Karar notu: </span>
            {r.reviewNote}
          </p>
        )}
      </div>
      {pending && (
        <div className="flex items-center gap-3">
          <Button
            size="sm"
            variant="primary"
            loading={decide.isPending && decide.variables?.approve === true}
            disabled={decide.isPending}
            onClick={() =>
              decide.mutate(
                { id: r.id, approve: true },
                { onSuccess: () => toast.success(`${r.currentName ?? 'Profil'} güncellendi`), onError: fail },
              )
            }
          >
            Onayla<span className="sr-only">: {r.currentName}</span>
          </Button>
          <Button size="sm" disabled={decide.isPending} onClick={() => setRejecting(true)}>
            Reddet<span className="sr-only">: {r.currentName}</span>
          </Button>
        </div>
      )}
      <ConfirmDialog
        open={rejecting}
        onOpenChange={setRejecting}
        destructive
        title="Talebi reddet"
        description={`${r.currentName ?? 'Kişinin'} profil bilgileri değişmez; kişi talebinin sonucunu profil ayarlarında görür.`}
        confirmLabel="Reddet"
        loading={decide.isPending}
        onConfirm={() =>
          decide.mutate(
            { id: r.id, approve: false, note },
            {
              onSuccess: () => {
                toast.success('Talep reddedildi')
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
        <Field label="Not" hint="İsteğe bağlı; kişi görür. Ör. nüfus kaydındaki adla eşleşmiyor.">
          <Textarea rows={3} maxLength={1000} valueLength={note.length} value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
      </ConfirmDialog>
    </li>
  )
}
