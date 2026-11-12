import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatInstant, formatLocal, nowLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Panel } from '@/components/ui/Panel'
import { Select } from '@/components/ui/Select'
import { EmptyState, PageHeader, QueryBoundary, Skeleton } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import {
  ENDED_STAFF,
  ENDED_STUDENT,
  ROLE_LABEL,
  STAFF_STATUS_LABEL,
  STUDENT_STATUS_LABEL,
  accountErrorMessage,
  useAccountAction,
  useAccountSearch,
  useAffiliations,
  type ManagedAccount,
  type StaffStatus,
  type StudentStatus,
} from './accounts'
import { RequireAccess } from './RequireAccess'

const roles = (a: ManagedAccount) => a.roles.map((r) => ROLE_LABEL[r] ?? r).join(', ')
const nameOf = (a: ManagedAccount) => [a.firstName, a.lastName].filter(Boolean).join(' ')
const fail = (e: unknown) => {
  const ae = toApiError(e)
  return accountErrorMessage(ae.code, ae.message)
}

/** Hesaplar (PERM_ACCOUNT_MANAGER, F-55, F-56, F-59): ara, seç; durum, askı ve e-posta işlemleri sağda. */
export function AccountsPage() {
  usePageTitle('Hesaplar')
  return (
    <RequireAccess perms={['PERM_ACCOUNT_MANAGER']}>
      <Accounts />
    </RequireAccess>
  )
}

function Accounts() {
  const [params, setParams] = useSearchParams()
  const [q, setQ] = useState(params.get('q') ?? '')
  const [query, setQuery] = useState(q)
  // Aramayı yazma bitince çalıştır ve adres çubuğuna yaz: bağlantı paylaşılabilir, geri tuşu çalışır.
  useEffect(() => {
    const t = setTimeout(() => {
      setQuery(q)
      setParams(
        (p) => {
          const next = new URLSearchParams(p)
          if (q.trim()) next.set('q', q.trim())
          else next.delete('q')
          return next
        },
        { replace: true },
      )
    }, 350)
    return () => clearTimeout(t)
  }, [q, setParams])
  const search = useAccountSearch(query)
  const selectedId = params.get('hesap')
  const select = (id: string) =>
    setParams((p) => {
      const next = new URLSearchParams(p)
      next.set('hesap', id)
      return next
    })
  const selected = search.data?.find((a) => a.id === selectedId)

  return (
    <div className="mx-auto max-w-[80rem]">
      <PageHeader
        title="Hesaplar"
        meta="Öğrenci ve akademisyen hesaplarının durumunu, askıya alınmasını ve e-posta adresini buradan yönetirsiniz."
      />
      <div className="mt-8 max-w-[28rem]">
        <Field label="Hesap ara" hint="Öğrenci numarası ya da e-posta; en az 3 karakter.">
          <Input type="search" autoFocus autoComplete="off" value={q} onChange={(e) => setQ(e.target.value)} />
        </Field>
      </div>

      <div className="mt-10 grid gap-14 lg:grid-cols-12 lg:gap-10">
        <section aria-label="Sonuçlar" className="min-w-0 lg:col-span-5">
          {query.trim().length < 3 ? (
            <p className="text-md text-ink-2">Aramak için en az 3 karakter yazın. Yönetici ve görevli hesapları burada listelenmez.</p>
          ) : (
            <QueryBoundary query={search} what="Hesaplar">
              {(list) =>
                list.length === 0 ? (
                  <EmptyState title="Hesap bulunamadı">Öğrenci numarasını tam, e-postayı ise bir kısmıyla arayabilirsiniz.</EmptyState>
                ) : (
                  <ul className="border-t border-ink">
                    {list.map((a) => (
                      <li key={a.id} className="border-b border-rule">
                        <button
                          type="button"
                          aria-current={a.id === selectedId ? 'true' : undefined}
                          onClick={() => select(a.id)}
                          className="row-fill grid w-full gap-1 px-2 py-3.5 text-left aria-[current=true]:bg-sunken aria-[current=true]:shadow-[inset_3px_0_0_var(--ec-yonetim)]"
                        >
                          <span className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                            <span className="min-w-0 truncate font-semibold">{nameOf(a) || a.email}</span>
                            {a.status === 'SUSPENDED' && <Badge tone="warning">Askıda</Badge>}
                          </span>
                          <span className="flex flex-wrap gap-x-3 text-sm text-ink-3">
                            {nameOf(a) && <span>{a.email}</span>}
                            <span>{roles(a)}</span>
                            {a.studentNumber && <span className="tabular">{a.studentNumber}</span>}
                          </span>
                          {a.closureDueAt && (
                            <span className="text-sm font-semibold text-danger">
                              {formatInstant(a.closureDueAt, 'date')} tarihinde kapanacak
                            </span>
                          )}
                        </button>
                      </li>
                    ))}
                  </ul>
                )
              }
            </QueryBoundary>
          )}
        </section>
        <section aria-label="Seçili hesap" className="min-w-0 lg:col-span-7">
          {selected ? (
            <AccountDetail key={selected.id} account={selected} />
          ) : (
            <p className="border-l-[3px] border-rule pl-4 text-md text-ink-2">İşlem yapmak için soldaki listeden bir hesap seçin.</p>
          )}
        </section>
      </div>
    </div>
  )
}

