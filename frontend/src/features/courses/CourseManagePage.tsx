import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { usePageTitle } from '@/lib/usePageTitle'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Meter } from '@/components/ui/Meter'
import { Notice } from '@/components/ui/Notice'
import { RevealTitle } from '@/components/ui/RevealTitle'
import { QueryBoundary } from '@/components/ui/States'
import { Tabs } from '@/components/ui/Tabs'
import { Textarea } from '@/components/ui/Textarea'
import {
  COURSE_STATUS_LABEL,
  COURSE_STATUS_TONE,
  STAFF_ROLE_LABEL,
  useAnnouncements,
  useCourse,
  useMaterials,
  type Course,
  type StaffRole,
} from './api'
import { useCourseAssignments, useGroupSets } from '@/features/assignments/staff'
import { CourseAssessmentsTab, GradebookTab } from './CourseAssessments'
import { CourseNotes } from './CourseNotes'
import { CourseGroupsTab } from './CourseGroups'
import { CourseAnnouncementsManage, CourseMaterialsManage } from './CourseContent'
import { CourseApplications, CourseStudents } from './CourseRoster'
import { StaffManage } from './CourseStaff'
import { abilities, useCourseLifecycle, useUpdateCourse, type TeachingCourse } from './teach'

/**
 * Ders yönetimi (kadro görünümü, F-41…F-44): başlık bandında doluluk ve bekleyen başvurular,
 * taslak/tamamlanan derste yaşam döngüsü eylemi; sekmeler kadrodaki göreve göre (F-43).
 */
export function CourseManagePage({ teaching }: { teaching: TeachingCourse }) {
  const course = useCourse(teaching.id)
  usePageTitle(`${teaching.code} ${teaching.title}`)
  return (
    <div className="mx-auto max-w-[72rem]">
      <Link to="/courses" className="text-sm font-semibold text-ders underline-offset-2 hover:underline">
        Verdiğim dersler
      </Link>
      <div className="mt-8">
        <QueryBoundary query={course} what="Ders" skeletonRows={2}>
          {(c) => <Body course={c} role={teaching.staffRole} pending={teaching.pendingApplicationCount} />}
        </QueryBoundary>
      </div>
    </div>
  )
}

function Body({ course: c, role, pending }: { course: Course; role: StaffRole; pending: number }) {
  const can = abilities(role)
  const materials = useMaterials(c.id)
  const announcements = useAnnouncements(c.id)
  const assessments = useCourseAssignments(c.id)
  const groupSets = useGroupSets(c.id)
  return (
    <>
      <Hero course={c} role={role} pending={can.applications ? pending : null} />
      <Lifecycle course={c} role={role} />
      <div className="mt-8">
        <Tabs
          label="Ders yönetimi"
          tabs={[
            { value: 'genel', label: 'Genel', content: <Overview course={c} role={role} /> },
            {
              value: 'odevler',
              label: 'Ödevler',
              count: assessments.data?.length,
              content: <CourseAssessmentsTab course={c} role={role} />,
            },
            { value: 'not-defteri', label: 'Not defteri', content: <GradebookTab course={c} /> },
            { value: 'gruplar', label: 'Gruplar', count: groupSets.data?.length, content: <CourseGroupsTab course={c} role={role} /> },
            ...(can.applications
              ? [{ value: 'basvurular', label: 'Başvurular', count: pending, content: <CourseApplications course={c} /> }]
              : []),
            { value: 'ogrenciler', label: 'Öğrenciler', count: c.enrolledStudentCount, content: <CourseStudents course={c} role={role} /> },
            {
              value: 'duyurular',
              label: 'Duyurular',
              count: announcements.data?.length,
              content: <CourseAnnouncementsManage course={c} role={role} />,
            },
            {
              value: 'materyaller',
              label: 'Materyaller',
              count: materials.data?.length,
              content: <CourseMaterialsManage course={c} role={role} />,
            },
            { value: 'ders-notlari', label: 'Ders notları', content: <CourseNotes courseId={c.id} canHide={role !== 'ASSISTANT'} /> },
          ]}
        />
      </div>
    </>
  )
}

