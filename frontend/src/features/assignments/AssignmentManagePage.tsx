import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { formatNumber } from '@/lib/format'
import { formatInstant, formatLocal } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { useCourseStaff } from '@/features/courses/api'
import { abilities, assessmentsEditable, type TeachingCourse } from '@/features/courses/teach'
import { withTitle } from '@/features/people/titles'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Meter } from '@/components/ui/Meter'
import { RevealTitle } from '@/components/ui/RevealTitle'
import { EmptyState, QueryBoundary, Skeleton } from '@/components/ui/States'
import { Tabs } from '@/components/ui/Tabs'
import { AI_POLICY, TYPE_LABEL, type AiPolicy, type AssignmentType } from './api'
import { AssignmentForm } from './AssignmentForm'
import { ExtensionDialog, ExtensionsTab } from './Extensions'
import { SubmissionsTab } from './SubmissionGrading'
import {
  CHANGE_FIELD_LABEL,
  assignmentToDraft,
  columnProgress,
  remainingWeight,
  useAssignmentChanges,
  useCourseAssignments,
  useDeleteAssignment,
  useExtensions,
  useGradebook,
  useGroupSets,
  usePublishGrades,
  useUpdateAssignment,
  type AssignmentChange,
  type GroupSet,
  type StaffAssignment,
} from './staff'

/**
 * Değerlendirme yönetimi (kadro, F-45…F-48): başlık bandında teslim ve notlama; puan ilanı şeridi;
 * sekmeler Teslimler, Uzatmalar, Düzenle (koordinatör ve hoca), Değişiklikler.
 */
export function AssignmentManagePage({ teaching }: { teaching: TeachingCourse }) {
  const { assignmentId = '' } = useParams()
  const list = useCourseAssignments(teaching.id)
  const a = list.data?.find((x) => x.id === assignmentId)
  usePageTitle(a ? `${a.title} – ${teaching.code}` : 'Değerlendirme')
  const back = `/courses/${teaching.id}?sekme=odevler`

  return (
    <div className="mx-auto max-w-[72rem]">
      <p className="flex items-center gap-3 text-md">
        <span aria-hidden className="h-5 w-[4px] bg-ders" />
        <Link to={back} className="tabular font-semibold underline-offset-4 hover:underline">
          {teaching.code} {teaching.title}
        </Link>
      </p>
      <div className="mt-8">
        {list.isPending ? (
          <Skeleton rows={5} />
        ) : a ? (
          <Body assignment={a} all={list.data ?? []} course={teaching} />
        ) : (
          <EmptyState title="Değerlendirme bulunamadı">
            Silinmiş olabilir.{' '}
            <Link to={back} className="font-semibold underline-offset-4 hover:underline">
              Değerlendirmelere dön
            </Link>
          </EmptyState>
        )}
      </div>
    </div>
  )
}

function Body({ assignment: a, all, course: c }: { assignment: StaffAssignment; all: StaffAssignment[]; course: TeachingCourse }) {
  const can = abilities(c.staffRole)
  const editable = assessmentsEditable(c.status)
  const book = useGradebook(c.id)
  const extensions = useExtensions(a.id)
  const groupSets = useGroupSets(c.id, can.assignments)
  const p = columnProgress(book.data, a.id)
  const [extendFor, setExtendFor] = useState<string | null>(null)
  const canExtend = can.assignments && editable

  return (
    <>
      <Hero assignment={a} progress={p} group={groupSets.data?.find((s) => s.id === a.groupSetId)} />
      <PublishStrip assignment={a} course={c} waiting={p.waiting} />
      <div className="mt-8">
        <Tabs
          label="Değerlendirme"
          tabs={[
            {
              value: 'teslimler',
              label: 'Teslimler',
              count: book.data ? p.submitted : undefined,
              content: <SubmissionsTab assignment={a} canExtend={canExtend} onExtend={(id) => setExtendFor(id)} />,
            },
            {
              value: 'uzatmalar',
              label: 'Uzatmalar',
              count: extensions.data?.length,
              content: <ExtensionsTab assignment={a} canEdit={canExtend} onGrant={() => setExtendFor('')} />,
            },
            ...(can.assignments && editable
              ? [
                  {
                    value: 'duzenle',
                    label: 'Düzenle',
                    content: (
                      <EditTab assignment={a} all={all} groupSets={groupSets.data ?? []} hasSubmissions={p.submitted > 0} courseId={c.id} />
                    ),
                  },
                ]
              : []),
            { value: 'degisiklikler', label: 'Değişiklikler', content: <ChangesTab assignment={a} groupSets={groupSets.data ?? []} /> },
          ]}
        />
      </div>
      {extendFor !== null && <ExtensionDialog assignment={a} studentId={extendFor || undefined} onClose={() => setExtendFor(null)} />}
    </>
  )
}

