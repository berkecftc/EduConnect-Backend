import { describe, expect, it } from 'vitest'
import { ballotsOf, ballotsToVote, candidatesOf, isRunning, seatsOf, type Election, type ElectionCandidate } from './elections'

const cand = (id: string, ballot: ElectionCandidate['ballot'], votes: number | null = null): ElectionCandidate => ({
  id,
  ballot,
  studentId: `s-${id}`,
  firstName: 'Ad',
  lastName: id,
  votes,
  elected: false,
})

const election = (e: Partial<Election>): Election => ({
  id: 'e1',
  clubId: 'k1',
  status: 'CANDIDACY',
  boardSeats: 3,
  auditSeats: 0,
  note: null,
  openedBy: 'u1',
  openedAt: '2026-10-01T09:00:00Z',
  votingStartedAt: null,
  closedAt: null,
  eligibleVoters: null,
  voters: null,
  requestId: null,
  completedAt: null,
  candidates: [],
  myBallots: [],
  ...e,
})

describe('seatsOf / ballotsOf', () => {
  it('başkanlık her zaman bir koltuk; koltuğu olmayan kurul listelenmez', () => {
    const e = election({ boardSeats: 3, auditSeats: 0 })
    expect(seatsOf(e, 'PRESIDENT')).toBe(1)
    expect(seatsOf(e, 'BOARD')).toBe(3)
    expect(ballotsOf(e).map((b) => b.code)).toEqual(['PRESIDENT', 'BOARD'])
  })
})

describe('candidatesOf', () => {
  it('sayımdan önce başvuru sırası, sonra oya göre (eşitlikte sıra korunur)', () => {
    const before = election({ candidates: [cand('a', 'BOARD'), cand('b', 'BOARD'), cand('c', 'PRESIDENT')] })
    expect(candidatesOf(before, 'BOARD').map((c) => c.id)).toEqual(['a', 'b'])
    const after = election({ candidates: [cand('a', 'BOARD', 4), cand('b', 'BOARD', 9), cand('d', 'BOARD', 4)] })
    expect(candidatesOf(after, 'BOARD').map((c) => c.id)).toEqual(['b', 'a', 'd'])
  })
})

describe('ballotsToVote', () => {
  it('yalnız oylamada, adayı olan ve oy verilmemiş kurullar', () => {
    const candidates = [cand('a', 'PRESIDENT'), cand('b', 'BOARD')]
    expect(ballotsToVote(election({ status: 'CANDIDACY', candidates }))).toEqual([])
    expect(ballotsToVote(election({ status: 'VOTING', candidates }))).toEqual(['PRESIDENT', 'BOARD'])
    expect(ballotsToVote(election({ status: 'VOTING', candidates, myBallots: ['PRESIDENT'] }))).toEqual(['BOARD'])
    expect(ballotsToVote(election({ status: 'VOTING', candidates: [cand('a', 'PRESIDENT')] }))).toEqual(['PRESIDENT'])
  })
})

describe('isRunning', () => {
  it('sonuç onayı da sürüyor sayılır', () => {
    expect(isRunning('AWAITING_APPROVAL')).toBe(true)
    expect(isRunning('COMPLETED')).toBe(false)
    expect(isRunning('CANCELLED')).toBe(false)
  })
})
