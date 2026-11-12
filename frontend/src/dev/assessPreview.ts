/**
 * Akademisyen önizlemesi, ikinci parça: kadro yönetimi, değerlendirmeler, teslimler, puanlama, uzatma ve not defteri.
 * Veriler teachPreview'daki öğrenci listeleriyle tutarlıdır; bellekte tutulur. Yalnız `/onizleme?rol=akademisyen` içindir.
 */
import type { InternalAxiosRequestConfig } from 'axios'
import { nowLocalIso, localStamp } from '@/lib/time'
import type { GradeCell } from '@/features/assignments/api'
import type {
  AssignmentChange,
  Extension,
  GradeChange,
  Gradebook,
  GroupSet,
  StaffAssignment,
  SubmissionSummary,
} from '@/features/assignments/staff'
import type { SubmissionVersion } from '@/features/assignments/api'
import type { AcademicianHit } from '@/features/courses/teach'
import { ME_ID, staffOf, students, teaching } from './teachPreview'

type Handler = (m: RegExpMatchArray, config: InternalAxiosRequestConfig) => unknown

const ago = (hours: number) => new Date(Date.now() - hours * 3_600_000).toISOString()
/** Bugünden `days` gün sonra `hh:mm`, bölgesiz yerel ISO. */
function at(days: number, hh: number, mm = 0): string {
  const d = new Date(localStamp(`${nowLocalIso().slice(0, 10)}T00:00:00`) + days * 86_400_000)
  return `${d.toISOString().slice(0, 10)}T${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}:00`
}
const round2 = (n: number) => Math.round(n * 100) / 100

function fail(config: InternalAxiosRequestConfig, status: number, errorCode: string, message: string): never {
  throw Object.assign(new Error(message), {
    isAxiosError: true,
    config,
    response: { status, statusText: '', headers: {}, config, data: { status, errorCode, message } },
  })
}
const body = <T>(config: InternalAxiosRequestConfig) => JSON.parse(String(config.data)) as T

// ——— Akademisyen arama ———

const ACADEMIC_TITLE: Record<string, string> = {
  'Arş. Gör.': 'RESEARCH_ASSISTANT',
  'Doç. Dr.': 'ASSOCIATE_PROFESSOR',
  'Dr. Öğr. Üyesi': 'ASSISTANT_PROFESSOR',
  'Prof. Dr.': 'PROFESSOR',
  'Öğr. Gör.': 'LECTURER',
}
const hit = (id: string, firstName: string, lastName: string, title: string, department = 'Bilgisayar Mühendisliği'): AcademicianHit => ({
  id,
  firstName,
  lastName,
  title,
  academicTitle: ACADEMIC_TITLE[title] ?? null,
  department,
  departmentId: department === 'Bilgisayar Mühendisliği' ? 'd1' : 'd2',
})
const academicians: AcademicianHit[] = [
  hit('s2', 'Deniz', 'Yılmaz', 'Arş. Gör.'),
  hit('s3', 'Ayşe', 'Arslan', 'Doç. Dr.'),
  hit('s5', 'Selin', 'Koç', 'Dr. Öğr. Üyesi'),
  hit('s6', 'Kemal', 'Aydın', 'Prof. Dr.', 'Matematik'),
  hit('s7', 'Burak', 'Şen', 'Arş. Gör.'),
  hit('s8', 'Seda', 'Kaplan', 'Öğr. Gör.', 'Matematik'),
]
/** Sunucudaki arama anahtarı gibi: Türkçe büyütme, İ→I, tam ad. */
const searchKey = (s: string) => s.trim().replace(/\s+/g, ' ').toLocaleUpperCase('tr-TR').replace(/İ/g, 'I')

// ——— Değerlendirmeler ———

const assessment = (a: Partial<StaffAssignment> & Pick<StaffAssignment, 'id' | 'courseId' | 'title' | 'dueDate'>): StaffAssignment => ({
  description: null,
  fileUrl: null,
  type: 'HOMEWORK',
  aiPolicy: 'GUIDANCE',
  weight: 10,
  maxPoints: 100,
  gradesPublishedAt: null,
  lateUntil: null,
  latePenaltyPercent: 0,
  groupSetId: null,
  ...a,
})