function Hero({
  assignment: a,
  progress: p,
  group,
}: {
  assignment: StaffAssignment
  progress: ReturnType<typeof columnProgress>
  group?: GroupSet
}) {
  return (
    <section className="grid gap-10 border-b border-ink pb-10 lg:grid-cols-12">
      <div className="min-w-0 lg:col-span-8">
        <p className="text-lg font-heavy text-ders">{TYPE_LABEL[a.type]}</p>
        <RevealTitle text={a.title} className="mt-1 text-4xl sm:text-5xl" />
        <dl className="mt-6 grid max-w-[40rem] grid-cols-[9rem_minmax(0,1fr)] gap-y-2 text-md">
          <dt className="text-ink-3">Son teslim</dt>
          <dd className="tabular font-semibold">
            {formatLocal(a.dueDate, 'long')}, {formatLocal(a.dueDate, 'time')}
          </dd>
          {a.lateUntil && (
            <>
              <dt className="text-ink-3">Geç teslim</dt>
              <dd className="tabular">
                {formatLocal(a.lateUntil, 'long')}, {formatLocal(a.lateUntil, 'time')} kadar
                {a.latePenaltyPercent ? `, %${formatNumber(a.latePenaltyPercent)} kesinti` : ', kesintisiz'}
              </dd>
            </>
          )}
          <dt className="text-ink-3">Puanlama</dt>
          <dd className="tabular">
            {formatNumber(a.maxPoints)} puan, ağırlık %{formatNumber(a.weight)}
          </dd>
          <dt className="text-ink-3">Yapay zekâ</dt>
          <dd>{AI_POLICY[a.aiPolicy].label}</dd>
          {group && (
            <>
              <dt className="text-ink-3">Grup</dt>
              <dd>
                {group.name}, {group.groups.length} grup
              </dd>
            </>
          )}
        </dl>
      </div>
      <dl className="grid grid-cols-2 self-end border-t border-rule lg:col-span-4 lg:grid-cols-1 lg:border-t-0 lg:border-l lg:pl-8">
        <div className="py-4 lg:pt-0">
          <dd className="flex items-baseline gap-2">
            <span className="tabular text-6xl leading-none font-heavy">{p.submitted}</span>
            <span className="tabular text-xl text-ink-3">/ {p.students}</span>
          </dd>
          <dt className="mt-2 text-sm text-ink-3">{a.groupSetId ? 'öğrencinin grubu teslim etti' : 'öğrenci teslim etti'}</dt>
          <div className="mt-3 max-w-[14rem]">
            <Meter value={p.submitted} max={Math.max(1, p.students)} label={`${p.students} öğrenciden ${p.submitted} teslim`} />
          </div>
        </div>
        <div className="border-l border-rule py-4 pl-5 lg:border-t lg:border-l-0 lg:pl-0">
          <dd className="flex items-baseline gap-2">
            <span className={`tabular text-6xl leading-none font-heavy ${p.waiting ? '' : 'text-ink-3'}`}>
              {p.waiting}
            </span>
          </dd>
          <dt className="mt-2 text-sm text-ink-3">teslim notlanmayı bekliyor</dt>
        </div>
      </dl>
    </section>
  )
}

