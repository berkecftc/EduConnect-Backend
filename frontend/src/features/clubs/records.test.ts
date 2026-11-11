import { describe, expect, it } from 'vitest'
import {
  academicYearOf,
  documentProblem,
  formatMoney,
  parseMoney,
  quorum,
  recentYears,
  validateMeetingDraft,
  yearLabel,
  type MeetingDraft,
} from './records'

describe('academicYearOf', () => {
  it('kulübün bildirdiği bitiş gününe uyar', () => {
    expect(academicYearOf('2026-07-01', '2026-06-30')).toBe(2027)
    expect(academicYearOf('2026-06-30', '2026-06-30')).toBe(2026)
  })

  it('bitiş günü gelmezse 30 Eylül yılın son günü; ertesi gün yeni akademik yıl', () => {
    expect(academicYearOf('2026-09-30')).toBe(2026)
    expect(academicYearOf('2026-10-01')).toBe(2027)
    expect(academicYearOf('2027-03-15')).toBe(2027)
    expect(yearLabel(2027)).toBe('2026-2027')
    expect(recentYears(2027, 2)).toEqual([2027, 2026, 2025])
  })
})

describe('parseMoney / formatMoney', () => {
  it('Türkçe ve noktalı yazımı kabul eder, en çok iki ondalık', () => {
    expect(parseMoney('1.250,50')).toBe(1250.5)
    expect(parseMoney('1250,5')).toBe(1250.5)
    expect(parseMoney('1250.50')).toBe(1250.5)
    expect(parseMoney(' 3.200 ₺ ')).toBe(3200)
    expect(parseMoney('12,345')).toBeNull()
    expect(parseMoney('abc')).toBeNull()
    expect(parseMoney('')).toBeNull()
  })

  it('simge sonda, iki ondalık', () => {
    expect(formatMoney(3200)).toBe('3.200,00 ₺')
    expect(formatMoney(null)).toBe('–')
  })
})

describe('quorum', () => {
  const board = [
    { studentId: 'a', firstName: null, lastName: null, role: 'ROLE_CLUB_OFFICIAL' },
    { studentId: 'b', firstName: null, lastName: null, role: 'VICE_PRESIDENT' },
    { studentId: 'c', firstName: null, lastName: null, role: 'ROLE_BOARD_MEMBER' },
    { studentId: 'd', firstName: null, lastName: null, role: 'EVENT_COORDINATOR' },
  ]
  it('yalnız yönetim kurulu sayılır; yarıdan fazlası gerekir', () => {
    expect(quorum(board, ['a', 'd'])).toEqual({ present: 1, size: 3, met: false })
    expect(quorum(board, ['a', 'c'])).toEqual({ present: 2, size: 3, met: true })
  })
})

describe('validateMeetingDraft', () => {
  const draft = (d: Partial<MeetingDraft> = {}): MeetingDraft => ({
    meetingAt: '2026-10-15T18:00',
    location: '',
    agenda: 'Gündem',
    minutes: 'Tutanak',
    attendeeIds: ['a'],
    decisions: [''],
    note: '',
    ...d,
  })
  it('ileri tarih, boş gündem ve katılımcısız tutanak geçmez', () => {
    expect(validateMeetingDraft(draft(), '2026-10-19T12:00')).toEqual({})
    const e = validateMeetingDraft(draft({ meetingAt: '2026-10-20T18:00', agenda: ' ', attendeeIds: [] }), '2026-10-19T12:00')
    expect(Object.keys(e).sort()).toEqual(['agenda', 'attendeeIds', 'meetingAt'])
  })
})

describe('documentProblem', () => {
  it('PDF ve görseller, en çok 10 MB', () => {
    expect(documentProblem(new File(['x'], 'fatura.pdf', { type: 'application/pdf' }))).toBeNull()
    expect(documentProblem(new File(['x'], 'tablo.xlsx', { type: 'application/vnd.ms-excel' }))).toMatch(/PDF/)
  })
})