/** Başlık bandı: dev ders kodu ve ad, göreviniz ve dönem; sağda kayıtlı öğrenci ve bekleyen başvuru. */
function Hero({ course: c, role, pending }: { course: Course; role: StaffRole; pending: number | null }) {
  const full = c.enrolledStudentCount >= c.capacity
  return (
    <section className="grid gap-10 border-b border-ink pb-10 lg:grid-cols-12">
      <div className="min-w-0 lg:col-span-8">
        <p className="flex items-center gap-4">
          <span aria-hidden className="h-14 w-[5px] bg-ders" />
          <span className="tabular text-6xl font-heavy">{c.code}</span>
        </p>
        <RevealTitle text={c.title} className="mt-1 text-4xl sm:text-5xl" />
        <p className="mt-5 flex flex-wrap items-center gap-x-5 gap-y-1 text-lg text-ink-2">
          <span className="font-semibold text-ink">{STAFF_ROLE_LABEL[role]}</span>
          {c.termLabel && <span>{c.termLabel}</span>}
          {c.section && <span>Şube {c.section}</span>}
          <span>
            {c.credit} kredi{c.ects ? `, ${c.ects} AKTS` : ''}
          </span>
          <Badge tone={COURSE_STATUS_TONE[c.status]}>{COURSE_STATUS_LABEL[c.status]}</Badge>
        </p>
      </div>
      <dl className="grid grid-cols-2 self-end border-t border-rule lg:col-span-4 lg:grid-cols-1 lg:border-t-0 lg:border-l lg:pl-8">
        <div className="py-4 lg:pt-0">
          <dd className="flex items-baseline gap-2">
            <span className="tabular text-6xl leading-none font-heavy">{c.enrolledStudentCount}</span>
            <span className="tabular text-xl text-ink-3">/ {c.capacity}</span>
          </dd>
          <dt className="mt-2 text-sm text-ink-3">{full ? 'kayıtlı öğrenci, kontenjan dolu' : 'kayıtlı öğrenci'}</dt>
          <div className="mt-3 max-w-[14rem]">
            <Meter
              value={c.enrolledStudentCount}
              max={c.capacity}
              label={`${c.capacity} kişilik kontenjanın ${c.enrolledStudentCount} kişisi dolu`}
            />
          </div>
        </div>
        {pending !== null && (
          <div className="border-l border-rule py-4 pl-5 lg:border-t lg:border-l-0 lg:pl-0">
            <dd>
              <Link to="?sekme=basvurular" className="group block">
                <span className={`tabular block text-6xl leading-none font-heavy ${pending ? '' : 'text-ink-3'}`}>{pending || '–'}</span>
                <span className="mt-2 block text-sm text-ink-3 underline-offset-4 group-hover:text-ink group-hover:underline">
                  {pending ? 'başvuru onayınızı bekliyor' : 'bekleyen başvuru yok'}
                </span>
              </Link>
            </dd>
            <dt className="sr-only">Bekleyen başvuru</dt>
          </div>
        )}
      </dl>
    </section>
  )
}

/**
 * Yaşam döngüsü şeridi (F-42): taslakta yayımla/sil, tamamlanmışta arşivle (koordinatör);
 * arşivde salt okunur bilgisi. Süren derste gösterilmez.
 */
