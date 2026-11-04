import { useState } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatInstant } from '@/lib/time'
import { roleLabel, roleRank, useClub, useClubBoard, type ClubMember } from '@/features/clubs/api'
import { can, type ClubAccess } from '@/features/clubs/manage'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Panel } from '@/components/ui/Panel'
import { QueryBoundary } from '@/components/ui/States'
import type { CampusEvent } from './api'
import { staffName, staffable, useEventStaff, useStaffAction, type EventStaff } from './staff'
import { PersonLink } from '@/features/people/PersonLink'

const SHOWN = 8
const memberName = (m: Pick<ClubMember, 'firstName' | 'lastName'>) => [m.firstName, m.lastName].filter(Boolean).join(' ')

/**
 * Görevliler sekmesi: etkinliğin görevlileri ve onay bekleyen öneriler; kulüp üyeleri arasından görevli önerme.
 * Başkan önerirse görevli hemen atanır; diğer önerileri başkan onaylar ya da reddeder.
 */
export function EventStaffPanel({ event: e, access }: { event: CampusEvent; access: ClubAccess | undefined }) {
  const staff = useEventStaff(e.id, true)
  const action = useStaffAction(e.id)
  const approves = can(access, 'APPROVE_AS_PRESIDENT')
  const manages = can(access, 'MANAGE_EVENT_OPERATIONS')
  const proposes = manages && staffable(e)
  const [removing, setRemoving] = useState<EventStaff | null>(null)
  const fail = (err: unknown) => toast.error(toApiError(err).message)

  return (
    <div className="grid gap-14 lg:grid-cols-12 lg:gap-10">
      <div className="min-w-0 lg:col-span-7">
        <Panel title="Görevliler">
          <p className="max-w-[60ch] text-md text-ink-2">
            Görevliler kapı ekranından bilet okutur ve giriş yapar; katılımcı listesini göremezler.
          </p>
          <QueryBoundary query={staff} what="Görevliler" skeletonRows={2}>
            {(list) =>
              list.length === 0 ? (
                <p className="mt-5 text-ink-3">Henüz görevli yok.</p>
              ) : (
                <ul className="mt-5 border-t border-rule">
                  {[...list]
                    .sort(
                      (a, b) =>
                        Number(a.status === 'APPROVED') - Number(b.status === 'APPROVED') ||
                        staffName(a).localeCompare(staffName(b), 'tr-TR'),
                    )
                    .map((s) => (
                      <li
                        key={s.userId}
                        className="grid gap-x-6 gap-y-2 border-b border-rule py-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
                      >
                        <span className="min-w-0">
                          <span className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                            <PersonLink id={s.userId} className="font-semibold">
                              {staffName(s)}
                            </PersonLink>
                            {s.status === 'PENDING' && <Badge tone="warning">Başkan onayında</Badge>}
                          </span>
                          <span className="block text-sm text-ink-3">
                            {s.status === 'APPROVED' && s.decidedAt
                              ? `${formatInstant(s.decidedAt, 'date')} tarihinden beri görevli`
                              : `${formatInstant(s.createdAt, 'date')} tarihinde önerildi`}
                          </span>
                        </span>
                        <span className="flex flex-wrap items-center gap-x-5 gap-y-2 sm:justify-end">
                          {s.status === 'PENDING' && approves && (
                            <>
                              <Button
                                size="sm"
                                variant="primary"
                                loading={action.isPending && action.variables?.userId === s.userId && action.variables.kind === 'approve'}
                                onClick={() =>
                                  action.mutate(
                                    { kind: 'approve', userId: s.userId },
                                    { onSuccess: () => toast.success(`${staffName(s)} görevli oldu`), onError: fail },
                                  )
                                }
                              >
                                Onayla
                              </Button>
                              <button
                                type="button"
                                onClick={() =>
                                  action.mutate(
                                    { kind: 'reject', userId: s.userId },
                                    { onSuccess: () => toast.success('Öneri reddedildi'), onError: fail },
                                  )
                                }
                                className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline"
                              >
                                Reddet<span className="sr-only">: {staffName(s)}</span>
                              </button>
                            </>
                          )}
                          {manages && (s.status === 'APPROVED' || !approves) && (
                            <button
                              type="button"
                              onClick={() => setRemoving(s)}
                              className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline"
                            >
                              {s.status === 'APPROVED' ? 'Görevden al' : 'Öneriyi geri çek'}
                              <span className="sr-only">: {staffName(s)}</span>
                            </button>
                          )}
                        </span>
                      </li>
                    ))}
                </ul>
              )
            }
          </QueryBoundary>
        </Panel>
      </div>
      {proposes && e.clubId && (
        <aside className="lg:col-span-5">
          <Propose
            clubId={e.clubId}
            staff={staff.data ?? []}
            approves={approves}
            onPick={(userId, name) =>
              action.mutate(
                { kind: 'propose', userId },
                {
                  onSuccess: () => toast.success(approves ? `${name} görevli olarak atandı` : `${name} başkanın onayına önerildi`),
                  onError: fail,
                },
              )
            }
            busy={action.isPending && action.variables?.kind === 'propose' ? action.variables.userId : null}
          />
        </aside>
      )}

      <ConfirmDialog
        open={!!removing}
        onOpenChange={(o) => !o && setRemoving(null)}
        title={removing?.status === 'APPROVED' ? `${removing ? staffName(removing) : ''} görevden alınsın mı?` : 'Öneri geri çekilsin mi?'}
        description={removing?.status === 'APPROVED' ? 'Kapı ekranına erişimi hemen kapanır.' : 'Başkanın onayını bekleyen öneri silinir.'}
        confirmLabel={removing?.status === 'APPROVED' ? 'Görevden al' : 'Geri çek'}
        destructive={removing?.status === 'APPROVED'}
        loading={action.isPending && action.variables?.kind === 'remove'}
        onConfirm={() =>
          removing &&
          action.mutate(
            { kind: 'remove', userId: removing.userId },
            {
              onSuccess: () => {
                toast.success(removing.status === 'APPROVED' ? 'Görevli görevden alındı' : 'Öneri geri çekildi')
                setRemoving(null)
              },
              onError: fail,
            },
          )
        }
      />
    </div>
  )
}

