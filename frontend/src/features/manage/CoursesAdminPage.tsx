import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api } from '@/lib/api/client'
import { toApiError } from '@/lib/api/problem'
import { usePageTitle } from '@/lib/usePageTitle'
import {
  COURSE_STATUS_LABEL,
  COURSE_STATUS_TONE,
  STAFF_ROLE_LABEL,
  courseKeys,
  useCatalog,
  useCourseStaff,
  useCurrentTerm,
  useTerms,
  type Course,
} from '@/features/courses/api'
import { useAcademicianSearch } from '@/features/courses/teach'
import { withTitle } from '@/features/people/titles'
import { Badge } from '@/components/ui/Badge'
import { ChoiceGroup } from '@/components/ui/ChoiceGroup'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import { RequireAccess } from './RequireAccess'

type PreviousRole = 'INSTRUCTOR' | 'ASSISTANT' | 'NONE'

/** Koordinatör devri (yönetici, F-43): eski koordinatör hoca ya da asistan olarak kalabilir ya da kadrodan çıkar. */
function useTransferCoordinator(courseId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (v: { userId: string; previous: PreviousRole }) => {
      await api.put(`/courses/${courseId}/coordinator`, {
        userId: v.userId,
        previousCoordinatorRole: v.previous === 'NONE' ? null : v.previous,
      })
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: courseKeys.staff(courseId) })
      void qc.invalidateQueries({ queryKey: ['courses', 'catalog'] })
      void qc.invalidateQueries({ queryKey: courseKeys.detail(courseId) })
    },
  })
}

/** Dersler (yönetici): dönemin dersleri ve koordinatörleri; koordinatör devri. */
export function CoursesAdminPage() {
  usePageTitle('Dersler')
  return (
    <RequireAccess>
      <CoursesAdmin />
    </RequireAccess>
  )
}