function Lifecycle({ course: c, role }: { course: Course; role: StaffRole }) {
  const can = abilities(role)
  const lifecycle = useCourseLifecycle(c.id)
  const navigate = useNavigate()
  const [confirm, setConfirm] = useState<'publish' | 'archive' | 'delete' | null>(null)

  const run = (action: 'publish' | 'archive' | 'delete') =>
    lifecycle.mutate(action, {
      onSuccess: () => {
        setConfirm(null)
        if (action === 'delete') {
          toast.success(`${c.code} taslağı silindi`)
          navigate('/courses')
        } else toast.success(action === 'publish' ? `${c.code} yayımlandı` : `${c.code} arşivlendi`)
      },
      onError: (e) => {
        setConfirm(null)
        toast.error(toApiError(e).message)
      },
    })

  let notice: { title: string; text: string; actions?: React.ReactNode } | null = null
  if (c.status === 'DRAFT') {
    notice = {
      title: 'Bu ders taslak',
      text: can.lifecycle
        ? 'Öğrenciler katalogda göremez ve başvuramaz. Bilgileri tamamlayınca yayımlayın.'
        : 'Öğrenciler katalogda göremez. Dersi koordinatör yayımlar.',
      actions: can.lifecycle && (
        <>
          <Button variant="primary" onClick={() => setConfirm('publish')}>
            Dersi yayımla
          </Button>
          <button
            type="button"
            onClick={() => setConfirm('delete')}
            className="text-md font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline"
          >
            Taslağı sil
          </button>
        </>
      ),
    }
  } else if (c.status === 'COMPLETED') {
    notice = {
      title: 'Dönem bitti',
      text: can.lifecycle
        ? 'Ders salt okunur; duyuru yapabilirsiniz. Arşivlerseniz ders geçmiş dönemlerde kalır. Arşivlemezseniz bir süre sonra kendiliğinden arşivlenir.'
        : 'Ders salt okunur; duyuru yapılabilir. Koordinatör dersi arşivleyebilir.',
      actions: can.lifecycle && <Button onClick={() => setConfirm('archive')}>Arşivle</Button>,
    }
  } else if (c.status === 'ARCHIVED') {
    notice = { title: 'Ders arşivde', text: 'Öğrenci listesi, duyurular ve materyaller kayıt için saklanır; değiştirilemez.' }
  }
  if (!notice) return null

  return (
    <div className="mt-8 flex flex-wrap items-center justify-between gap-x-8 gap-y-4 border-l-[3px] border-ders py-1 pl-5">
      <div className="max-w-[60ch]">
        <p className="text-lg font-heavy">{notice.title}</p>
        <p className="mt-0.5 text-md text-ink-2">{notice.text}</p>
      </div>
      {notice.actions && <div className="flex flex-wrap items-center gap-x-5 gap-y-2">{notice.actions}</div>}
      <ConfirmDialog
        open={confirm === 'publish'}
        onOpenChange={(o) => !o && setConfirm(null)}
        title={`${c.code} yayımlansın mı?`}
        description="Ders katalogda görünür ve öğrenciler başvurabilir. Yayımlanan ders taslağa geri alınamaz; kontenjan ve açıklamayı sonradan değiştirebilirsiniz."
        confirmLabel="Dersi yayımla"
        loading={lifecycle.isPending}
        onConfirm={() => run('publish')}
      />
      <ConfirmDialog
        open={confirm === 'delete'}
        onOpenChange={(o) => !o && setConfirm(null)}
        destructive
        title="Taslağı sil"
        description={`${c.code} ${c.title} taslağı kalıcı olarak silinir.`}
        confirmLabel="Taslağı sil"
        loading={lifecycle.isPending}
        onConfirm={() => run('delete')}
      />
      <ConfirmDialog
        open={confirm === 'archive'}
        onOpenChange={(o) => !o && setConfirm(null)}
        title={`${c.code} arşivlensin mi?`}
        description="Ders arşive taşınır; öğrenci listesi, duyurular ve materyaller kayıt için saklanır ve artık değiştirilemez."
        confirmLabel="Arşivle"
        loading={lifecycle.isPending}
        onConfirm={() => run('archive')}
      />
    </div>
  )
}

