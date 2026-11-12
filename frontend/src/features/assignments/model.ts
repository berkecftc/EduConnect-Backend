import { formatRemaining, localDiffMs, nowLocalIso } from '@/lib/time'
import { formatNumber } from '@/lib/format'
import type { MyAssignment } from './api'

export type Phase = 'open' | 'late' | 'closed'
export type Tone = 'neutral' | 'info' | 'warning' | 'danger' | 'success'

export type AssignmentView = {
  phase: Phase
  submitted: boolean
  graded: boolean
  /** Teslim formu açık mı (backend kurallarıyla aynı). */
  canSubmit: boolean
  /** Neden kapalı; açıksa null. */
  blockedReason: string | null
  /** Teslimi engelleyen tek şey grubun olmaması (ekran gruplara yönlendirir). */
  needsGroup: boolean
  status: { label: string; tone: Tone }
  /** Kısa zaman bilgisi: "2 gün kaldı", "Geç teslim 5 saat daha açık". */
  timing: string | null
  /** "17,5 / 20" ya da "80 → 64 / 100". */
  gradeText: string | null
  /** Geç teslim kesintisi açıklaması: "geç teslim %20". */
  penaltyText: string | null
  /** Kapanış anı (etkin geç teslim bitişi ya da son tarih). */
  closesAt: string
  /** Sıralama için eylem anı: açıkken son tarih, geç teslim penceresinde kapanış. */
  actionAt: string
}

/**
 * Öğrencinin bir ödevdeki durumu (backend DeadlinePolicy ile aynı kurallar):
 * - son tarihten sonra teslim geç sayılır; geç teslim bitişinden sonra kapanır,
 * - puanlanmış teslim değiştirilemez, geç teslimden sonra yeniden teslim yok.
 */
export function viewAssignment(a: MyAssignment, now: string = nowLocalIso()): AssignmentView {
  const closesAt = a.effectiveLateUntil ?? a.effectiveDueDate
  const toDue = localDiffMs(now, a.effectiveDueDate)
  const toClose = localDiffMs(now, closesAt)
  const phase: Phase = toDue >= 0 ? 'open' : toClose >= 0 ? 'late' : 'closed'
  const s = a.submission
  const graded = !!s && a.gradesPublished && s.grade !== null

  let blockedReason: string | null = null
  let needsGroup = false
  if (graded) blockedReason = 'Puanlanan çalışma yeniden teslim edilemez.'
  else if (phase === 'closed') blockedReason = 'Teslim süresi doldu.'
  else if (s?.late) blockedReason = 'Geç teslimden sonra yeniden teslim yapılamaz.'
  else if (a.groupSetId && !a.groupId) {
    blockedReason = 'Bu bir grup ödevi. Önce bir gruba katılın.'
    needsGroup = true
  }

  let status: AssignmentView['status']
  if (graded) status = { label: 'Puanlandı', tone: 'success' }
  else if (s) status = { label: s.late ? 'Geç teslim edildi' : 'Teslim edildi', tone: 'info' }
  else if (phase === 'open') status = { label: 'Teslim bekliyor', tone: toDue < 86_400_000 ? 'warning' : 'neutral' }
  else if (phase === 'late') status = { label: 'Geç teslim açık', tone: 'warning' }
  else status = { label: 'Teslim edilmedi', tone: 'danger' }

  let timing: string | null = null
  if (s && !graded) timing = a.gradesPublished ? 'Puanlanmadı' : 'Puan henüz ilan edilmedi'
  else if (!s && phase === 'open') timing = `${formatRemaining(toDue)} kaldı`
  else if (!s && phase === 'late') timing = `Geç teslim ${formatRemaining(toClose)} daha açık`

  let gradeText: string | null = null
  if (graded && s) {
    const max = formatNumber(a.maxPoints)
    const final = s.finalGrade ?? s.grade
    gradeText =
      s.late && final !== s.grade ? `${formatNumber(s.grade)} → ${formatNumber(final)} / ${max}` : `${formatNumber(final)} / ${max}`
  }

  return {
    phase,
    submitted: !!s,
    graded,
    canSubmit: blockedReason === null,
    blockedReason,
    needsGroup,
    status,
    timing,
    gradeText,
    penaltyText: a.latePenaltyPercent > 0 ? `geç teslim %${formatNumber(a.latePenaltyPercent)}` : null,
    closesAt,
    actionAt: phase === 'late' ? closesAt : a.effectiveDueDate,
  }
}