/** Puan ilanı (F-46): ilan edilene kadar öğrenciler puan ve geri bildirimi görmez; ilan geri alınmaz. */
function PublishStrip({ assignment: a, course: c, waiting }: { assignment: StaffAssignment; course: TeachingCourse; waiting: number }) {
  const publish = usePublishGrades(a)
  const [open, setOpen] = useState(false)
  const can = abilities(c.staffRole).assignments && c.status !== 'ARCHIVED'

  if (a.gradesPublishedAt) {
    return (
      <div className="mt-8 border-l-[3px] border-success py-1 pl-5">
        <p className="text-lg font-heavy">Puanlar {formatInstant(a.gradesPublishedAt, 'date')} tarihinde ilan edildi</p>
        <p className="mt-0.5 max-w-[64ch] text-md text-ink-2">
          Öğrenciler puanlarını ve geri bildirimleri görüyor. İlan edilmiş bir puanı değiştirirken gerekçe yazmanız gerekir; öğrenciye
          bildirim gider.
        </p>
      </div>
    )
  }
  return (
    <div className="mt-8 flex flex-wrap items-center justify-between gap-x-8 gap-y-4 border-l-[3px] border-ders py-1 pl-5">
      <div className="max-w-[60ch]">
        <p className="text-lg font-heavy">Puanlar henüz ilan edilmedi</p>
        <p className="mt-0.5 text-md text-ink-2">
          {can
            ? 'Puan verdikçe öğrenciler görmez; hepsini bitirince tek seferde ilan edin.'
            : 'Puan verdikçe öğrenciler görmez; ilanı koordinatör ya da hoca yapar.'}
        </p>
      </div>
      {can && (
        <Button variant="primary" onClick={() => setOpen(true)}>
          Puanları ilan et
        </Button>
      )}
      <ConfirmDialog
        open={open}
        onOpenChange={setOpen}
        title="Puanları ilan et"
        description={
          <>
            {waiting > 0 && (
              <span className="mb-2 block font-semibold text-warning">
                {waiting} teslim henüz notlanmadı; bu öğrenciler ilandan sonra notlandıklarında puanlarını görür.
              </span>
            )}
            Öğrenciler puanlarını ve geri bildirimleri görür, bildirim alır. İlan geri alınamaz; sonradan puan değiştirmek gerekçe ister.
          </>
        }
        confirmLabel="Puanları ilan et"
        loading={publish.isPending}
        onConfirm={() =>
          publish.mutate(undefined, {
            onSuccess: () => {
              toast.success('Puanlar ilan edildi; öğrencilere bildirim gitti')
              setOpen(false)
            },
            onError: (e) => {
              setOpen(false)
              toast.error(toApiError(e).message)
            },
          })
        }
      />
    </div>
  )
}

function EditTab({
  assignment: a,
  all,
  groupSets,
  hasSubmissions,
  courseId,
}: {
  assignment: StaffAssignment
  all: StaffAssignment[]
  groupSets: GroupSet[]
  hasSubmissions: boolean
  courseId: string
}) {
  const update = useUpdateAssignment(a)
  const remove = useDeleteAssignment(courseId)
  const navigate = useNavigate()
  const [failure, setFailure] = useState<ApiError | null>(null)
  const [confirm, setConfirm] = useState(false)

  return (
    <div className="flex flex-col gap-10">
      <AssignmentForm
        key={a.id}
        initial={assignmentToDraft(a)}
        isNew={false}
        remainingWeight={remainingWeight(all, a.id)}
        groupSets={groupSets}
        submitLabel="Değişiklikleri kaydet"
        busy={update.isPending}
        failure={failure}
        onSubmit={(draft) => {
          setFailure(null)
          update.mutate(draft, {
            onSuccess: (saved) =>
              toast.success(
                saved.dueDate !== a.dueDate
                  ? 'Kaydedildi; son teslim değiştiği için öğrencilere bildirim gitti'
                  : 'Değişiklikler kaydedildi',
              ),
            onError: (e) => setFailure(toApiError(e)),
          })
        }}
      />
      <div className="border-t border-rule pt-6">
        <p className="text-lg font-heavy">Değerlendirmeyi sil</p>
        {hasSubmissions ? (
          <p className="mt-1 max-w-[60ch] text-md text-ink-2">
            Teslim alınmış değerlendirme silinemez; ağırlığını 0 yaparak ortalamadan çıkarabilirsiniz.
          </p>
        ) : (
          <>
            <p className="mt-1 max-w-[60ch] text-md text-ink-2">
              Henüz teslim yok; silinen değerlendirme öğrencilerin listesinden de kalkar.
            </p>
            <button
              type="button"
              onClick={() => setConfirm(true)}
              className="mt-3 text-md font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline"
            >
              Değerlendirmeyi sil
            </button>
          </>
        )}
      </div>
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        destructive
        title="Değerlendirmeyi sil"
        description={`“${a.title}” kalıcı olarak silinir.`}
        confirmLabel="Sil"
        loading={remove.isPending}
        onConfirm={() =>
          remove.mutate(a.id, {
            onSuccess: () => {
              toast.success('Değerlendirme silindi')
              navigate(`/courses/${courseId}?sekme=odevler`)
            },
            onError: (e) => {
              setConfirm(false)
              toast.error(toApiError(e).message)
            },
          })
        }
      />
    </div>
  )
}