function AccountDetail({ account: a }: { account: ManagedAccount }) {
  const affiliations = useAffiliations(a.id)
  return (
    <div className="flex flex-col gap-14">
      <div>
        <h2 className="text-3xl font-heavy break-all">{nameOf(a) || a.email}</h2>
        <dl className="mt-4 grid max-w-[36rem] grid-cols-[9rem_minmax(0,1fr)] gap-y-2 text-md">
          {nameOf(a) && (
            <>
              <dt className="text-ink-3">E-posta</dt>
              <dd className="break-all">{a.email}</dd>
            </>
          )}
          <dt className="text-ink-3">Hesap</dt>
          <dd>
            {roles(a)}, {a.status === 'SUSPENDED' ? <span className="font-semibold text-warning">askıda</span> : 'etkin'}
          </dd>
          {a.studentNumber && (
            <>
              <dt className="text-ink-3">Öğrenci no</dt>
              <dd className="tabular">{a.studentNumber}</dd>
            </>
          )}
          {a.studentStatus && (
            <>
              <dt className="text-ink-3">Öğrencilik</dt>
              <dd>{STUDENT_STATUS_LABEL[a.studentStatus]}</dd>
            </>
          )}
          {a.staffStatus && (
            <>
              <dt className="text-ink-3">Personellik</dt>
              <dd>{STAFF_STATUS_LABEL[a.staffStatus]}</dd>
            </>
          )}
          {a.closureDueAt && (
            <>
              <dt className="text-ink-3">Kapanış</dt>
              <dd className="font-semibold text-danger">{formatInstant(a.closureDueAt, 'date')} tarihinde kapanacak</dd>
            </>
          )}
        </dl>
      </div>

      {a.studentStatus && <StatusForm account={a} kind="studentStatus" current={a.studentStatus} />}
      {a.staffStatus && <StatusForm account={a} kind="staffStatus" current={a.staffStatus} />}
      <EmailForm account={a} />
      <SuspendSection account={a} />

      <Panel title="Durum geçmişi">
        {affiliations.isPending ? (
          <Skeleton rows={2} />
        ) : (affiliations.data?.history.length ?? 0) === 0 ? (
          <p className="text-md text-ink-2">Durum değişikliği kaydı yok.</p>
        ) : (
          <ul className="border-t border-rule">
            {[...affiliations.data!.history]
              .sort((x, y) => y.changedAt.localeCompare(x.changedAt))
              .map((h, i) => {
                const label = (s: string | null) =>
                  !s
                    ? '–'
                    : h.affiliation === 'STUDENT'
                      ? (STUDENT_STATUS_LABEL[s as StudentStatus] ?? s)
                      : (STAFF_STATUS_LABEL[s as StaffStatus] ?? s)
                return (
                  <li key={i} className="grid gap-x-6 gap-y-1 border-b border-rule py-3 sm:grid-cols-[8rem_minmax(0,1fr)]">
                    <span className="tabular text-sm text-ink-3">{formatLocal(h.effectiveDate, 'date')}</span>
                    <span className="min-w-0 text-md">
                      <span className="text-ink-3">{h.affiliation === 'STUDENT' ? 'Öğrencilik: ' : 'Personellik: '}</span>
                      {h.previousStatus && <span className="text-ink-3">{label(h.previousStatus)} → </span>}
                      <span className="font-semibold">{label(h.status)}</span>
                      {h.reason && <span className="mt-0.5 block text-ink-2">{h.reason}</span>}
                    </span>
                  </li>
                )
              })}
          </ul>
        )}
      </Panel>
    </div>
  )
}

