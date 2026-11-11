import { useMemo } from 'react'
import { usePageTitle } from '@/lib/usePageTitle'
import { nowLocalIso } from '@/lib/time'
import { useMyCourses } from '@/features/courses/api'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import { AssignmentRow } from './AssignmentRow'
import { useMyAssignments, type MyAssignment } from './api'
import { viewAssignment } from './model'

type Bucket = { key: string; title: string; items: MyAssignment[] }

/** Ödevlerim (öğrenci): teslim bekleyen, teslim edilen, puanlanan ve kaçırılan olarak gruplu. */
export function AssignmentsPage() {
  usePageTitle('Ödevlerim')
  const assignments = useMyAssignments()
  const courses = useMyCourses()
  const codeOf = useMemo(() => new Map((courses.data ?? []).map((c) => [c.id, c.code])), [courses.data])
  const now = nowLocalIso()

  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader title="Ödevlerim" meta="Tüm derslerinizin ödevleri, sınavları ve projeleri" />
      <div className="mt-8">
        <QueryBoundary query={assignments} what="Ödevler">
          {(list) => {
            if (list.length === 0) {
              return <EmptyState title="Henüz ödeviniz yok">Kayıtlı olduğunuz derslere ödev eklendiğinde burada listelenir.</EmptyState>
            }
            const buckets: Bucket[] = [
              { key: 'bekleyen', title: 'Teslim bekleyenler', items: [] },
              { key: 'teslim', title: 'Teslim edilenler', items: [] },
              { key: 'puan', title: 'Puanlananlar', items: [] },
              { key: 'kacan', title: 'Kaçırılanlar', items: [] },
            ]
            for (const a of list) {
              const v = viewAssignment(a, now)
              const b = v.graded ? 2 : v.submitted ? 1 : v.phase === 'closed' || !v.canSubmit ? 3 : 0
              buckets[b]!.items.push(a)
            }
            buckets[0]!.items.sort((x, y) => viewAssignment(x, now).actionAt.localeCompare(viewAssignment(y, now).actionAt))
            for (const b of buckets.slice(1)) b.items.sort((x, y) => y.effectiveDueDate.localeCompare(x.effectiveDueDate))
            return (
              <div className="flex flex-col gap-20">
                {buckets
                  .filter((b) => b.items.length > 0)
                  .map((b) => (
                    <section key={b.key} aria-labelledby={`odev-${b.key}`}>
                      <h2 id={`odev-${b.key}`} className="flex items-baseline gap-3 text-2xl">
                        {b.title}
                        <span className="tabular text-lg font-normal text-ink-3">{b.items.length}</span>
                      </h2>
                      <ul className="mt-3 border-t border-ink">
                        {b.items.map((a) => (
                          <AssignmentRow key={a.id} assignment={a} courseCode={codeOf.get(a.courseId)} now={now} />
                        ))}
                      </ul>
                    </section>
                  ))}
              </div>
            )
          }}
        </QueryBoundary>
      </div>
    </div>
  )
}
