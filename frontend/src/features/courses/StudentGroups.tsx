import { useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { useSession } from '@/lib/auth/session'
import type { MyAssignment } from '@/features/assignments/api'
import { groupRule, isGroupFull, useGroupMembership, type GroupRow } from '@/features/assignments/groups'
import type { GroupSet } from '@/features/assignments/staff'
import { PersonLink } from '@/features/people/PersonLink'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { isRunning, type Course } from './api'

/**
 * Ders sayfasında öğrencinin grupları (F-50): her set için grubu ve üyeleri; seçim açıksa katılma ve ayrılma.
 * Grup ödevi ancak grubu olan öğrenci tarafından teslim edilebilir; set kullanan ödevler burada listelenir.
 */
export function StudentGroups({ course, sets, assignments }: { course: Course; sets: GroupSet[]; assignments: MyAssignment[] }) {
  return (
    <div className="flex flex-col gap-14">
      {sets.map((s) => (
        <GroupSetBlock key={s.id} course={course} set={s} assignments={assignments.filter((a) => a.groupSetId === s.id)} />
      ))}
    </div>
  )
}

function GroupSetBlock({ course: c, set: s, assignments }: { course: Course; set: GroupSet; assignments: MyAssignment[] }) {
  const membership = useGroupMembership(c.id)
  const mine = s.groups.find((g) => g.id === s.myGroupId)
  const open = s.signupOpen && isRunning(c.status)
  // Sunucu teslim yapmış gruptan ayrılmaya izin vermez; düğme baştan gösterilmez.
  const submitted = assignments.some((a) => a.submission)
  const others = s.groups.filter((g) => g.id !== s.myGroupId)
  const busyGroup = membership.isPending && membership.variables?.kind === 'join' ? membership.variables.groupId : null

  const join = (g: GroupRow) =>
    membership.mutate(
      { kind: 'join', groupId: g.id },
      {
        onSuccess: () => toast.success(`Gruba katıldınız: ${g.name}`),
        onError: (e) => toast.error(toApiError(e).message),
      },
    )

  return (
    <section aria-labelledby={`grup-seti-${s.id}`} className="border-t-2 border-ink pt-5">
      <h2 id={`grup-seti-${s.id}`} className="text-2xl font-heavy">
        {s.name}
      </h2>
      <p className="mt-1 max-w-[64ch] text-md text-ink-2">{groupRule(s, open)}</p>
      {assignments.length > 0 && (
        <p className="mt-1 text-sm text-ink-3">
          Bu gruplarla teslim edilir:{' '}
          {assignments.map((a, i) => (
            <span key={a.id}>
              {i > 0 && ', '}
              <Link to={`/courses/${c.id}/assignments/${a.id}`} className="font-semibold text-ink-2 underline-offset-4 hover:underline">
                {a.title}
              </Link>
            </span>
          ))}
        </p>
      )}

      {mine ? (
        <MyGroup courseId={c.id} group={mine} leavable={open && !submitted} locked={open && submitted} />
      ) : (
        !open && (
          <p className="mt-6 max-w-[64ch] text-lg">
            Henüz bir grubunuz yok. <span className="text-ink-2">Hocanız sizi bir gruba atayınca burada görünür.</span>
          </p>
        )
      )}

      {open && others.length === 0 && !mine && <p className="mt-6 text-md text-ink-3">Henüz grup açılmadı. Hocanız grupları ekleyince burada seçebilirsiniz.</p>}

      {open && others.length > 0 && (
        <>
          {mine && <h3 className="mt-8 text-sm font-semibold text-ink-3">Diğer gruplar. Geçmek için önce grubunuzdan ayrılın.</h3>}
          <ul className={`border-t border-rule ${mine ? 'mt-2' : 'mt-6'}`}>
            {others.map((g) => {
              const full = isGroupFull(s, g)
              return (
                <li key={g.id} className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-6 border-b border-rule py-3.5">
                  <span className="min-w-0">
                    <span className="block font-semibold">{g.name}</span>
                    <span className="tabular block text-sm text-ink-3">
                      {g.memberCount}
                      {s.maxMembers ? ` / ${s.maxMembers}` : ''} üye
                    </span>
                  </span>
                  {full ? (
                    <Badge>Dolu</Badge>
                  ) : (
                    !mine && (
                      <Button size="sm" disabled={membership.isPending && busyGroup !== g.id} loading={busyGroup === g.id} onClick={() => join(g)}>
                        Katıl<span className="sr-only">: {g.name}</span>
                      </Button>
                    )
                  )}
                </li>
              )
            })}
          </ul>
        </>
      )}
    </section>
  )
}

/** Grubum: grup adı ve üyeler; seçim açıksa ayrılma. Teslim yapmış gruptan ayrılınamayacağı baştan söylenir. */
function MyGroup({ courseId, group: g, leavable, locked }: { courseId: string; group: GroupRow; leavable: boolean; locked: boolean }) {
  const me = useSession()?.userId
  const membership = useGroupMembership(courseId)
  const [leaving, setLeaving] = useState(false)

  return (
    <div className="mt-6 border-l-[5px] border-ders pl-5">
      <p className="text-sm text-ink-3">Grubunuz</p>
      <p className="text-3xl leading-tight font-heavy">{g.name}</p>
      <ul className="mt-3 flex flex-col gap-1">
        {g.members.map((m) => (
          <li key={m.studentId} className="text-md">
            <PersonLink id={m.studentId} className="font-semibold">
              {m.name ?? 'Öğrenci'}
            </PersonLink>
            {m.studentId === me && <span className="ml-2 text-sm text-ink-3">siz</span>}
          </li>
        ))}
      </ul>
      {leavable && (
        <button
          type="button"
          onClick={() => setLeaving(true)}
          className="mt-4 text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline"
        >
          Gruptan ayrıl
        </button>
      )}
      {locked && <p className="mt-4 max-w-[60ch] text-sm text-ink-3">Grubunuz teslim yaptığı için ayrılamazsınız; değişiklik için hocanıza başvurun.</p>}
      <ConfirmDialog
        open={leaving}
        onOpenChange={setLeaving}
        title="Gruptan ayrılıyor musunuz?"
        description={`${g.name} üyeliğiniz sona erer. Seçim açık olduğu sürece başka bir gruba katılabilirsiniz; grup ödevini gruba katılmadan teslim edemezsiniz.`}
        confirmLabel="Gruptan ayrıl"
        destructive
        loading={membership.isPending}
        onConfirm={() =>
          membership.mutate(
            { kind: 'leave', groupId: g.id },
            {
              onSuccess: () => {
                toast.success(`Gruptan ayrıldınız: ${g.name}`)
                setLeaving(false)
              },
              onError: (e) => toast.error(toApiError(e).message),
            },
          )
        }
      />
    </div>
  )
}
