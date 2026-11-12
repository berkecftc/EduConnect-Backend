import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { usePageTitle } from '@/lib/usePageTitle'
import { assessmentsEditable, abilities, useTeachingCourses } from '@/features/courses/teach'
import { EmptyState, PageHeader, Skeleton } from '@/components/ui/States'
import { AssignmentForm } from './AssignmentForm'
import { EMPTY_ASSIGNMENT, remainingWeight, useCourseAssignments, useCreateAssignment, useGroupSets } from './staff'

/** Değerlendirme ekle (F-45): koordinatör ve hoca; kaydı açık derste. Kayıtlı öğrencilere bildirim gider. */
export function AssignmentCreatePage() {
  usePageTitle('Değerlendirme ekle')
  const { courseId = '' } = useParams()
  const navigate = useNavigate()
  const teaching = useTeachingCourses()
  const existing = useCourseAssignments(courseId)
  const groupSets = useGroupSets(courseId)
  const create = useCreateAssignment(courseId)
  const [failure, setFailure] = useState<ApiError | null>(null)

  if (teaching.isPending || existing.isPending || groupSets.isPending) return <Skeleton rows={6} />
  const course = teaching.data?.find((c) => c.id === courseId)
  if (!course || !abilities(course.staffRole).assignments || !assessmentsEditable(course.status)) {
    return (
      <div className="mx-auto max-w-[56rem]">
        <EmptyState title="Bu derse değerlendirme ekleyemezsiniz">
          {!course
            ? 'Değerlendirme yalnız dersin kadrosu tarafından eklenir.'
            : !abilities(course.staffRole).assignments
              ? 'Değerlendirmeleri dersin koordinatörü ve hocaları ekler; asistanlar teslimleri puanlar.'
              : 'Tamamlanmış ya da arşivlenmiş derse değerlendirme eklenemez.'}{' '}
          <Link to={`/courses/${courseId}?sekme=odevler`} className="font-semibold underline-offset-4 hover:underline">
            Derse dön
          </Link>
        </EmptyState>
      </div>
    )
  }
  const back = `/courses/${courseId}?sekme=odevler`

  return (
    <div className="mx-auto max-w-[72rem]">
      <p className="flex items-center gap-3 text-md">
        <span aria-hidden className="h-5 w-[4px] bg-ders" />
        <Link to={back} className="tabular font-semibold underline-offset-4 hover:underline">
          {course.code} {course.title}
        </Link>
      </p>
      <PageHeader
        title="Değerlendirme ekle"
        meta="Kaydettiğinizde kayıtlı öğrencilere bildirim gider; değerlendirme ödev listelerinde görünür."
      />
      <div className="mt-8">
        <AssignmentForm
          initial={EMPTY_ASSIGNMENT}
          isNew
          remainingWeight={remainingWeight(existing.data ?? [])}
          groupSets={groupSets.data ?? []}
          submitLabel="Değerlendirmeyi ekle"
          busy={create.isPending}
          failure={failure}
          extra={
            <Link to={back} className="text-md font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline">
              Vazgeç
            </Link>
          }
          onSubmit={(draft, file) => {
            setFailure(null)
            create.mutate(
              { draft, file },
              {
                onSuccess: (a) => {
                  toast.success(`${a.title} eklendi; öğrencilere bildirim gitti`)
                  navigate(`/courses/${courseId}/assignments/${a.id}`)
                },
                onError: (e) => setFailure(toApiError(e)),
              },
            )
          }}
        />
      </div>
    </div>
  )
}