export const assessments: StaffAssignment[] = [
  assessment({
    id: 't1',
    courseId: 'c1',
    title: 'Ödev 1: Yığın ve kuyruk',
    description: 'Dizi tabanlı yığın ve döngüsel kuyruk gerçekleyin; her işlem için karmaşıklığı yorum satırında açıklayın.',
    dueDate: at(-14, 23, 59),
    lateUntil: at(-12, 23, 59),
    latePenaltyPercent: 20,
    gradesPublishedAt: ago(24 * 9),
  }),
  assessment({
    id: 't2',
    courseId: 'c1',
    title: 'Bağlı liste uygulaması',
    type: 'LAB',
    description: 'Çift yönlü bağlı liste: ekleme, silme, ters çevirme. Testleri geçen kodu ve kısa bir rapor teslim edin.',
    dueDate: at(-2, 23, 59),
    aiPolicy: 'ALLOWED_WITH_DISCLOSURE',
    maxPoints: 20,
  }),
  assessment({ id: 't3', courseId: 'c1', title: 'Ara sınav', type: 'MIDTERM', dueDate: at(9, 13, 30), weight: 30, aiPolicy: 'NONE' }),
  assessment({
    id: 't4',
    courseId: 'c1',
    title: 'Dönem projesi',
    type: 'PROJECT',
    dueDate: at(40, 23, 59),
    weight: 30,
    groupSetId: 'g1',
    aiPolicy: 'ALLOWED_WITH_DISCLOSURE',
  }),
  assessment({ id: 't21', courseId: 'c21', title: 'Lab 3: Döngüler', type: 'LAB', dueDate: at(3, 17, 0), weight: 5, maxPoints: 10 }),
]

export const groupSets: GroupSet[] = [
  {
    id: 'g1',
    courseId: 'c1',
    name: 'Proje grupları',
    selfSignup: true,
    maxMembers: 6,
    signupClosesAt: at(5, 23, 59),
    signupOpen: true,
    groups: Array.from({ length: 9 }, (_, i) => {
      const members = (students.c1 ?? []).slice(i * 6, i < 8 ? i * 6 + 6 : i * 6).map((s) => ({
        studentId: s.studentId,
        name: `${s.firstName} ${s.lastName}`,
        studentNumber: s.studentNumber,
        joinedAt: ago(24 * (10 - i)),
      }))
      return { id: `g1-${i + 1}`, name: `Grup ${i + 1}`, memberCount: members.length, members }
    }),
  },
]
const findGroup = (id: string) => {
  for (const s of groupSets) {
    const g = s.groups.find((x) => x.id === id)
    if (g) return { set: s, group: g }
  }
  throw new Error('grup yok')
}
const recount = (s: GroupSet) => s.groups.forEach((g) => (g.memberCount = g.members.length))

// ——— Teslimler ———

const TEXTS = [
  'Ters çevirmeyi yinelemeli yazdım; her adımda prev, current ve next işaretçilerini kaydırıyorum. Testlerin hepsi geçti. Silmede baş ve kuyruk düğümlerini ayrıca ele aldım.',
  'Rapor ekte. Ekleme O(1), arama O(n). Silme işleminde düğüm bulunamazsa false döndürüyorum.',
]
const AI_NOTES = [
  'Hata ayıklarken bir sınır durumunu anlamak için yapay zekâya danıştım; kodu kendim yazdım.',
  'Rapor metnini düzeltmek için kullandım.',
]