/** Değişiklik değerini okunur yazar: tarih, tür, yüzde, grup seti adı. */
function changeValue(c: AssignmentChange, v: string | null, groupSets: GroupSet[]) {
  if (v == null || v === '') return c.field === 'groupSet' ? 'Bireysel' : 'boş'
  switch (c.field) {
    case 'dueDate':
    case 'lateUntil':
      return formatLocal(v, 'datetime')
    case 'type':
      return TYPE_LABEL[v as AssignmentType] ?? v
    case 'aiPolicy':
      return AI_POLICY[v as AiPolicy]?.label ?? v
    case 'weight':
    case 'latePenalty':
      return `%${formatNumber(Number(v))}`
    case 'maxPoints':
      return formatNumber(Number(v))
    case 'groupSet':
      return groupSets.find((s) => s.id === v)?.name ?? 'Grup seti'
    case 'description':
      return v.length > 80 ? `${v.slice(0, 80)}…` : v
    default:
      return v
  }
}

/** Değişiklikler (F-45): alan, eski ve yeni değer, kim ve ne zaman. */
function ChangesTab({ assignment: a, groupSets }: { assignment: StaffAssignment; groupSets: GroupSet[] }) {
  const changes = useAssignmentChanges(a.id, true)
  const staff = useCourseStaff(a.courseId)
  const who = (id: string | null) => {
    const s = staff.data?.find((x) => x.userId === id)
    return s ? withTitle(s.name, s.title, s.academicTitle) : 'Kadro'
  }
  return (
    <QueryBoundary query={changes} what="Değişiklikler">
      {(list) =>
        list.length === 0 ? (
          <EmptyState title="Değişiklik yok">Değerlendirme eklendiğinden beri düzenlenmedi.</EmptyState>
        ) : (
          <ul className="divide-y divide-rule border-y border-ink">
            {[...list]
              .sort((x, y) => y.changedAt.localeCompare(x.changedAt))
              .map((c, i) => (
                <li key={i} className="grid gap-x-6 gap-y-1 py-3.5 sm:grid-cols-[9rem_11rem_minmax(0,1fr)]">
                  <span className="tabular text-sm text-ink-3">{formatInstant(c.changedAt, 'datetime')}</span>
                  <span className="font-semibold">{CHANGE_FIELD_LABEL[c.field] ?? c.field}</span>
                  <span className="min-w-0 text-md">
                    <span className="text-ink-3 line-through decoration-1">{changeValue(c, c.oldValue, groupSets)}</span>
                    <span aria-hidden> → </span>
                    <span className="sr-only">yerine </span>
                    <span className="font-semibold">{changeValue(c, c.newValue, groupSets)}</span>
                    <span className="block text-sm text-ink-3">{who(c.changedBy)}</span>
                  </span>
                </li>
              ))}
          </ul>
        )
      }
    </QueryBoundary>
  )
}