/** Öğrencilik ya da personellik durumu: sona erdiren durumlar onay ister (F-56). */
function StatusForm({ account: a, kind, current }: { account: ManagedAccount; kind: 'studentStatus' | 'staffStatus'; current: string }) {
  const act = useAccountAction(a.id)
  const student = kind === 'studentStatus'
  const labels: Record<string, string> = student ? STUDENT_STATUS_LABEL : STAFF_STATUS_LABEL
  const ended: string[] = student ? ENDED_STUDENT : ENDED_STAFF
  const [status, setStatus] = useState(current)
  const [date, setDate] = useState(nowLocalIso().slice(0, 10))
  const [reason, setReason] = useState('')
  const [confirm, setConfirm] = useState(false)
  const otherActive = student
    ? !!a.staffStatus && !ENDED_STAFF.includes(a.staffStatus)
    : !!a.studentStatus && !ENDED_STUDENT.includes(a.studentStatus)

  const save = () =>
    act.mutate(
      { kind, status, effectiveDate: date, reason },
      {
        onSuccess: () => {
          toast.success(`${student ? 'Öğrencilik' : 'Personellik'} durumu: ${labels[status]}`)
          setConfirm(false)
          setReason('')
        },
        onError: (e) => {
          setConfirm(false)
          toast.error(fail(e))
        },
      },
    )

  return (
    <Panel title={student ? 'Öğrencilik durumu' : 'Personellik durumu'}>
      <form
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          if (status === current) return toast.error('Hesap zaten bu durumda.')
          if (ended.includes(status)) setConfirm(true)
          else save()
        }}
        className="flex max-w-[36rem] flex-col gap-5"
      >
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Durum">
            <Select value={status} onChange={(e) => setStatus(e.target.value)}>
              {Object.entries(labels).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Geçerlilik tarihi">
            <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </Field>
        </div>
        <Field label="Gerekçe" hint="İsteğe bağlı; durum geçmişinde görünür. Ör. yönetim kurulu kararı tarihi ve sayısı.">
          <Textarea rows={2} maxLength={1000} valueLength={reason.length} value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <div>
          <Button type="submit" loading={act.isPending && !confirm} disabled={status === current}>
            Durumu kaydet
          </Button>
        </div>
      </form>
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        destructive
        title={`${labels[status]} olarak kaydet`}
        description={
          otherActive
            ? `${student ? 'Öğrencilik' : 'Personellik'} sona erer; kişinin diğer kaydı sürdüğü için hesap açık kalır. Bu işlem geri alınamaz.`
            : `${student ? 'Öğrencilik' : 'Personellik'} sona erer. Başka kaydı olmadığı için hesap kapanış sürecine girer. Bu işlem geri alınamaz.`
        }
        confirmLabel="Kaydet"
        loading={act.isPending}
        onConfirm={save}
      />
    </Panel>
  )
}