function submissionsFor(
  assignmentId: string,
  courseId: string,
  count: number,
  graded: number,
  hoursAgo: number,
  max: number,
): SubmissionSummary[] {
  const roster = students[courseId] ?? []
  return roster.slice(0, count).map((s, i) => {
    const a = assessments.find((x) => x.id === assignmentId)!
    // Geç teslim yalnız geç teslim penceresi olan değerlendirmede olabilir.
    const late = !!a.lateUntil && i % 11 === 5
    const grade = i < graded ? round2(max * (0.55 + ((i * 37) % 45) / 100)) : null
    return {
      submissionId: `${assignmentId}-s${i}`,
      studentId: s.studentId,
      studentName: `${s.firstName} ${s.lastName}`,
      studentNumber: s.studentNumber,
      submissionFileUrl: i % 3 === 0 ? null : `teslimler/${assignmentId}/${s.studentNumber}.zip`,
      submittedAt: ago(hoursAgo + (i % 9) * 3),
      grade,
      finalGrade: grade == null ? null : late && a.latePenaltyPercent ? round2((grade * (100 - a.latePenaltyPercent)) / 100) : grade,
      textContent: i % 3 === 0 ? TEXTS[i % 2]! : null,
      groupId: null,
      groupName: null,
      late,
      aiUsed: a.aiPolicy === 'ALLOWED_WITH_DISCLOSURE' ? i % 4 === 1 : null,
      aiNote: a.aiPolicy === 'ALLOWED_WITH_DISCLOSURE' && i % 4 === 1 ? AI_NOTES[i % 2]! : null,
      feedback:
        grade == null ? null : i % 2 ? 'Karmaşıklık açıklamaları eksik; sınır durumlarını test edin.' : 'Temiz ve okunur bir çözüm.',
    }
  })
}

export const submissions: Record<string, SubmissionSummary[]> = {
  t1: submissionsFor('t1', 'c1', 50, 50, 24 * 14, 100),
  t2: submissionsFor('t2', 'c1', 46, 30, 50, 20),
  t3: [],
  t4: groupSets[0]!.groups.slice(0, 3).map((g, i) => ({
    submissionId: `t4-g${i + 1}`,
    studentId: g.members[0]!.studentId,
    studentName: g.members[0]!.name,
    studentNumber: g.members[0]!.studentNumber,
    submissionFileUrl: `teslimler/t4/grup-${i + 1}.zip`,
    submittedAt: ago(30 + i * 20),
    grade: i === 0 ? 85 : null,
    finalGrade: i === 0 ? 85 : null,
    textContent: 'Ara rapor ve çalışan ilk sürüm ekte. Görev dağılımı raporun son sayfasında.',
    groupId: g.id,
    groupName: g.name,
    late: false,
    aiUsed: false,
    aiNote: null,
    feedback: i === 0 ? 'İyi bir başlangıç; test kapsamını genişletin.' : null,
    members: g.members.map((m, k) => ({
      studentId: m.studentId,
      studentName: m.name,
      studentNumber: m.studentNumber,
      personalGrade: i === 0 && k === 5 ? 70 : null,
      grade: i === 0 ? (k === 5 ? 70 : 85) : null,
      finalGrade: i === 0 ? (k === 5 ? 70 : 85) : null,
    })),
  })),
  t21: submissionsFor('t21', 'c21', 12, 0, 6, 10),
}

export const versions: Record<string, SubmissionVersion[]> = {
  't2-s0': [
    { versionNo: 1, fileUrl: 'teslimler/t2/ilk.zip', textContent: null, submittedAt: ago(80), late: false, submittedBy: null },
    { versionNo: 2, fileUrl: 'teslimler/t2/son.zip', textContent: TEXTS[0]!, submittedAt: ago(52), late: false, submittedBy: null },
  ],
}

export const gradeHistory: Record<string, GradeChange[]> = {}
for (const s of submissions.t1!) {
  gradeHistory[s.submissionId] = [
    {
      oldGrade: null,
      newGrade: s.grade,
      feedbackChanged: true,
      afterPublication: false,
      reason: null,
      changedBy: ME_ID,
      changedAt: ago(24 * 10),
      studentId: null,
    },
  ]
}

