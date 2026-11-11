import { useSyncExternalStore } from 'react'

export type ThemePref = 'light' | 'dark' | 'system'

const KEY = 'ec-theme'
const listeners = new Set<() => void>()

function read(): ThemePref {
  try {
    const v = localStorage.getItem(KEY)
    return v === 'light' || v === 'dark' ? v : 'system'
  } catch {
    return 'system'
  }
}

let current: ThemePref = read()

/** Açık/koyu tercihini uygular; "system" işletim sistemine bırakır (index.css medya sorgusu). */
export function setTheme(pref: ThemePref) {
  current = pref
  const root = document.documentElement
  if (pref === 'system') delete root.dataset.theme
  else root.dataset.theme = pref
  try {
    if (pref === 'system') localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, pref)
  } catch {
    // Tercih yalnız bu oturumda geçerli olur.
  }
  listeners.forEach((l) => l())
}

export function useTheme(): ThemePref {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => {
        listeners.delete(l)
      }
    },
    () => current,
    () => 'system',
  )
}