function CoursesAdmin() {
  const terms = useTerms()
  const current = useCurrentTerm()
  const [params, setParams] = useSearchParams()
  const termId = params.get('donem') ?? current.data?.id ?? terms.data?.[0]?.id
  const courses = useCatalog(termId)
  const [q, setQ] = useState('')
  const [transfer, setTransfer] = useState<Course | null>(null)
  const needle = q.trim().toLocaleLowerCase('tr-TR')

  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader
        title="Dersler"
        meta="Dönemin yayımlanmış dersleri ve koordinatörleri. Koordinatör devri yalnız yönetici tarafından yapılır."
      />
      <div className="mt-8 flex flex-wrap items-end gap-x-6 gap-y-4">
        <Field label="Dönem" className="w-[14rem]">
          <Select
            value={termId ?? ''}
            onChange={(e) =>
              setParams(
                (p) => {
                  const next = new URLSearchParams(p)
                  next.set('donem', e.target.value)
                  return next
                },
                { replace: true },
              )
            }
            disabled={terms.isPending}
          >
            {(terms.data ?? []).map((t) => (
              <option key={t.id} value={t.id}>
                {t.label}
              </option>
            ))}
          </Select>
        </Field>
        <label className="w-full max-w-[22rem]">
          <span className="mb-1 block text-sm font-semibold">Ara</span>
          <Input type="search" placeholder="Ders kodu, adı ya da koordinatör" value={q} onChange={(e) => setQ(e.target.value)} />
        </label>
      </div>
      <div className="mt-8">
        <QueryBoundary query={courses} what="Dersler">
          {(list) => {
            const shown = list
              .filter((c) => !needle || `${c.code} ${c.title} ${c.instructorName ?? ''}`.toLocaleLowerCase('tr-TR').includes(needle))
              .sort((a, b) => a.code.localeCompare(b.code, 'tr') || (a.section ?? '').localeCompare(b.section ?? ''))
            if (list.length === 0)
              return <EmptyState title="Bu dönemde yayımlanmış ders yok">Taslak dersler burada listelenmez.</EmptyState>
            if (shown.length === 0) return <p className="text-md text-ink-2">“{q}” ile eşleşen ders yok.</p>
            return (
              <ul className="border-t border-ink">
                {shown.map((c) => (
                  <li
                    key={c.id}
                    className="grid gap-x-8 gap-y-2 border-b border-rule py-4 md:grid-cols-[9rem_minmax(0,1fr)_auto] md:items-baseline"
                  >
                    <span className="tabular text-lg font-heavy">{c.code}</span>
                    <span className="min-w-0">
                      <span className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                        <span className="font-semibold">{c.title}</span>
                        <Badge tone={COURSE_STATUS_TONE[c.status]}>{COURSE_STATUS_LABEL[c.status]}</Badge>
                      </span>
                      <span className="mt-0.5 flex flex-wrap gap-x-4 text-sm text-ink-3">
                        {c.section && <span>Şube {c.section}</span>}
                        <span>Koordinatör: {withTitle(c.instructorName, c.instructorTitle, c.instructorAcademicTitle) || 'yok'}</span>
                        <span className="tabular">
                          {c.enrolledStudentCount} / {c.capacity} öğrenci
                        </span>
                      </span>
                    </span>
                    <button
                      type="button"
                      disabled={c.status === 'ARCHIVED'}
                      onClick={() => setTransfer(c)}
                      className="justify-self-start text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline disabled:cursor-not-allowed disabled:text-ink-3 disabled:no-underline md:justify-self-end"
                    >
                      Koordinatörü devret<span className="sr-only">: {c.code}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )
          }}
        </QueryBoundary>
      </div>
      {transfer && <TransferDialog course={transfer} onClose={() => setTransfer(null)} />}
    </div>
  )
}

function TransferDialog({ course: c, onClose }: { course: Course; onClose: () => void }) {
  const staff = useCourseStaff(c.id)
  const transfer = useTransferCoordinator(c.id)
  const [q, setQ] = useState('')
  const [query, setQuery] = useState('')
  const [userId, setUserId] = useState<string | undefined>()
  const [previous, setPrevious] = useState<PreviousRole>('INSTRUCTOR')
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    const t = setTimeout(() => setQuery(q), 250)
    return () => clearTimeout(t)
  }, [q])
  const hits = useAcademicianSearch(query)
  const coordinator = staff.data?.find((s) => s.role === 'COORDINATOR')
  const choices = (hits.data ?? [])
    .filter((h) => h.id !== coordinator?.userId)
    .map((h) => {
      const onStaff = staff.data?.find((s) => s.userId === h.id)
      return {
        value: h.id,
        title: withTitle([h.firstName, h.lastName].filter(Boolean).join(' ') || 'Akademisyen', h.title, h.academicTitle),
        description:
          [h.department, onStaff ? `şu an ${STAFF_ROLE_LABEL[onStaff.role].toLocaleLowerCase('tr-TR')}` : null]
            .filter(Boolean)
            .join(', ') || undefined,
      }
    })
  const picked = choices.find((x) => x.value === userId)

  return (
    <ConfirmDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={`${c.code} koordinatörünü devret`}
      description={
        coordinator
          ? `Şu anki koordinatör: ${withTitle(coordinator.name, coordinator.title, coordinator.academicTitle)}. Yeni koordinatöre ve eskisine bildirim gider.`
          : 'Yeni koordinatöre bildirim gider.'
      }
      confirmLabel="Devret"
      loading={transfer.isPending}
      onConfirm={() => {
        if (!picked) return setError('Yeni koordinatörü listeden seçin.')
        transfer.mutate(
          { userId: picked.value, previous },
          {
            onSuccess: () => {
              toast.success(`${c.code} koordinatörü: ${picked.title}`)
              onClose()
            },
            onError: (e) => setError(toApiError(e).message),
          },
        )
      }}
    >
      <div className="flex max-h-[60vh] flex-col gap-5 overflow-y-auto pr-1">
        <Field label="Yeni koordinatör" hint="Ad ya da soyaddan en az iki harf. Araştırma görevlileri koordinatör olamaz.">
          <Input
            type="search"
            autoFocus
            autoComplete="off"
            value={q}
            onChange={(e) => {
              setQ(e.target.value)
              setUserId(undefined)
              setError(null)
            }}
          />
        </Field>
        {query.trim().length >= 2 &&
          hits.isSuccess &&
          (choices.length > 0 ? (
            <ChoiceGroup layout="rows" label="Sonuçlar" value={userId} onChange={setUserId} choices={choices} />
          ) : (
            <p className="text-md text-ink-2">“{query.trim()}” ile eşleşen akademisyen yok.</p>
          ))}
        {coordinator && (
          <ChoiceGroup
            layout="rows"
            label={`${coordinator.name} ne olsun?`}
            value={previous}
            onChange={setPrevious}
            choices={[
              { value: 'INSTRUCTOR', title: 'Hoca olarak kadroda kalsın' },
              { value: 'ASSISTANT', title: 'Asistan olarak kadroda kalsın' },
              { value: 'NONE', title: 'Kadrodan çıksın' },
            ]}
          />
        )}
        {error && <p className="text-sm font-semibold text-danger">{error}</p>}
      </div>
    </ConfirmDialog>
  )
}