const roster2 = students.c1!
export const extensions: Record<string, Extension[]> = {
  t2: [
    {
      studentId: roster2[50]!.studentId,
      studentName: `${roster2[50]!.firstName} ${roster2[50]!.lastName}`,
      studentNumber: roster2[50]!.studentNumber,
      dueDate: at(3, 23, 59),
      lateUntil: null,
      reason: 'Sağlık raporu (3 gün).',
      grantedBy: ME_ID,
      grantedAt: ago(30),
    },
  ],
}

export const changes: Record<string, AssignmentChange[]> = {
  t2: [
    { field: 'dueDate', oldValue: at(-4, 23, 59), newValue: at(-2, 23, 59), changedBy: ME_ID, changedAt: ago(24 * 6) },
    { field: 'maxPoints', oldValue: '100', newValue: '20', changedBy: 's2', changedAt: ago(24 * 12) },
  ],
}

// ——— Not defteri ———

function gradebook(courseId: string): Gradebook {
  const list = assessments.filter((a) => a.courseId === courseId).sort((x, y) => x.dueDate.localeCompare(y.dueDate))
  const rows = (students[courseId] ?? []).map((s) => {
    const cells: GradeCell[] = list.map((a) => {
      const sub = submissions[a.id]?.find((x) => x.studentId === s.studentId || x.members?.some((m) => m.studentId === s.studentId))
      const member = sub?.members?.find((m) => m.studentId === s.studentId)
      if (sub && member) {
        return {
          assignmentId: a.id,
          submissionId: sub.submissionId,
          status: member.grade == null ? 'SUBMITTED' : 'GRADED',
          grade: member.grade,
          finalGrade: member.finalGrade,
          late: sub.late,
          submittedAt: sub.submittedAt,
        }
      }
      if (!sub)
        return {
          assignmentId: a.id,
          submissionId: null,
          status: 'NOT_SUBMITTED',
          grade: null,
          finalGrade: null,
          late: false,
          submittedAt: null,
        }
      return {
        assignmentId: a.id,
        submissionId: sub.submissionId,
        status: sub.grade == null ? 'SUBMITTED' : 'GRADED',
        grade: sub.grade,
        finalGrade: sub.finalGrade,
        late: sub.late,
        submittedAt: sub.submittedAt,
      }
    })
    let total = 0
    let weight = 0
    list.forEach((a, i) => {
      const c = cells[i]!
      if (c.status === 'GRADED') {
        total += ((c.finalGrade ?? c.grade ?? 0) / a.maxPoints) * a.weight
        weight += a.weight
      }
    })
    return {
      studentId: s.studentId,
      studentName: `${s.firstName} ${s.lastName}`,
      studentNumber: s.studentNumber,
      grades: cells,
      weightedTotal: round2(total),
      gradedWeight: weight,
      missing: cells.filter((c) => c.status === 'NOT_SUBMITTED').length,
    }
  })
  rows.sort((x, y) => (x.studentNumber ?? '').localeCompare(y.studentNumber ?? ''))
  return {
    courseId,
    totalWeight: list.reduce((n, a) => n + a.weight, 0),
    assessments: list.map((a) => ({
      id: a.id,
      title: a.title,
      type: a.type,
      weight: a.weight,
      maxPoints: a.maxPoints,
      dueDate: a.dueDate,
      gradesPublished: !!a.gradesPublishedAt,
    })),
    students: rows,
  }
}

function csv(courseId: string) {
  const b = gradebook(courseId)
  const head = ['Öğrenci No', 'Ad Soyad', ...b.assessments.map((a) => `${a.title} (%${a.weight}, ${a.maxPoints} puan)`), 'Ağırlıklı Toplam']
  const lines = b.students.map((r) => [
    r.studentNumber ?? '',
    r.studentName ?? '',
    ...r.grades.map((g) => (g.status === 'NOT_SUBMITTED' ? '-' : String(g.finalGrade ?? ''))),
    String(r.weightedTotal),
  ])
  return new Blob(['﻿' + [head, ...lines].map((l) => l.join(';')).join('\n')], { type: 'text/csv' })
}

