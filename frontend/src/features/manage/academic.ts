import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { CatalogFaculty, ProgramLevel } from '@/features/auth/register/api'

/**
 * Akademik yapı (yönetici, F-53): fakülte → bölüm → program. Birim silinmez, `active: false` ile kapatılır;
 * kapalı birim yeni başvuru ve kayıtlarda seçilemez, mevcut kayıtlar korunur. Her yazma bütün ağacı döndürür.
 */

export type UnitKind = 'faculty' | 'department' | 'program'

export const UNIT_LABEL: Record<UnitKind, string> = { faculty: 'Fakülte', department: 'Bölüm', program: 'Program' }

const PATH: Record<UnitKind, string> = { faculty: 'faculties', department: 'departments', program: 'programs' }

export type UnitDraft = { code: string; name: string; level: ProgramLevel; durationYears: string; active: boolean }

export type UnitDraftErrors = Partial<Record<keyof UnitDraft, string>>

export const CATALOG_ALL_KEY = ['academic', 'catalog', 'all'] as const

/** Kapalı birimler dahil bütün ağaç. */
export function useCatalogAll() {
  return useQuery({
    queryKey: CATALOG_ALL_KEY,
    queryFn: async () => (await api.get<CatalogFaculty[]>('/users/academic/catalog/all')).data,
  })
}

/** Kod: en fazla 20 harf, rakam, alt çizgi ya da tire (sunucu kuralı). */
export function validateUnitDraft(kind: UnitKind, d: UnitDraft): UnitDraftErrors {
  const e: UnitDraftErrors = {}
  if (!/^[A-Za-z0-9_-]{1,20}$/.test(d.code.trim())) e.code = 'Kod en fazla 20 harf, rakam, alt çizgi ya da tire olabilir.'
  if (!d.name.trim()) e.name = 'Adı yazın.'
  if (kind === 'program') {
    const n = Number(d.durationYears)
    if (!Number.isInteger(n) || n < 1 || n > 8) e.durationYears = 'Süre 1 ile 8 yıl arasında olmalı.'
  }
  return e
}

/**
 * Birim ekle ya da düzenle. Düzenlemede `active` her zaman gönderilir: sunucu eksik `active`'i `true` sayar
 * ve kapalı birimi istemeden açardı. `parentId` yalnız eklemede (düzenlemede null = mevcut üst birim).
 */
export function useSaveUnit() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (v: { kind: UnitKind; id?: string; parentId?: string; draft: UnitDraft }) => {
      const body = {
        parentId: v.id ? null : (v.parentId ?? null),
        code: v.draft.code.trim(),
        name: v.draft.name.trim(),
        active: v.draft.active,
        ...(v.kind === 'program' ? { level: v.draft.level, durationYears: Number(v.draft.durationYears) } : {}),
      }
      const url = `/users/academic/${PATH[v.kind]}${v.id ? `/${v.id}` : ''}`
      return (v.id ? await api.put<CatalogFaculty[]>(url, body) : await api.post<CatalogFaculty[]>(url, body)).data
    },
    onSuccess: (tree) => {
      qc.setQueryData(CATALOG_ALL_KEY, tree)
      // Başvuru ve profil formlarının kullandığı yalnız-etkin katalog da tazelenir.
      void qc.invalidateQueries({ queryKey: ['academic', 'catalog'], exact: true })
    },
  })
}
