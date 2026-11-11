import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { Term } from '@/features/courses/api'

/**
 * Dönem yönetimi (yönetici, F-41, F-44). Akademik yıl bitiş yılıyla tutulur: 2026 → "2025-2026".
 * Kayıt penceresi tanımlıysa öğrenciler derslere yalnız bu aralıkta başvurabilir (409 ENROLLMENT_CLOSED).
 */

export type Season = Term['season']

export const SEASON_LABEL: Record<Season, string> = { FALL: 'Güz', SPRING: 'Bahar', SUMMER: 'Yaz' }

/** Backend'in etiketiyle aynı: `(yıl − 1)-yıl Güz`. */
export const termLabel = (academicYear: number, season: Season) => `${academicYear - 1}-${academicYear} ${SEASON_LABEL[season]}`

export type TermDraft = {
  academicYear: string
  season: Season
  startsOn: string
  endsOn: string
  enrollmentOpensOn: string
  enrollmentClosesOn: string
}

export type TermDraftErrors = Partial<Record<keyof TermDraft, string>>

export function termToDraft(t: Term): TermDraft {
  return {
    academicYear: String(t.academicYear),
    season: t.season,
    startsOn: t.startsOn,
    endsOn: t.endsOn,
    enrollmentOpensOn: t.enrollmentOpensOn ?? '',
    enrollmentClosesOn: t.enrollmentClosesOn ?? '',
  }
}

/** Ön kontrol; kurallar TermService ile aynı (INVALID_TERM_DATES). */
export function validateTermDraft(d: TermDraft): TermDraftErrors {
  const e: TermDraftErrors = {}
  const year = Number(d.academicYear)
  if (!Number.isInteger(year) || year < 2000 || year > 2200) e.academicYear = 'Akademik yılı seçin.'
  if (!d.startsOn) e.startsOn = 'Başlangıç tarihini seçin.'
  if (!d.endsOn) e.endsOn = 'Bitiş tarihini seçin.'
  else if (d.startsOn && d.endsOn <= d.startsOn) e.endsOn = 'Dönem bitişi başlangıçtan sonra olmalı.'
  if (d.enrollmentOpensOn && d.enrollmentClosesOn && d.enrollmentOpensOn > d.enrollmentClosesOn)
    e.enrollmentClosesOn = 'Kayıt kapanışı açılıştan önce olamaz.'
  if (d.enrollmentClosesOn && d.endsOn && d.enrollmentClosesOn > d.endsOn) e.enrollmentClosesOn = 'Kayıt, dönem bitmeden kapanmalı.'
  return e
}

/** Dönemin bugüne göre durumu (tarihler bölgesiz `YYYY-MM-DD`). */
export function termPhase(t: Term, today: string): 'current' | 'upcoming' | 'past' {
  if (today < t.startsOn) return 'upcoming'
  if (today > t.endsOn) return 'past'
  return 'current'
}

export function useSaveTerm() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, draft }: { id?: string; draft: TermDraft }) => {
      const body = {
        academicYear: Number(draft.academicYear),
        season: draft.season,
        startsOn: draft.startsOn,
        endsOn: draft.endsOn,
        enrollmentOpensOn: draft.enrollmentOpensOn || null,
        enrollmentClosesOn: draft.enrollmentClosesOn || null,
      }
      if (id) return (await api.put<Term>(`/courses/terms/${id}`, body)).data
      return (await api.post<Term>('/courses/terms', body)).data
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['courses', 'terms'] }),
  })
}

export function useDeleteTerm() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/courses/terms/${id}`)
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['courses', 'terms'] }),
  })
}