/** Genel: ders açıklaması ve kontenjan (koordinatör düzenler), yanda kadro. */
function Overview({ course: c, role }: { course: Course; role: StaffRole }) {
  const can = abilities(role)
  const editable = can.edit && c.status !== 'COMPLETED' && c.status !== 'ARCHIVED'
  const [editing, setEditing] = useState(false)
  return (
    <div className="grid gap-12 lg:grid-cols-[minmax(0,1fr)_18rem]">
      <section aria-labelledby="ders-bilgileri">
        <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          <h2 id="ders-bilgileri" className="text-lg">
            Ders hakkında
          </h2>
          {editable && !editing && (
            <button type="button" onClick={() => setEditing(true)} className="text-md font-semibold underline-offset-4 hover:underline">
              Düzenle
            </button>
          )}
        </div>
        {editing ? (
          <EditForm course={c} onDone={() => setEditing(false)} />
        ) : (
          <>
            <p className="mt-2 max-w-[68ch] whitespace-pre-line text-ink-2">
              {c.description?.trim() ||
                (editable
                  ? 'Henüz açıklama yok. Öğrenciler dersi katalogda açıklamasıyla görür; eklemek için Düzenle’ye basın.'
                  : 'Açıklama eklenmemiş.')}
            </p>
            <dl className="mt-6 grid max-w-[30rem] grid-cols-2 gap-y-3 text-md">
              <dt className="text-ink-3">Kontenjan</dt>
              <dd className="tabular">
                {c.enrolledStudentCount} / {c.capacity}
              </dd>
              {c.termLabel && (
                <>
                  <dt className="text-ink-3">Dönem</dt>
                  <dd>{c.termLabel}</dd>
                </>
              )}
              <dt className="text-ink-3">Şube</dt>
              <dd>{c.section ?? '1'}</dd>
            </dl>
            {!can.edit && <p className="mt-6 text-sm text-ink-3">Açıklamayı ve kontenjanı dersin koordinatörü değiştirir.</p>}
          </>
        )}
      </section>
      <StaffManage course={c} role={role} />
    </div>
  )
}

function EditForm({ course: c, onDone }: { course: Course; onDone: () => void }) {
  const update = useUpdateCourse(c.id)
  const [description, setDescription] = useState(c.description ?? '')
  const [capacity, setCapacity] = useState(String(c.capacity))
  const [error, setError] = useState<{ capacity?: string; general?: string }>({})

  const save = (e: React.FormEvent) => {
    e.preventDefault()
    const n = Number(capacity)
    if (!/^\d+$/.test(capacity.trim()) || n < 1 || n > 10000) return setError({ capacity: 'Kontenjan 1 ile 10.000 arasında olmalı.' })
    if (n < c.enrolledStudentCount)
      return setError({ capacity: `Kontenjan kayıtlı öğrenci sayısının (${c.enrolledStudentCount}) altına inemez.` })
    setError({})
    update.mutate(
      { description, capacity: n },
      {
        onSuccess: () => {
          toast.success('Ders bilgileri kaydedildi')
          onDone()
        },
        onError: (err) => {
          const ae = toApiError(err)
          setError(ae.code === 'CAPACITY_BELOW_ENROLLED' ? { capacity: ae.message } : { general: ae.message })
        },
      },
    )
  }

  return (
    <form onSubmit={save} noValidate className="mt-4 flex max-w-[40rem] flex-col gap-5">
      <Field label="Açıklama" hint="Katalogda ve ders sayfasında görünür.">
        <Textarea
          rows={7}
          maxLength={5000}
          valueLength={description.length}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </Field>
      <Field label="Kontenjan" required error={error.capacity} hint={`Şu an ${c.enrolledStudentCount} öğrenci kayıtlı.`}>
        <Input inputMode="numeric" maxLength={5} value={capacity} onChange={(e) => setCapacity(e.target.value)} className="max-w-[8rem]" />
      </Field>
      {error.general && (
        <Notice variant="line" tone="danger">
          {error.general}
        </Notice>
      )}
      <div className="flex items-center gap-5">
        <Button type="submit" variant="primary" loading={update.isPending}>
          Kaydet
        </Button>
        <button
          type="button"
          onClick={onDone}
          className="text-md font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
        >
          Vazgeç
        </button>
      </div>
    </form>
  )
}
