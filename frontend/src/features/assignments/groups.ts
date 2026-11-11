import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { formatLocal } from '@/lib/time'
import { assignmentKeys } from './api'
import { staffKeys, type GroupSet } from './staff'

/**
 * Öğrencinin grup seçimi (F-50): bir sette en fazla bir gruptadır. Seçim açıksa kendisi katılır ya da ayrılır;
 * kapalıysa ya da seçimi hoca yapıyorsa gruplar yalnız görüntülenir. Öğrenci başka grupların üye adlarını görmez.
 */

export type GroupRow = GroupSet['groups'][number]

export const isGroupFull = (s: Pick<GroupSet, 'maxMembers'>, g: Pick<GroupRow, 'memberCount'>) =>
  s.maxMembers != null && g.memberCount >= s.maxMembers

/** Öğrencinin hemen seçim yapması gereken setler; ders sekmesinde sayı olarak görünür. */
export const setsAwaitingChoice = (sets: GroupSet[]) => sets.filter((s) => s.signupOpen && !s.myGroupId && s.groups.length > 0)

/** Setin kuralı tek cümlede. `open`: seçim açık ve ders sürüyor. */
export function groupRule(s: GroupSet, open: boolean): string {
  if (open) {
    const closes = s.signupClosesAt ? `; seçim ${formatLocal(s.signupClosesAt, 'datetime')} kapanır` : ''
    const size = s.maxMembers ? ` Grup başına en fazla ${s.maxMembers} kişi.` : ''
    return `Grubunuzu kendiniz seçersiniz${closes}.${size}`
  }
  return s.selfSignup ? 'Grup seçimi kapandı; değişiklik için hocanıza başvurun.' : 'Grupları hocanız atar.'
}

export function useGroupMembership(courseId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (a: { kind: 'join' | 'leave'; groupId: string }) =>
      (await api.post<GroupSet[]>(`/assignments/groups/${a.groupId}/${a.kind}`)).data,
    onSuccess: (sets) => {
      qc.setQueryData(staffKeys.groupSets(courseId), sets)
      // Ödevlerdeki grup adı ve teslim izni değişir.
      void qc.invalidateQueries({ queryKey: assignmentKeys.mine })
    },
  })
}
