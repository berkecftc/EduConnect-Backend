import { useSyncExternalStore } from 'react'
import { hasRole, useSession, type Session } from './session'

/**
 * Çift bağlılık (F-54): hem personel hem öğrenci olan hesapta menü ve Bugün ekranı seçilen görünüme göre açılır.
 * Yetki değişmez; sunucu iki rolü de görür. Seçim bu cihazda saklanır; seçilmemişse sunucunun rol sırasındaki ilki (personel).
 */
export type Panel = 'academician' | 'student'

const KEY = 'ec-panel'

function read(): Panel | null {
  try {
    const v = localStorage.getItem(KEY)
    return v === 'academician' || v === 'student' ? v : null
  } catch {
    return null
  }
}

let chosen: Panel | null = read()
const listeners = new Set<() => void>()

export function setPanel(next: Panel) {
  chosen = next
  try {
    localStorage.setItem(KEY, next)
  } catch {
    // Depolama kapalıysa seçim yalnız bu sekmede geçerli.
  }
  listeners.forEach((l) => l())
}

const subscribe = (listener: () => void) => {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export const isDualAffiliation = (session: Session | null) =>
  hasRole(session, 'ROLE_ACADEMICIAN') && hasRole(session, 'ROLE_STUDENT', 'ROLE_CLUB_OFFICIAL')

/** Etkin görünüm: tek bağlılıkta `null`. */
export const panelOf = (session: Session | null, choice: Panel | null): Panel | null =>
  isDualAffiliation(session) ? (choice ?? 'academician') : null

export function usePanel(): Panel | null {
  const session = useSession()
  return panelOf(session, useSyncExternalStore(subscribe, () => chosen, () => null))
}

/** Bu görünümde öğrenci ve akademisyen ekranlarından hangileri açılır; tek bağlılıkta rolün kendisi. */
export const viewRoles = (session: Session | null, panel: Panel | null) => ({
  student: hasRole(session, 'ROLE_STUDENT', 'ROLE_CLUB_OFFICIAL') && panel !== 'academician',
  academician: hasRole(session, 'ROLE_ACADEMICIAN') && panel !== 'student',
})