const findAssessment = (id: string) => assessments.find((a) => a.id === id)!
const findSubmission = (id: string) =>
  Object.values(submissions)
    .flat()
    .find((s) => s.submissionId === id)!

// ——— Tablolar ———

export const GET: [RegExp, Handler][] = [
  [
    /^\/users\/search\/academicians$/,
    (_m, config) => {
      const q = searchKey(String((config.params as { query?: string } | undefined)?.query ?? ''))
      if (q.length < 2) return []
      return academicians.filter((a) => searchKey(`${a.firstName} ${a.lastName}`).includes(q)).slice(0, 10)
    },
  ],
  [/^\/assignments\/course\/([^/]+)\/gradebook$/, (m) => gradebook(m[1]!)],
  [/^\/assignments\/course\/([^/]+)\/gradebook\.csv$/, (m) => csv(m[1]!)],
  [/^\/assignments\/course\/([^/]+)\/group-sets$/, (m) => groupSets.filter((g) => g.courseId === m[1])],
  [/^\/assignments\/course\/([^/]+)$/, (m) => assessments.filter((a) => a.courseId === m[1]).map((a) => ({ ...a }))],
  [
    /^\/assignments\/submissions\/([^/]+)\/versions$/,
    (m) =>
      versions[m[1]!] ?? [
        { versionNo: 1, fileUrl: null, textContent: null, submittedAt: findSubmission(m[1]!).submittedAt, late: false, submittedBy: null },
      ],
  ],
  [/^\/assignments\/submissions\/([^/]+)\/grade-history$/, (m) => [...(gradeHistory[m[1]!] ?? [])].reverse()],
  [/^\/assignments\/submissions\/([^/]+)\/file$/, () => new Blob(['Önizleme teslim dosyası'], { type: 'text/plain' })],
  [/^\/assignments\/files\/download$/, () => new Blob(['Önizleme sürüm dosyası'], { type: 'text/plain' })],
  [/^\/assignments\/([^/]+)\/submissions$/, (m) => (submissions[m[1]!] ?? []).map((s) => ({ ...s }))],
  [/^\/assignments\/([^/]+)\/changes$/, (m) => [...(changes[m[1]!] ?? [])]],
  [/^\/assignments\/([^/]+)\/extensions$/, (m) => [...(extensions[m[1]!] ?? [])]],
]