/**
 * Görevli öner: kulübün üyeleri arasında arama; üye listesi yetkisi olmayan etkinlik yetkilisi yalnız yönetimi görür.
 * Zaten görevli ya da önerilmiş olanlar listede yok.
 */
function Propose({
  clubId,
  staff,
  approves,
  onPick,
  busy,
}: {
  clubId: string
  staff: EventStaff[]
  approves: boolean
  onPick: (userId: string, name: string) => void
  busy: string | null
}) {
  const club = useClub(clubId)
  const board = useClubBoard(clubId)
  const [q, setQ] = useState('')
  const taken = new Set(staff.map((s) => s.userId))
  const people = new Map<string, ClubMember>()
  for (const m of [...(board.data ?? []), ...(club.data?.members ?? [])])
    if (memberName(m) && !people.has(m.studentId)) people.set(m.studentId, m)
  const needle = q.trim().toLocaleLowerCase('tr-TR')
  const candidates = [...people.values()]
    .filter((m) => !taken.has(m.studentId) && (!needle || memberName(m).toLocaleLowerCase('tr-TR').includes(needle)))
    .sort((a, b) => roleRank(a.role) - roleRank(b.role) || memberName(a).localeCompare(memberName(b), 'tr-TR'))

  return (
    <Panel title="Görevli ekle">
      <p className="text-md text-ink-2">
        {approves ? 'Atadığınız üye hemen kapıda giriş yapabilir.' : 'Önerdiğiniz üye başkan onaylayınca kapıda giriş yapabilir.'}{' '}
        Görevliler kulübün aktif üyelerinden seçilir.
      </p>
      <label className="mt-5 block">
        <span className="mb-1 block text-sm font-semibold">Üye ara</span>
        <Input type="search" value={q} onChange={(ev) => setQ(ev.target.value)} />
      </label>
      {candidates.length === 0 ? (
        <p className="mt-4 text-md text-ink-3">{needle ? `“${q.trim()}” ile eşleşen üye yok.` : 'Önerilebilecek üye kalmadı.'}</p>
      ) : (
        <ul className="mt-4 border-t border-rule">
          {candidates.slice(0, SHOWN).map((m) => {
            const name = memberName(m)
            return (
              <li key={m.studentId} className="flex items-center justify-between gap-4 border-b border-rule py-3">
                <span className="min-w-0">
                  <span className="block font-semibold">{name}</span>
                  <span className="block text-sm text-ink-3">{roleLabel(m.role)}</span>
                </span>
                <Button size="sm" loading={busy === m.studentId} onClick={() => onPick(m.studentId, name)}>
                  {approves ? 'Ata' : 'Öner'}
                  <span className="sr-only">: {name}</span>
                </Button>
              </li>
            )
          })}
        </ul>
      )}
      {candidates.length > SHOWN && <p className="mt-3 text-sm text-ink-3">{candidates.length - SHOWN} üye daha; aramayla daraltın.</p>}
    </Panel>
  )
}