/** E-posta değişikliği (F-55): giriş adresini yalnız hesap yöneticisi değiştirir; oturumlar kapanır. */
function EmailForm({ account: a }: { account: ManagedAccount }) {
  const act = useAccountAction(a.id)
  const [email, setEmail] = useState('')
  const [reason, setReason] = useState('')
  const [confirm, setConfirm] = useState(false)
  const [error, setError] = useState<string | null>(null)
  return (
    <Panel title="E-posta adresi">
      <form
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          if (!/^\S+@\S+\.\S+$/.test(email.trim())) return setError('Geçerli bir e-posta adresi yazın.')
          setError(null)
          setConfirm(true)
        }}
        className="flex max-w-[36rem] flex-col gap-5"
      >
        <p className="text-md text-ink-2">
          Şu anki adres: <span className="font-semibold break-all text-ink">{a.email}</span>
        </p>
        <Field label="Yeni adres" error={error ?? undefined} hint="Kurum alan adlarından biri olmalı.">
          <Input type="email" autoComplete="off" maxLength={255} value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Gerekçe" hint="İsteğe bağlı; denetim kaydına yazılır.">
          <Input maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <div>
          <Button type="submit" disabled={!email.trim()}>
            Adresi değiştir
          </Button>
        </div>
      </form>
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        title="E-posta adresini değiştir"
        description={`Giriş adresi ${email.trim()} olur. Kişinin açık oturumları kapanır ve eski adrese bilgilendirme gider.`}
        confirmLabel="Değiştir"
        loading={act.isPending}
        onConfirm={() =>
          act.mutate(
            { kind: 'email', newEmail: email, reason },
            {
              onSuccess: () => {
                toast.success('E-posta adresi değişti; oturumlar kapandı')
                setConfirm(false)
                setEmail('')
                setReason('')
              },
              onError: (e) => {
                setConfirm(false)
                setError(fail(e))
              },
            },
          )
        }
      />
    </Panel>
  )
}

function SuspendSection({ account: a }: { account: ManagedAccount }) {
  const act = useAccountAction(a.id)
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const suspended = a.status === 'SUSPENDED'
  const run = () =>
    act.mutate(suspended ? { kind: 'reactivate' } : { kind: 'suspend', reason }, {
      onSuccess: () => {
        toast.success(suspended ? 'Hesap yeniden etkin' : 'Hesap askıya alındı')
        setOpen(false)
        setReason('')
      },
      onError: (e) => {
        setOpen(false)
        toast.error(fail(e))
      },
    })
  return (
    <Panel title={suspended ? 'Askıdaki hesap' : 'Askıya alma'}>
      <p className="max-w-[60ch] text-md text-ink-2">
        {suspended
          ? 'Hesap askıda; kişi giriş yapamaz. Yeniden etkinleştirince kişi giriş yapabilir.'
          : 'Askıya alınan hesapla giriş yapılamaz ve açık oturumlar kapanır. Kayıtlar ve geçmiş korunur; istediğiniz zaman yeniden etkinleştirebilirsiniz.'}
      </p>
      <div className="mt-4">
        <Button
          variant={suspended ? 'primary' : 'secondary'}
          onClick={() => setOpen(true)}
          className={suspended ? undefined : 'text-danger'}
        >
          {suspended ? 'Yeniden etkinleştir' : 'Hesabı askıya al'}
        </Button>
      </div>
      <ConfirmDialog
        open={open}
        onOpenChange={setOpen}
        destructive={!suspended}
        title={suspended ? 'Hesabı yeniden etkinleştir' : 'Hesabı askıya al'}
        description={suspended ? `${a.email} yeniden giriş yapabilir.` : `${a.email} giriş yapamaz; açık oturumları kapanır.`}
        confirmLabel={suspended ? 'Etkinleştir' : 'Askıya al'}
        loading={act.isPending}
        onConfirm={run}
      >
        {!suspended && (
          <Field label="Gerekçe" hint="İsteğe bağlı; denetim kaydına yazılır.">
            <Textarea rows={2} maxLength={500} valueLength={reason.length} value={reason} onChange={(e) => setReason(e.target.value)} />
          </Field>
        )}
      </ConfirmDialog>
    </Panel>
  )
}