export const WRITE: [string, RegExp, Handler][] = [
  // Kadro
  [
    'post',
    /^\/courses\/([^/]+)\/staff$/,
    (m, config) => {
      const { userId, role } = body<{ userId: string; role: 'INSTRUCTOR' | 'ASSISTANT' }>(config)
      const list = staffOf(m[1]!)
      if (list.some((s) => s.userId === userId)) fail(config, 409, 'STAFF_EXISTS', 'Bu kullanıcı zaten dersin kadrosunda.')
      const a = academicians.find((x) => x.id === userId)!
      list.push({
        userId,
        role,
        name: `${a.firstName} ${a.lastName}`,
        title: a.title,
        academicTitle: a.academicTitle,
        department: a.department,
        since: new Date().toISOString(),
      })
      return list
    },
  ],
  [
    'put',
    /^\/courses\/([^/]+)\/staff\/([^/]+)$/,
    (m, config) => {
      const list = staffOf(m[1]!)
      list.find((s) => s.userId === m[2])!.role = body<{ role: 'INSTRUCTOR' | 'ASSISTANT' }>(config).role
      return list
    },
  ],
  [
    'delete',
    /^\/courses\/([^/]+)\/staff\/([^/]+)$/,
    (m) => {
      const list = staffOf(m[1]!)
      list.splice(
        list.findIndex((s) => s.userId === m[2]),
        1,
      )
      if (m[2] === ME_ID)
        teaching.splice(
          teaching.findIndex((c) => c.id === m[1]),
          1,
        )
      return null
    },
  ],
  // Grup setleri
  [
    'post',
    /^\/assignments\/course\/([^/]+)\/group-sets$/,
    (m, config) => {
      const b = body<{ name: string; selfSignup: boolean; maxMembers: number | null; signupClosesAt: string | null }>(config)
      if (groupSets.some((s) => s.courseId === m[1] && s.name === b.name))
        fail(config, 409, 'GROUP_SET_EXISTS', 'Bu derste aynı adlı bir grup seti var.')
      groupSets.push({
        id: 'g-' + Date.now(),
        courseId: m[1]!,
        name: b.name,
        selfSignup: b.selfSignup,
        maxMembers: b.maxMembers,
        signupClosesAt: b.signupClosesAt,
        signupOpen: b.selfSignup && (!b.signupClosesAt || b.signupClosesAt > nowLocalIso()),
        groups: [],
      })
      return groupSets.filter((s) => s.courseId === m[1])
    },
  ],
  [
    'put',
    /^\/assignments\/group-sets\/([^/]+)$/,
    (m, config) => {
      const s = groupSets.find((x) => x.id === m[1])!
      const b = body<{ name: string; selfSignup: boolean; maxMembers: number | null; signupClosesAt: string | null }>(config)
      Object.assign(s, b, { signupOpen: b.selfSignup && (!b.signupClosesAt || b.signupClosesAt > nowLocalIso()) })
      return groupSets.filter((x) => x.courseId === s.courseId)
    },
  ],
  [
    'delete',
    /^\/assignments\/group-sets\/([^/]+)$/,
    (m, config) => {
      if (assessments.some((a) => a.groupSetId === m[1]))
        fail(config, 409, 'GROUP_SET_IN_USE', 'Bu grup seti bir ödevde kullanılıyor; önce ödevin grup ayarını kaldırın.')
      groupSets.splice(
        groupSets.findIndex((s) => s.id === m[1]),
        1,
      )
      return null
    },
  ],
  [
    'post',
    /^\/assignments\/group-sets\/([^/]+)\/groups$/,
    (m, config) => {
      const s = groupSets.find((x) => x.id === m[1])!
      const { name } = body<{ name: string }>(config)
      if (s.groups.some((g) => g.name === name)) fail(config, 409, 'GROUP_EXISTS', 'Bu sette aynı adlı bir grup var.')
      s.groups.push({ id: `${s.id}-${Date.now()}`, name, memberCount: 0, members: [] })
      return groupSets.filter((x) => x.courseId === s.courseId)
    },
  ],
  [
    'put',
    /^\/assignments\/groups\/([^/]+)\/members\/([^/]+)$/,
    (m, config) => {
      const { set, group } = findGroup(m[1]!)
      if (set.maxMembers && group.members.length >= set.maxMembers) fail(config, 409, 'GROUP_FULL', 'Grup kontenjanı dolu.')
      const st = students[set.courseId]!.find((x) => x.studentId === m[2])!
      for (const g of set.groups) g.members = g.members.filter((x) => x.studentId !== m[2])
      group.members.push({
        studentId: st.studentId,
        name: `${st.firstName} ${st.lastName}`,
        studentNumber: st.studentNumber,
        joinedAt: new Date().toISOString(),
      })
      recount(set)
      return groupSets.filter((x) => x.courseId === set.courseId)
    },
  ],
  [
    'delete',
    /^\/assignments\/groups\/([^/]+)\/members\/([^/]+)$/,
    (m) => {
      const { set, group } = findGroup(m[1]!)
      group.members = group.members.filter((x) => x.studentId !== m[2])
      recount(set)
      return null
    },
  ],
  [
    'delete',
    /^\/assignments\/groups\/([^/]+)$/,
    (m, config) => {
      const { set, group } = findGroup(m[1]!)
      if (
        Object.values(submissions)
          .flat()
          .some((s) => s.groupId === group.id)
      )
        fail(config, 409, 'GROUP_HAS_SUBMISSIONS', 'Teslim yapmış grup silinemez.')
      set.groups = set.groups.filter((g) => g.id !== group.id)
      return null
    },
  ],
  // Kişisel puan
  [
    'put',
    /^\/assignments\/submissions\/([^/]+)\/members\/([^/]+)\/grade$/,
    (m, config) => {
      const s = findSubmission(m[1]!)
      const b = body<{ grade: number; reason: string }>(config)
      const member = s.members!.find((x) => x.studentId === m[2])!
      ;(gradeHistory[s.submissionId] ??= []).push({
        oldGrade: member.grade,
        newGrade: b.grade,
        feedbackChanged: false,
        afterPublication: false,
        reason: b.reason,
        changedBy: ME_ID,
        changedAt: new Date().toISOString(),
        studentId: member.studentId,
      })
      Object.assign(member, { personalGrade: b.grade, grade: b.grade, finalGrade: b.grade })
      return 'Kişisel puan verildi'
    },
  ],
  [
    'delete',
    /^\/assignments\/submissions\/([^/]+)\/members\/([^/]+)\/grade$/,
    (m) => {
      const s = findSubmission(m[1]!)
      const member = s.members!.find((x) => x.studentId === m[2])!
      ;(gradeHistory[s.submissionId] ??= []).push({
        oldGrade: member.personalGrade,
        newGrade: s.grade,
        feedbackChanged: false,
        afterPublication: false,
        reason: null,
        changedBy: ME_ID,
        changedAt: new Date().toISOString(),
        studentId: member.studentId,
      })
      Object.assign(member, { personalGrade: null, grade: s.grade, finalGrade: s.finalGrade })
      return null
    },
  ],
  // Değerlendirmeler
  [
    'post',
    /^\/assignments$/,
    async (_m, config) => {
      const b = JSON.parse(await ((config.data as FormData).get('assignment') as Blob).text()) as Omit<StaffAssignment, 'id'>
      const used = assessments.filter((a) => a.courseId === b.courseId).reduce((n, a) => n + a.weight, 0)
      if (used + (b.weight ?? 0) > 100)
        fail(config, 409, 'WEIGHT_EXCEEDED', `Dersteki değerlendirmelerin ağırlık toplamı %100'ü geçemez (kalan: %${100 - used}).`)
      const file = (config.data as FormData).get('file') as File | null
      const a = assessment({ ...b, id: 't-' + Date.now(), fileUrl: file ? `odevler/${file.name}` : null })
      assessments.push(a)
      submissions[a.id] = []
      return a
    },
  ],
  [
    'put',
    /^\/assignments\/submissions\/([^/]+)\/grade$/,
    (m, config) => {
      const s = findSubmission(m[1]!)
      const a = Object.entries(submissions).find(([, list]) => list.includes(s))![0]
      const asg = findAssessment(a)
      const b = body<{ grade: number; feedback: string | null; reason: string | null }>(config)
      if (b.grade < 0 || b.grade > asg.maxPoints) fail(config, 400, 'INVALID_GRADE', `Puan 0 ile ${asg.maxPoints} arasında olmalı.`)
      const changed = s.grade !== b.grade
      if (asg.gradesPublishedAt && s.grade != null && changed && !b.reason)
        fail(config, 400, 'GRADE_CHANGE_REASON_REQUIRED', 'İlan edilmiş bir puanı değiştirmek için gerekçe yazılmalı.')
      ;(gradeHistory[s.submissionId] ??= []).push({
        oldGrade: s.grade,
        newGrade: b.grade,
        feedbackChanged: b.feedback !== null && (s.feedback ?? '') !== b.feedback,
        afterPublication: !!asg.gradesPublishedAt,
        reason: b.reason,
        changedBy: ME_ID,
        changedAt: new Date().toISOString(),
        studentId: null,
      })
      for (const m of s.members ?? []) if (m.personalGrade == null) Object.assign(m, { grade: b.grade, finalGrade: b.grade })
      Object.assign(s, {
        grade: b.grade,
        feedback: b.feedback === null ? s.feedback : b.feedback || null,
        finalGrade: s.late && asg.latePenaltyPercent ? round2((b.grade * (100 - asg.latePenaltyPercent)) / 100) : b.grade,
      })
      return 'Not başarıyla verildi'
    },
  ],
  [
    'post',
    /^\/assignments\/([^/]+)\/publish-grades$/,
    (m, config) => {
      const a = findAssessment(m[1]!)
      if (a.gradesPublishedAt) fail(config, 409, 'GRADES_ALREADY_PUBLISHED', 'Bu değerlendirmenin puanları zaten ilan edildi.')
      a.gradesPublishedAt = new Date().toISOString()
      return { ...a }
    },
  ],
  [
    'put',
    /^\/assignments\/([^/]+)\/extensions\/([^/]+)$/,
    (m, config) => {
      const a = findAssessment(m[1]!)
      const b = body<{ dueDate: string; reason: string | null }>(config)
      if (b.dueDate <= a.dueDate) fail(config, 400, 'EXTENSION_NOT_LATER', 'Uzatılan tarih son teslim tarihinden sonra olmalı.')
      const s = students[a.courseId]!.find((x) => x.studentId === m[2])!
      const list = (extensions[a.id] ??= [])
      const e: Extension = {
        studentId: s.studentId,
        studentName: `${s.firstName} ${s.lastName}`,
        studentNumber: s.studentNumber,
        dueDate: b.dueDate,
        lateUntil: null,
        reason: b.reason,
        grantedBy: ME_ID,
        grantedAt: new Date().toISOString(),
      }
      const i = list.findIndex((x) => x.studentId === s.studentId)
      if (i >= 0) list[i] = e
      else list.push(e)
      return e
    },
  ],
  [
    'delete',
    /^\/assignments\/([^/]+)\/extensions\/([^/]+)$/,
    (m) => {
      extensions[m[1]!] = (extensions[m[1]!] ?? []).filter((e) => e.studentId !== m[2])
      return null
    },
  ],
  [
    'put',
    /^\/assignments\/([^/]+)$/,
    (m, config) => {
      const a = findAssessment(m[1]!)
      const b = body<Record<string, unknown>>(config)
      const used = assessments.filter((x) => x.courseId === a.courseId && x.id !== a.id).reduce((n, x) => n + x.weight, 0)
      if (typeof b.weight === 'number' && used + b.weight > 100)
        fail(config, 409, 'WEIGHT_EXCEEDED', `Dersteki değerlendirmelerin ağırlık toplamı %100'ü geçemez (kalan: %${100 - used}).`)
      const FIELD: Record<string, string> = { latePenaltyPercent: 'latePenalty', groupSetId: 'groupSet' }
      const now = new Date().toISOString()
      for (const [k, v] of Object.entries(b)) {
        if (k === 'clearLateUntil' || k === 'clearGroupSet') continue
        ;(changes[a.id] ??= []).push({
          field: FIELD[k] ?? k,
          oldValue: (a as Record<string, unknown>)[k] == null ? null : String((a as Record<string, unknown>)[k]),
          newValue: v == null ? null : String(v),
          changedBy: ME_ID,
          changedAt: now,
        })
      }
      if (b.clearLateUntil) {
        ;(changes[a.id] ??= []).push({ field: 'lateUntil', oldValue: a.lateUntil, newValue: null, changedBy: ME_ID, changedAt: now })
        a.lateUntil = null
      }
      if (b.clearGroupSet) a.groupSetId = null
      Object.assign(a, Object.fromEntries(Object.entries(b).filter(([k]) => !k.startsWith('clear'))))
      return { ...a }
    },
  ],
  [
    'delete',
    /^\/assignments\/([^/]+)$/,
    (m, config) => {
      if ((submissions[m[1]!] ?? []).length) fail(config, 409, 'ASSIGNMENT_HAS_SUBMISSIONS', 'Teslim alınmış değerlendirme silinemez.')
      assessments.splice(
        assessments.findIndex((a) => a.id === m[1]),
        1,
      )
      return null
    },
  ],
]
