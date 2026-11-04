/**
 * Geliştirme önizlemesi için örnek veri. Biçimler gerçek API yanıtlarıyla aynıdır; tarihler bugüne göre üretilir.
 * Yalnız `/onizleme` yolunda ve geliştirme sunucusunda kullanılır, üretim paketine girmez.
 */
import { localStamp, nowLocalIso } from '@/lib/time'
import type { EnrolledCourse, Course, CourseStaff, Material, Announcement, CourseApplication, Term } from '@/features/courses/api'
import type { MyAssignment, MyGrades } from '@/features/assignments/api'
import type { GroupSet } from '@/features/assignments/staff'
import type { MyRegistration, CampusEvent, ParticipationRequest } from '@/features/events/api'
import type { AppNotification } from '@/features/notifications/api'
import type { GamificationSummary } from '@/features/gamification/api'
import type { Me } from '@/features/me/useMe'
import type { ClubAnnouncement, ClubDetails, ClubMember, ClubSummary, MembershipRequest, MyMembership, PositionTerm } from '@/features/clubs/api'
import type { ApprovalRequest, ClubAccess, PendingMembershipRequest } from '@/features/clubs/manage'

/** Bugünden `days` gün sonra (eksiyse önce) saat `hh:mm`, bölgesiz yerel ISO. */
function at(days: number, hh: number, mm = 0): string {
  const today = nowLocalIso().slice(0, 10)
  const d = new Date(localStamp(`${today}T00:00:00`) + days * 86_400_000)
  return `${d.toISOString().slice(0, 10)}T${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}:00`
}

/** UTC kayıt anı: şu andan `hours` saat önce. */
function ago(hours: number): string {
  return new Date(Date.now() - hours * 3_600_000).toISOString()
}

export const term: Term = {
  id: 't1',
  academicYear: 2027,
  season: 'FALL',
  label: '2026-2027 Güz',
  startsOn: '2026-09-14',
  endsOn: '2027-01-31',
  enrollmentOpensOn: at(-10, 0).slice(0, 10),
  enrollmentClosesOn: at(6, 0).slice(0, 10),
}

const course = (c: Partial<EnrolledCourse> & Pick<EnrolledCourse, 'id' | 'code' | 'title' | 'instructorName'>): EnrolledCourse => ({
  description: null,
  credit: 3,
  semester: null,
  termId: 't1',
  termLabel: '2026-2027 Güz',
  section: '1',
  ects: 5,
  status: 'ACTIVE',
  imageUrl: null,
  instructorId: 'u-' + c.id,
  enrollmentDate: ago(24 * 20),
  ...c,
})

export const courses: EnrolledCourse[] = [
  course({
    id: 'c1',
    code: 'BİL 301',
    title: 'Veri Yapıları ve Algoritmalar',
    instructorName: 'Mehmet Kaya',
    instructorTitle: 'Dr. Öğr. Üyesi',
    instructorAcademicTitle: 'ASSISTANT_PROFESSOR',
    credit: 4,
    ects: 6,
    description:
      'Diziler, bağlı listeler, yığın ve kuyruk, ağaçlar, öncelik kuyrukları ve çizgeler. Algoritma çözümlemesi ve büyük O gösterimi; her hafta bir uygulama oturumu.',
  }),
  course({
    id: 'c2',
    code: 'BİL 304',
    title: 'Veri Tabanı Sistemleri',
    instructorName: 'Ayşe Arslan',
    instructorTitle: 'Doç. Dr.',
    instructorAcademicTitle: 'ASSOCIATE_PROFESSOR',
    description: 'İlişkisel model, SQL, normalizasyon, işlem yönetimi ve indeksleme. Dönem projesi olarak bir kampüs uygulamasının veri modeli tasarlanır.',
  }),
  course({
    id: 'c3',
    code: 'MAT 201',
    title: 'Lineer Cebir',
    instructorName: 'Hakan Öztürk',
    instructorAcademicTitle: 'PROFESSOR',
    section: '2',
    description: 'Vektör uzayları, doğrusal dönüşümler, matrisler, determinant, özdeğer ve özvektörler.',
  }),
  course({
    id: 'c9',
    code: 'MAT 101',
    title: 'Analiz I',
    instructorName: 'Zeynep Çelik',
    instructorTitle: 'Dr. Öğr. Üyesi',
    termId: 't0',
    termLabel: '2025-2026 Bahar',
    status: 'COMPLETED',
  }),
]

export const courseDetail = (id: string): Course | undefined => {
  const c = courses.find((x) => x.id === id)
  if (!c) return undefined
  const rest: Omit<EnrolledCourse, 'enrollmentDate'> & { enrollmentDate?: string } = { ...c }
  delete rest.enrollmentDate
  return { ...rest, capacity: 60, enrolledStudentCount: id === 'c1' ? 54 : 41 }
}

export const terms: Term[] = [
  term,
  {
    id: 't0',
    academicYear: 2026,
    season: 'SPRING',
    label: '2025-2026 Bahar',
    startsOn: '2026-02-16',
    endsOn: '2026-06-30',
    enrollmentOpensOn: '2026-02-02',
    enrollmentClosesOn: '2026-02-20',
  },
]

const offered = (c: Partial<Course> & Pick<Course, 'id' | 'code' | 'title' | 'instructorName'>): Course => ({
  description: null,
  credit: 3,
  semester: null,
  termId: 't1',
  termLabel: '2026-2027 Güz',
  section: '1',
  ects: 5,
  status: 'OPEN',
  imageUrl: null,
  instructorId: 'u-' + c.id,
  instructorTitle: null,
  instructorAcademicTitle: null,
  capacity: 60,
  enrolledStudentCount: 30,
  ...c,
})

/** Bu dönem açılan ve kayıtlı olunmayan dersler (katalog için). */
const offeredNow: Course[] = [
  offered({
    id: 'c7',
    code: 'İST 202',
    title: 'Olasılık ve İstatistik',
    instructorName: 'Selin Koç',
    instructorTitle: 'Doç. Dr.',
    capacity: 45,
    enrolledStudentCount: 38,
  }),
  offered({
    id: 'c10',
    code: 'BİL 305',
    title: 'Yazılım Mühendisliği',
    instructorName: 'Kemal Aydın',
    instructorAcademicTitle: 'PROFESSOR',
    credit: 3,
    ects: 6,
    capacity: 50,
    enrolledStudentCount: 22,
    description:
      'Gereksinim çözümlemesi, tasarım örüntüleri, sürüm denetimi ve test. Dönem boyunca dört kişilik ekiplerle bir proje geliştirilir; her iki haftada bir ara teslim vardır.',
  }),
  offered({
    id: 'c11',
    code: 'BİL 310',
    title: 'İşletim Sistemleri',
    instructorName: 'Ayşe Arslan',
    instructorTitle: 'Doç. Dr.',
    credit: 4,
    ects: 6,
    capacity: 40,
    enrolledStudentCount: 40,
    description: 'Süreçler ve iş parçacıkları, zamanlama, eşzamanlılık, bellek yönetimi ve dosya sistemleri. Uygulamalar C ile yapılır.',
  }),
  offered({
    id: 'c12',
    code: 'BİL 321',
    title: 'Bilgisayar Ağları',
    instructorName: 'Burak Şahin',
    instructorTitle: 'Öğr. Gör. Dr.',
    section: '2',
    capacity: 35,
    enrolledStudentCount: 12,
  }),
  offered({
    id: 'c13',
    code: 'MAT 202',
    title: 'Diferansiyel Denklemler',
    instructorName: 'Hakan Öztürk',
    instructorTitle: 'Prof. Dr.',
    credit: 4,
    capacity: 80,
    enrolledStudentCount: 63,
  }),
  offered({
    id: 'c14',
    code: 'FİZ 102',
    title: 'Fizik II',
    instructorName: 'Elif Aksoy',
    instructorTitle: 'Doç. Dr.',
    status: 'ACTIVE',
    credit: 4,
    ects: 6,
    capacity: 120,
    enrolledStudentCount: 97,
  }),
  offered({
    id: 'c15',
    code: 'TÜR 101',
    title: 'Türk Dili I',
    instructorName: 'Merve Demirtaş',
    instructorTitle: 'Öğr. Gör.',
    credit: 2,
    ects: 2,
    capacity: 100,
    enrolledStudentCount: 44,
  }),
]

/** `GET /courses?termId=`: kayıtlı olunan dersler katalogda da görünür. */
export function catalog(termId: string | undefined): Course[] {
  const mine = courses.filter((c) => (termId ? c.termId === termId : true)).map((c) => courseDetail(c.id)!)
  return termId === 't0' ? mine : [...mine, ...offeredNow]
}

export const staff: Record<string, CourseStaff[]> = {
  c1: [
    { userId: 's1', role: 'COORDINATOR', name: 'Mehmet Kaya', title: 'Dr. Öğr. Üyesi', academicTitle: 'ASSISTANT_PROFESSOR', department: 'Bilgisayar Mühendisliği', since: ago(24 * 40) },
    { userId: 's2', role: 'ASSISTANT', name: 'Deniz Yılmaz', title: null, academicTitle: 'RESEARCH_ASSISTANT', department: 'Bilgisayar Mühendisliği', since: ago(24 * 30) },
  ],
  c2: [{ userId: 's3', role: 'COORDINATOR', name: 'Ayşe Arslan', title: 'Doç. Dr.', academicTitle: 'ASSOCIATE_PROFESSOR', department: 'Bilgisayar Mühendisliği', since: ago(24 * 40) }],
  c3: [{ userId: 's4', role: 'COORDINATOR', name: 'Hakan Öztürk', title: 'Prof. Dr.', academicTitle: 'PROFESSOR', department: 'Matematik', since: ago(24 * 40) }],
}

const assignment = (a: Partial<MyAssignment> & Pick<MyAssignment, 'id' | 'courseId' | 'title' | 'dueDate'>): MyAssignment => ({
  description: null,
  fileUrl: null,
  type: 'HOMEWORK',
  aiPolicy: 'GUIDANCE',
  weight: 10,
  maxPoints: 100,
  gradesPublished: false,
  latePenaltyPercent: 0,
  effectiveDueDate: a.dueDate,
  effectiveLateUntil: null,
  groupSetId: null,
  groupId: null,
  groupName: null,
  submission: null,
  ...a,
})

const groupMember = (studentId: string, name: string) => ({ studentId, name, studentNumber: null, joinedAt: new Date(Date.now() - 86_400_000).toISOString() })
const groupOf = (id: string, name: string, members: ReturnType<typeof groupMember>[]) => ({ id, name, memberCount: members.length, members })

/** Öğrenci görünümünün grup setleri (F-50). Üyeler burada tam; yanıtta öğrenci yalnız kendi grubunun üyelerini görür. */
export const studentGroupSets: GroupSet[] = [
  {
    id: 'gs1',
    courseId: 'c1',
    name: 'Bağlı liste grupları',
    selfSignup: true,
    maxMembers: 4,
    signupClosesAt: at(-3, 23, 59),
    signupOpen: false,
    myGroupId: 'g3',
    groups: [
      groupOf('g1', 'Grup 1', [groupMember('st20', 'Kerem Aslan'), groupMember('st21', 'Pınar Ünal')]),
      groupOf('g3', 'Grup 3', [groupMember('u1', 'Elif Demir'), groupMember('st2', 'Can Erdem'), groupMember('st3', 'Ece Yalçın')]),
    ],
  },
  {
    id: 'gs2',
    courseId: 'c2',
    name: 'Proje grupları',
    selfSignup: true,
    maxMembers: 4,
    signupClosesAt: at(6, 23, 59),
    signupOpen: true,
    myGroupId: null,
    groups: [
      groupOf('g21', 'Grup A', ['st20', 'st21', 'st22', 'st23'].map((id, i) => groupMember(id, `Öğrenci ${i + 1}`))),
      groupOf('g22', 'Grup B', [groupMember('st2', 'Can Erdem'), groupMember('st4', 'Mert Aksu')]),
      groupOf('g23', 'Grup C', []),
    ],
  },
  {
    id: 'gs3',
    courseId: 'c3',
    name: 'Laboratuvar grupları',
    selfSignup: false,
    maxMembers: null,
    signupClosesAt: null,
    signupOpen: false,
    myGroupId: null,
    groups: [groupOf('g31', 'Lab 1', [groupMember('st3', 'Ece Yalçın')])],
  },
]

export const assignments: MyAssignment[] = [
  assignment({
    id: 'a8',
    courseId: 'c2',
    title: 'Veritabanı projesi',
    dueDate: at(8, 23, 59),
    type: 'PROJECT',
    weight: 30,
    groupSetId: 'gs2',
    description: 'Üç kişilik gruplarla bir kütüphane veritabanı tasarlayın; ER diyagramını ve örnek sorguları teslim edin.',
  }),
  assignment({
    id: 'a1',
    courseId: 'c2',
    title: 'Normalizasyon ödevi',
    dueDate: at(0, 23, 59),
    weight: 15,
    aiPolicy: 'ALLOWED_WITH_DISCLOSURE',
    latePenaltyPercent: 20,
    effectiveLateUntil: at(2, 23, 59),
    fileUrl: 'x',
    description:
      'Ekteki kütüphane tablosunu 3NF düzeyine getirin. Her adımda hangi bağımlılığı kaldırdığınızı bir cümleyle açıklayın; sonucu PDF olarak yükleyin.',
  }),
  assignment({
    id: 'a2',
    courseId: 'c1',
    title: 'Bağlı liste uygulaması',
    dueDate: at(3, 18),
    effectiveDueDate: at(4, 18),
    type: 'PROJECT',
    weight: 25,
    groupSetId: 'gs1',
    groupId: 'g3',
    groupName: 'Grup 3',
    description: 'Tek ve çift yönlü bağlı listeyi aynı arayüzle yazın; ekleme, silme ve ters çevirme işlemlerini birim testleriyle gösterin.',
  }),
  assignment({ id: 'a3', courseId: 'c3', title: 'Kısa sınav 2', dueDate: at(6, 10), type: 'QUIZ', weight: 10, aiPolicy: 'NONE' }),
  assignment({ id: 'a7', courseId: 'c1', title: 'Ara sınav', dueDate: at(9, 13, 30), type: 'MIDTERM', weight: 30, aiPolicy: 'NONE' }),
  assignment({
    id: 'a5',
    courseId: 'c2',
    title: 'ER diyagramı',
    dueDate: at(-2, 18),
    effectiveLateUntil: at(1, 18),
    latePenaltyPercent: 10,
    weight: 10,
  }),
  assignment({
    id: 'a4',
    courseId: 'c1',
    title: 'Ödev 1: Yığın ve kuyruk',
    dueDate: at(-5, 18),
    weight: 15,
    gradesPublished: true,
    submission: {
      submissionId: 'sub4',
      submittedAt: ago(24 * 6),
      grade: 85,
      finalGrade: 85,
      textContent: null,
      feedback: 'Kuyruk taşma durumu güzel ele alınmış. Testlere boş yığın senaryosunu da ekleyin.',
      late: false,
      aiUsed: null,
      aiNote: null,
    },
  }),
  assignment({
    id: 'a6',
    courseId: 'c3',
    title: 'Matris işlemleri',
    dueDate: at(-10, 18),
    effectiveLateUntil: at(-8, 18),
    weight: 10,
    latePenaltyPercent: 20,
    gradesPublished: true,
    submission: {
      submissionId: 'sub6',
      submittedAt: ago(24 * 9),
      grade: 80,
      finalGrade: 64,
      textContent: null,
      feedback: null,
      late: true,
      aiUsed: null,
      aiNote: null,
    },
  }),
]

export function gradesFor(courseId: string): MyGrades {
  const list = assignments.filter((a) => a.courseId === courseId)
  const grades = list.map((a) => ({
    assignmentId: a.id,
    submissionId: a.submission?.submissionId ?? null,
    status: (a.submission ? (a.gradesPublished ? 'GRADED' : 'SUBMITTED') : 'NOT_SUBMITTED') as 'GRADED' | 'SUBMITTED' | 'NOT_SUBMITTED',
    grade: a.gradesPublished ? (a.submission?.grade ?? null) : null,
    finalGrade: a.gradesPublished ? (a.submission?.finalGrade ?? null) : null,
    late: a.submission?.late ?? false,
    submittedAt: a.submission?.submittedAt ?? null,
  }))
  const graded = list.filter((a) => a.gradesPublished && a.submission?.finalGrade != null)
  const weightedTotal = graded.reduce((s, a) => s + ((a.submission!.finalGrade ?? 0) * a.weight) / a.maxPoints, 0)
  return {
    courseId,
    assessments: list.map((a) => ({
      id: a.id,
      title: a.title,
      type: a.type,
      weight: a.weight,
      maxPoints: a.maxPoints,
      dueDate: a.dueDate,
      gradesPublished: a.gradesPublished,
    })),
    grades,
    weightedTotal: Math.round(weightedTotal * 100) / 100,
    gradedWeight: graded.reduce((s, a) => s + a.weight, 0),
  }
}

export const materials: Record<string, Material[]> = {
  c1: [
    { id: 'm1', courseId: 'c1', title: 'Ders notu: Bağlı listeler', description: 'Tek ve çift yönlü bağlı listeler, ekleme ve silme.', section: 'Hafta 3', sortOrder: 1, kind: 'FILE', fileName: 'hafta3-bagli-listeler.pdf', linkUrl: null, visible: true, createdAt: ago(200), updatedAt: ago(150) },
    { id: 'm2', courseId: 'c1', title: 'Görselleştirme aracı', description: 'Veri yapılarını adım adım izlemek için.', section: 'Hafta 3', sortOrder: 2, kind: 'LINK', fileName: null, linkUrl: 'https://visualgo.net', visible: true, createdAt: ago(200), updatedAt: ago(200) },
    { id: 'm3', courseId: 'c1', title: 'Ders notu: Yığın ve kuyruk', description: null, section: 'Hafta 2', sortOrder: 1, kind: 'FILE', fileName: 'hafta2-yigin-kuyruk.pdf', linkUrl: null, visible: true, createdAt: ago(400), updatedAt: ago(380) },
  ],
}

export const announcements: Record<string, Announcement[]> = {
  c1: [{ id: 'n1', courseId: 'c1', title: 'Ara sınav salonları açıklandı', content: 'Soyadı A–K olanlar B201, L–Z olanlar B204 salonunda. Kimlik kartınızı unutmayın.', createdAt: ago(5), createdByName: 'Dr. Öğr. Üyesi Mehmet Kaya' }],
  c2: [{ id: 'n2', courseId: 'c2', title: 'Perşembe dersi çevrim içi', content: 'Bu haftaki ders Teams üzerinden yapılacak; bağlantı ders saatinden önce paylaşılacak.', createdAt: ago(28), createdByName: 'Doç. Dr. Ayşe Arslan' }],
  c3: [],
}

export const applications: CourseApplication[] = [
  { id: 'ap1', courseId: 'c7', courseTitle: 'Olasılık ve İstatistik', courseCode: 'İST 202', status: 'PENDING', applicationDate: ago(30), processedDate: null, rejectionReason: null },
  {
    id: 'ap2',
    courseId: 'c11',
    courseTitle: 'İşletim Sistemleri',
    courseCode: 'BİL 310',
    status: 'REJECTED',
    applicationDate: ago(24 * 8),
    processedDate: ago(24 * 6),
    rejectionReason: 'Önkoşul olan BİL 201 dersini henüz tamamlamadınız.',
  },
  { id: 'ap3', courseId: 'c1', courseTitle: 'Veri Yapıları ve Algoritmalar', courseCode: 'BİL 301', status: 'APPROVED', applicationDate: ago(24 * 22), processedDate: ago(24 * 20), rejectionReason: null },
]

export const registrations: MyRegistration[] = [
  { eventId: 'e1', eventTitle: 'Arduino atölyesi', eventDescription: null, eventDate: at(1, 18, 30), eventLocation: 'B Blok 204', qrCode: '7f3c2a91-4b6e-4d1f-9a20-1c5e8b7d3e41', registrationTime: ago(48), attended: false, registrationStatus: 'REGISTERED', eventStatus: 'ACTIVE' },
  { eventId: 'e2', eventTitle: 'Kariyer günleri: yazılım', eventDescription: null, eventDate: at(4, 14), eventLocation: 'Kongre Merkezi', qrCode: 'c08d5e17-92ab-4f3e-8d61-5a4b2f9c7e02', registrationTime: ago(72), attended: false, registrationStatus: 'REGISTERED', eventStatus: 'ACTIVE' },
  { eventId: 'e9', eventTitle: 'Git ve GitHub atölyesi', eventDescription: null, eventDate: at(-9, 17), eventLocation: 'B Blok 104', qrCode: '1a2b3c4d-0000-4000-8000-00000000e9e9', registrationTime: ago(24 * 15), attended: true, registrationStatus: 'REGISTERED', eventStatus: 'COMPLETED' },
  { eventId: 'e10', eventTitle: 'Bahar şenliği konseri', eventDescription: null, eventDate: at(-20, 20), eventLocation: 'Açık hava tiyatrosu', qrCode: null, registrationTime: ago(24 * 30), attended: false, registrationStatus: 'NO_SHOW', eventStatus: 'COMPLETED' },
]

export const campusEvents: CampusEvent[] = [
  {
    id: 'e1',
    title: 'Arduino atölyesi',
    description: 'Başlangıç düzeyinde Arduino: devre kurma, sensör okuma ve servo motor sürme. Malzemeler kulüpten; dizüstü bilgisayarınızı getirin.',
    startsAt: at(1, 18, 30),
    endsAt: at(1, 20, 30),
    speakers: 'Can Erdem, Ece Yalçın',
    audience: 'ALL_STUDENTS',
    admission: 'AUTO_CONFIRM',
    location: 'B Blok 204',
    imageUrl: null,
    clubId: 'k1',
    clubName: 'Robotik Kulübü',
    organizerName: null,
    capacity: 40,
    registrationOpensAt: null,
    registrationClosesAt: at(1, 12),
    cancelUntil: at(1, 12),
    status: 'ACTIVE',
  },
  {
    id: 'e3',
    title: 'Yapay zekâ ve etik söyleşisi',
    description: 'Üretken yapay zekânın akademik dürüstlük, telif ve mahremiyet açısından sınırları. Söyleşinin ardından soru-cevap.',
    startsAt: at(2, 15),
    endsAt: at(2, 17),
    speakers: 'Prof. Dr. Nil Ersoy',
    audience: 'CAMPUS',
    admission: 'AUTO_CONFIRM',
    location: 'Amfi 1',
    imageUrl: null,
    clubId: null,
    clubName: null,
    organizerName: 'Mühendislik Fakültesi',
    capacity: 200,
    status: 'ACTIVE',
  },
  { id: 'e2', title: 'Kariyer günleri: yazılım', description: 'Yazılım şirketleriyle tanışma, CV inceleme masaları ve staj görüşmeleri.', startsAt: at(4, 14), endsAt: at(4, 18), audience: 'CAMPUS', admission: 'AUTO_CONFIRM', location: 'Kongre Merkezi', imageUrl: null, clubId: null, clubName: null, organizerName: 'Kariyer Merkezi', capacity: null, status: 'ACTIVE' },
  {
    id: 'e4',
    title: 'Satranç turnuvası',
    description: '5+3 hızlı satranç, İsviçre sistemi 7 tur. İlk üçe kupa.',
    startsAt: at(5, 13),
    endsAt: at(5, 19),
    audience: 'MEMBERS_ONLY',
    admission: 'APPROVAL_REQUIRED',
    location: 'Kütüphane salonu',
    imageUrl: null,
    clubId: 'k2',
    clubName: 'Satranç Kulübü',
    organizerName: null,
    capacity: 64,
    status: 'ACTIVE',
  },
  {
    id: 'e5',
    title: 'Doğa yürüyüşü: Belgrad Ormanı',
    description: 'Yaklaşık 12 km, orta zorlukta parkur. Servis kampüsten 08.00’de kalkar; su ve rahat ayakkabı şart.',
    startsAt: at(6, 8),
    endsAt: at(6, 17),
    audience: 'ALL_STUDENTS',
    admission: 'APPROVAL_REQUIRED',
    location: 'Kampüs ana kapı',
    imageUrl: null,
    clubId: 'k5',
    clubName: 'Dağcılık ve Doğa Sporları Kulübü',
    organizerName: null,
    capacity: 30,
    cancelUntil: at(4, 18),
    status: 'ACTIVE',
  },
  {
    id: 'e6',
    title: 'Fotoğraf yürüyüşü: Karaköy',
    description: 'Sokak fotoğrafçılığı üzerine kısa bir anlatımın ardından birlikte çekim.',
    startsAt: at(3, 10),
    endsAt: at(3, 14),
    audience: 'ALL_STUDENTS',
    admission: 'AUTO_CONFIRM',
    location: 'Karaköy iskelesi',
    imageUrl: null,
    clubId: 'k4',
    clubName: 'Fotoğrafçılık Kulübü',
    organizerName: null,
    capacity: 20,
    status: 'ACTIVE',
  },
]

/** `GET /events/{id}/availability`: kayıtlı ve bekleyen sayıları. */
const TAKEN: Record<string, [number, number]> = { e1: [32, 0], e3: [141, 0], e2: [388, 0], e4: [40, 0], e5: [30, 4], e6: [20, 2] }

export function availability(eventId: string) {
  const e = campusEvents.find((x) => x.id === eventId)!
  const [registered, waitlisted] = TAKEN[eventId] ?? [0, 0]
  return {
    eventId,
    audience: e.audience ?? 'ALL_STUDENTS',
    admission: e.admission ?? 'AUTO_CONFIRM',
    capacity: e.capacity,
    registered,
    waitlisted,
    remaining: e.capacity == null ? null : Math.max(0, e.capacity - registered),
    registrationOpen: true,
    registrationOpensAt: e.registrationOpensAt ?? null,
    registrationClosesAt: e.registrationClosesAt ?? null,
    cancelUntil: e.cancelUntil ?? null,
  }
}

export const participationRequests: ParticipationRequest[] = [
  { id: 'pr1', eventId: 'e5', eventTitle: 'Doğa yürüyüşü: Belgrad Ormanı', status: 'WAITLISTED', requestDate: ago(26), processedDate: null, message: null, rejectionReason: null },
]

export const notifications: AppNotification[] = [
  { id: 'nt1', category: 'COURSE', type: 'ASSIGNMENT_GRADES_PUBLISHED', title: '[BİL 301] Yığın ve kuyruk puanları ilan edildi', body: 'Puanınız ve geri bildiriminiz hazır.', link: '/courses/c1/assignments/a4', read: false, createdAt: ago(2) },
  { id: 'nt2', category: 'COURSE', type: 'ANNOUNCEMENT', title: '[BİL 301] Ara sınav salonları açıklandı', body: 'Salon bilgisi ders duyurularında.', link: '/courses/c1?sekme=duyurular', read: false, createdAt: ago(5) },
  { id: 'nt3', category: 'EVENT', type: 'EVENT_REMINDER', title: 'Arduino atölyesi yarın 18:30', body: 'Biletiniz Biletlerim sayfasında.', link: '/events/e1', read: false, createdAt: ago(9) },
  { id: 'nt4', category: 'ACHIEVEMENT', type: 'BADGE_EARNED', title: 'Yeni rozet: Yardımsever', body: 'Bir cevabınız kabul edildi.', link: '/profile', read: true, createdAt: ago(30) },
  { id: 'nt5', category: 'CLUB_NEWS', type: 'CLUB_ANNOUNCEMENT', title: 'Robotik Kulübü: yeni dönem tanışma', body: 'Perşembe 17:00, kulüp odası.', link: '/clubs/k1', read: true, createdAt: ago(50) },
  { id: 'nt6', category: 'COURSE', type: 'ASSIGNMENT_REMINDER', title: '[BİL 304] Normalizasyon ödevinin teslimine 1 gün kaldı', body: 'Son tarih bu akşam 23:59.', link: '/courses/c2/assignments/a1', read: false, createdAt: ago(1) },
  { id: 'nt7', category: 'CLUB_MANAGEMENT', type: 'CLUB_MEMBERSHIP', title: 'Satranç Kulübü: yeni üyelik başvurusu', body: 'Kaan Yıldız kulübe katılmak istiyor.', link: '/clubs/k2?sekme=yonetim', read: false, createdAt: ago(6) },
  { id: 'nt8', category: 'ACCOUNT', type: 'PASSWORD_CHANGED', title: 'Şifreniz değiştirildi', body: 'Bu işlemi siz yapmadıysanız hemen şifrenizi sıfırlayın.', link: null, read: true, createdAt: ago(24 * 4) },
  { id: 'nt9', category: 'CLUB_NEWS', type: 'CLUB_NOTICE', title: 'Tiyatro Kulübü kapatıldı', body: 'Kulübün etkinlikleri iptal edildi.', link: null, read: false, createdAt: ago(24 * 6) },
]

export const preferences = [
  { category: 'ACCOUNT', label: 'Hesap ve güvenlik', mandatory: true, emailEnabled: true },
  { category: 'COURSE', label: 'Dersler', mandatory: true, emailEnabled: true },
  { category: 'EVENT', label: 'Kayıtlı olduğum etkinlikler', mandatory: true, emailEnabled: true },
  { category: 'MODERATION', label: 'Moderasyon kararları', mandatory: true, emailEnabled: true },
  { category: 'CLUB_MANAGEMENT', label: 'Kulüp yönetimi', mandatory: true, emailEnabled: true },
  { category: 'CLUB_NEWS', label: 'Kulüp duyuruları ve etkinlikleri', mandatory: false, emailEnabled: true },
  { category: 'COMMUNITY', label: 'Topluluk etkileşimleri', mandatory: false, emailEnabled: false },
  { category: 'ACHIEVEMENT', label: 'Puan ve rozetler', mandatory: false, emailEnabled: false },
] as const satisfies readonly { category: AppNotification['category']; label: string; mandatory: boolean; emailEnabled: boolean }[]


export const summary: GamificationSummary = {
  totalPoints: 245,
  currentStreak: 4,
  highestStreak: 6,
  badges: [
    { badgeType: 'HELPER', name: 'Yardımsever', description: 'Kabul edilen ilk cevap', imageUrl: null, earnedAt: ago(30) },
    { badgeType: 'STREAK_3', name: 'Üç hafta', description: 'Üç hafta üst üste katkı', imageUrl: null, earnedAt: ago(24 * 10) },
  ],
}

export const me: Me = {
  id: 'u1',
  firstName: 'Elif',
  lastName: 'Demir',
  email: 'elif.demir@ogr.ornek.edu.tr',
  profileImageUrl: null,
  bio: null,
  role: 'Student',
  studentNumber: '20231045',
  title: null,
  academicTitle: null,
  department: 'Bilgisayar Mühendisliği',
  programId: 'p1',
  programName: 'Bilgisayar Mühendisliği',
  programLevel: 'BACHELOR',
  facultyName: 'Mühendislik Fakültesi',
  facultyId: 'f1',
  departmentId: 'd1',
  entryYear: 2023,
  classYear: 3,
  staffCategory: null,
  affiliations: ['STUDENT'],
  officeNumber: null,
  officeHours: null,
  studentStatus: 'ACTIVE',
  staffStatus: null,
}

// ——— Kulüpler ———

/** Önizleme logoları: gerçekte API herkese açık depo adresi verir; burada aynı yeri satır içi SVG tutar. */
const logo = (bg: string, shapes: string) =>
  `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96"><rect width="96" height="96" fill="${bg}"/>${shapes}</svg>`)}`

const LOGOS = {
  k1: logo('#0f1b24', '<rect x="26" y="30" width="44" height="36" rx="6" fill="#3fbf8c"/><circle cx="38" cy="46" r="5" fill="#0f1b24"/><circle cx="58" cy="46" r="5" fill="#0f1b24"/><rect x="46" y="18" width="4" height="12" fill="#3fbf8c"/><circle cx="48" cy="16" r="4" fill="#3fbf8c"/>'),
  k2: logo('#f4f1ea', '<circle cx="48" cy="28" r="9" fill="#14171a"/><path d="M38 70 L42 40 H54 L58 70 Z" fill="#14171a"/><rect x="30" y="70" width="36" height="8" fill="#14171a"/>'),
  k3: logo('#2b1d5c', '<circle cx="30" cy="34" r="6" fill="#c9b8ff"/><circle cx="66" cy="34" r="6" fill="#c9b8ff"/><circle cx="48" cy="64" r="6" fill="#c9b8ff"/><path d="M30 34 L66 34 L48 64 Z" stroke="#c9b8ff" stroke-width="3" fill="none"/>'),
  k6: logo('#d94f2b', '<path d="M48 72 C20 52 24 30 38 28 C44 27 48 32 48 36 C48 32 52 27 58 28 C72 30 76 52 48 72 Z" fill="#fff"/>'),
  k10: logo('#0057b8', '<text x="48" y="60" font-family="monospace" font-size="34" font-weight="700" fill="#fff" text-anchor="middle">&lt;/&gt;</text>'),
}

export const clubs: ClubSummary[] = [
  { id: 'k1', name: 'Robotik Kulübü', logoUrl: LOGOS.k1, memberCount: 86, advisorName: 'Mehmet Kaya', advisorId: 'u-c1', category: 'SCIENCE_TECHNOLOGY' },
  { id: 'k2', name: 'Satranç Kulübü', logoUrl: LOGOS.k2, memberCount: 41, advisorName: 'Hakan Öztürk', advisorId: 'u-c3', category: 'HOBBY' },
  { id: 'k3', name: 'Yapay Zekâ Topluluğu', logoUrl: LOGOS.k3, memberCount: 132, advisorName: 'Ayşe Arslan', advisorId: 'u-c2', category: 'SCIENCE_TECHNOLOGY' },
  { id: 'k4', name: 'Fotoğrafçılık Kulübü', logoUrl: null, memberCount: 57, advisorName: 'Zeynep Çelik', advisorId: 'u-c9', category: 'ARTS_CULTURE' },
  { id: 'k5', name: 'Dağcılık ve Doğa Sporları Kulübü', logoUrl: null, memberCount: 73, advisorName: 'Elif Aksoy', advisorId: 'u-c14', category: 'SPORTS' },
  { id: 'k6', name: 'Gönüllüler Topluluğu', logoUrl: LOGOS.k6, memberCount: 104, advisorName: 'Selin Koç', advisorId: 'u-c7', category: 'SOCIAL_RESPONSIBILITY' },
  { id: 'k7', name: 'Kariyer ve Girişimcilik Kulübü', logoUrl: null, memberCount: 95, advisorName: 'Kemal Aydın', advisorId: 'u-c10', category: 'CAREER' },
  { id: 'k8', name: 'Tiyatro Kulübü', logoUrl: null, memberCount: 38, advisorName: 'Merve Demirtaş', advisorId: 'u-c15', category: 'ARTS_CULTURE' },
  { id: 'k9', name: 'Matematik Topluluğu', logoUrl: null, memberCount: 29, advisorName: 'Hakan Öztürk', advisorId: 'u-c3', category: 'ACADEMIC' },
  { id: 'k10', name: 'Bilgisayar Bilimleri Kulübü', logoUrl: LOGOS.k10, memberCount: 148, advisorName: 'Burak Şahin', advisorId: 'u-c12', category: 'ACADEMIC' },
]

const ABOUT: Record<string, string> = {
  k1: 'Robot yarışmalarına takım olarak hazırlanıyor, her dönem başında başlangıç düzeyinde Arduino ve gömülü sistem atölyeleri açıyoruz. Laboratuvarımız B Blok 204’te, hafta içi her akşam açık.',
  k2: 'Her çarşamba kütüphane salonunda serbest oyun, ayda bir hızlı satranç turnuvası. Üniversiteler arası ligde iki takımla yer alıyoruz.',
  k3: 'Makine öğrenmesi okuma grubu, Kaggle yarışmaları ve sektörden konuşmacılarla söyleşiler düzenliyoruz.',
}

const PROFILE: Record<string, ClubDetails['profile']> = {
  k1: { category: 'SCIENCE_TECHNOLOGY', contactEmail: 'robotik@ogr.educonnect.local', websiteUrl: 'https://robotik.example.org', instagramUrl: 'https://instagram.com/robotik.example', xUrl: null, linkedinUrl: null },
}

const ADVISOR_TITLE: Record<string, string> = { k1: 'Dr. Öğr. Üyesi', k2: 'Prof. Dr.', k3: 'Doç. Dr.' }

export function clubDetail(id: string): ClubDetails | undefined {
  const c = clubs.find((x) => x.id === id)
  if (!c) return undefined
  return {
    id: c.id,
    name: c.name,
    about: ABOUT[id] ?? null,
    logoUrl: c.logoUrl,
    academicAdvisorId: c.advisorId,
    advisorName: c.advisorName,
    advisorTitle: ADVISOR_TITLE[id] ?? null,
    memberCount: c.memberCount,
    members: [],
    status: id === 'k8' ? 'AWAITING_ADVISOR' : 'ACTIVE',
    closedAt: null,
    closureReason: null,
    profile: PROFILE[id] ?? { category: c.category, contactEmail: null, websiteUrl: null, instagramUrl: null, xUrl: null, linkedinUrl: null },
  }
}

export const clubBoard: Record<string, ClubMember[]> = {
  k1: [
    { studentId: 'st2', firstName: 'Can', lastName: 'Erdem', role: 'PRESIDENT' },
    { studentId: 'u1', firstName: 'Elif', lastName: 'Demir', role: 'EVENT_COORDINATOR' },
    { studentId: 'st3', firstName: 'Ece', lastName: 'Yalçın', role: 'TREASURER' },
    { studentId: 'st4', firstName: 'Mert', lastName: 'Aksu', role: 'VICE_PRESIDENT' },
  ],
  k2: [
    { studentId: 'u1', firstName: 'Elif', lastName: 'Demir', role: 'PRESIDENT' },
    { studentId: 'st5', firstName: 'Deniz', lastName: 'Kara', role: 'VICE_PRESIDENT' },
    { studentId: 'st6', firstName: 'Oğuz', lastName: 'Tan', role: 'MEMBERSHIP_OFFICER' },
  ],
}

export const clubAnnouncements: Record<string, ClubAnnouncement[]> = {
  k1: [
    {
      id: 'ka1',
      clubId: 'k1',
      title: 'Yarışma takımı seçmeleri',
      body: 'Bahar dönemindeki robot yarışması için takım seçmeleri gelecek hafta laboratuvarda. Katılmak isteyenler çarşamba akşamına kadar yönetime yazsın.',
      createdAt: ago(30),
      publishedAt: ago(28),
    },
    { id: 'ka2', clubId: 'k1', title: 'Laboratuvar saatleri değişti', body: 'Laboratuvar bu dönem hafta içi 17.00–21.00 arasında açık.', createdAt: ago(24 * 9), publishedAt: ago(24 * 9) },
  ],
}

export const myMemberships: MyMembership[] = [
  { clubId: 'k1', clubName: 'Robotik Kulübü', logoUrl: LOGOS.k1, clubRole: 'EVENT_COORDINATOR', active: true, termStartDate: ago(24 * 200), clubStatus: 'ACTIVE', validUntil: at(120, 0).slice(0, 10), endedAt: null, endReason: null },
  { clubId: 'k2', clubName: 'Satranç Kulübü', logoUrl: LOGOS.k2, clubRole: 'PRESIDENT', active: true, termStartDate: ago(24 * 330), clubStatus: 'ACTIVE', validUntil: at(12, 0).slice(0, 10), endedAt: null, endReason: null },
]

export const membershipHistory: MyMembership[] = [
  { clubId: 'k4', clubName: 'Fotoğrafçılık Kulübü', logoUrl: null, clubRole: 'MEMBER', active: false, termStartDate: ago(24 * 500), clubStatus: 'ACTIVE', validUntil: null, endedAt: ago(24 * 150), endReason: 'LEFT' },
]

export const membershipRequests: MembershipRequest[] = [
  { id: 'mr1', clubId: 'k3', clubName: 'Yapay Zekâ Topluluğu', clubLogoUrl: null, status: 'PENDING', requestDate: ago(20), processedDate: null, message: null, rejectionReason: null },
  { id: 'mr2', clubId: 'k8', clubName: 'Tiyatro Kulübü', clubLogoUrl: null, status: 'REJECTED', requestDate: ago(24 * 40), processedDate: ago(24 * 37), message: null, rejectionReason: 'Bu dönemin oyuncu kadrosu tamamlandı; bahar döneminde yeniden başvurabilirsiniz.' },
]

export const myPositions: PositionTerm[] = [
  { clubId: 'k1', clubName: 'Robotik Kulübü', position: 'EVENT_COORDINATOR', positionName: 'Etkinlik Koordinatörü', startedAt: ago(24 * 60), endedAt: null, endReason: null },
  { clubId: 'k1', clubName: 'Robotik Kulübü', position: 'BOARD_MEMBER', positionName: 'Yönetim Kurulu Üyesi', startedAt: ago(24 * 400), endedAt: ago(24 * 60), endReason: 'CHANGED' },
]

// ——— Kulüp yönetimi ———

/** `GET /clubs/my-access`: Robotik'te etkinlik koordinatörü, Satranç'ta başkan. */
export const clubAccesses: ClubAccess[] = [
  {
    clubId: 'k1',
    userId: 'u1',
    position: 'EVENT_COORDINATOR',
    member: true,
    actingPresident: false,
    advisor: false,
    permissions: ['VIEW_MEMBERS', 'VIEW_MANAGEMENT_DATA', 'PREPARE_ANNOUNCEMENT', 'PREPARE_EVENT', 'MANAGE_EVENT_OPERATIONS'],
    clubName: 'Robotik Kulübü',
  },
  {
    clubId: 'k2',
    userId: 'u1',
    position: 'PRESIDENT',
    member: true,
    actingPresident: true,
    advisor: false,
    permissions: [
      'VIEW_MEMBERS',
      'VIEW_MANAGEMENT_DATA',
      'VIEW_DECISIONS',
      'MANAGE_MEMBERSHIP_REQUESTS',
      'PREPARE_ANNOUNCEMENT',
      'APPROVE_AS_PRESIDENT',
      'CREATE_EVENT',
      'MANAGE_EVENT_OPERATIONS',
      'PROPOSE_POSITION_CHANGE',
      'REQUEST_CLUB_CLOSURE',
      'PROPOSE_ADVISOR_CHANGE',
      'UPDATE_CLUB_PROFILE',
      'PREPARE_PROFILE_CHANGE',
      'PREPARE_LOGO_CHANGE',
      'PREPARE_ELECTION',
      'VIEW_FINANCE',
      'PREPARE_MINUTES',
      'PREPARE_ACTIVITY_REPORT',
      'PREPARE_FINANCE',
      'PREPARE_SPONSORSHIP',
    ],
    clubName: 'Satranç Kulübü',
  },
]

export const clubAccess = (clubId: string): ClubAccess =>
  clubAccesses.find((a) => a.clubId === clubId) ?? {
    clubId,
    userId: 'u1',
    position: null,
    member: false,
    actingPresident: false,
    advisor: false,
    permissions: [],
    clubName: clubs.find((c) => c.id === clubId)?.name ?? null,
  }

export const pendingMembershipRequests: Record<string, PendingMembershipRequest[]> = {
  k2: [
    {
      id: 'pm1',
      clubId: 'k2',
      clubName: 'Satranç Kulübü',
      clubLogoUrl: null,
      studentId: 'st7',
      studentName: 'Burcu Öz',
      studentEmail: 'burcu.oz@ogr.educonnect.local',
      status: 'PENDING',
      requestDate: ago(50),
      processedDate: null,
      message: 'Lise takımında oynadım, üniversiteler arası lige katılmak istiyorum.',
      rejectionReason: null,
      recommendation: 'APPROVE',
      recommendationNote: 'Turnuva deneyimi var; ligde yedek kadroya alınabilir.',
    },
    {
      id: 'pm2',
      clubId: 'k2',
      clubName: 'Satranç Kulübü',
      clubLogoUrl: null,
      studentId: 'st8',
      studentName: 'Kaan Yıldız',
      studentEmail: 'kaan.yildiz@ogr.educonnect.local',
      status: 'PENDING',
      requestDate: ago(6),
      processedDate: null,
      message: null,
      rejectionReason: null,
      recommendation: null,
      recommendationNote: null,
    },
  ],
}

const approval = (r: Partial<ApprovalRequest> & Pick<ApprovalRequest, 'id' | 'type' | 'status' | 'createdAt'>): ApprovalRequest => ({
  clubId: 'k2',
  clubName: 'Satranç Kulübü',
  preparedBy: 'st5',
  preparedByName: 'Deniz Kara',
  subjectUserId: null,
  subjectUserName: null,
  currentPosition: null,
  requestedPosition: null,
  note: null,
  rejectionReason: null,
  responseNote: null,
  presidentDecidedByName: null,
  presidentDecidedAt: null,
  decidedByName: null,
  decidedAt: null,
  profileChange: null,
  announcement: null,
  ...r,
})

export const clubApprovals: Record<string, ApprovalRequest[]> = {
  k2: [
    approval({
      id: 'ap-k2-1',
      type: 'CLUB_ANNOUNCEMENT',
      status: 'PENDING_PRESIDENT',
      createdAt: ago(20),
      announcement: {
        id: 'an-k2-1',
        clubId: 'k2',
        title: 'Hızlı satranç turnuvası kayıtları açıldı',
        body: 'Cumartesi kütüphane salonunda 5+3 hızlı satranç turnuvası var. Kayıtlar perşembe akşamına kadar; tahtalar kulüpten.',
        createdAt: ago(20),
        publishedAt: null,
      },
    }),
    approval({
      id: 'ap-k2-2',
      type: 'ROLE_CHANGE',
      status: 'PENDING_ADVISOR',
      createdAt: ago(30),
      preparedBy: 'u1',
      preparedByName: 'Elif Demir',
      subjectUserId: 'st9',
      subjectUserName: 'Selin Ak',
      currentPosition: 'MEMBER',
      requestedPosition: 'EVENT_COORDINATOR',
      note: 'Lig maçlarının organizasyonunu üstlendi; görevin resmîleşmesini öneriyorum.',
    }),
    approval({
      id: 'ap-k2-3',
      type: 'CLUB_PROFILE_UPDATE',
      status: 'APPROVED',
      createdAt: ago(24 * 3),
      decidedByName: 'Hakan Öztürk',
      decidedAt: ago(24 * 2),
      preparedBy: 'u1',
      preparedByName: 'Elif Demir',
      presidentDecidedByName: 'Elif Demir',
      presidentDecidedAt: ago(24 * 3),
      profileChange: {
        about: 'Her çarşamba serbest oyun, ayda bir hızlı satranç turnuvası.',
        category: 'HOBBY',
        contactEmail: 'satranc@ogr.educonnect.local',
        websiteUrl: null,
        instagramUrl: 'https://instagram.com/satranc.example',
        xUrl: null,
        linkedinUrl: null,
        logoUrl: null,
      },
    }),
    approval({
      id: 'ap-k2-4',
      type: 'CLUB_ANNOUNCEMENT',
      status: 'APPROVED',
      createdAt: ago(24 * 12),
      presidentDecidedByName: 'Elif Demir',
      presidentDecidedAt: ago(24 * 11),
      announcement: { id: 'an-k2-0', clubId: 'k2', title: 'Dönem açılış buluşması', body: 'İlk buluşma çarşamba 18.00’de.', createdAt: ago(24 * 12), publishedAt: ago(24 * 11) },
    }),
  ],
}

// ——— Etkinlik yönetimi ———

/** Yalnız yönetimin gördüğü etkinlikler (onay bekleyen, reddedilen). */
export const managedEvents: CampusEvent[] = [
  {
    id: 'e7',
    title: 'Simültane gösterisi',
    description: 'Kulübün en güçlü oyuncusu aynı anda yirmi kişiyle oynuyor. İzleyiciler de gelebilir.',
    startsAt: at(12, 17),
    endsAt: at(12, 20),
    audience: 'ALL_STUDENTS',
    admission: 'AUTO_CONFIRM',
    location: 'Kütüphane salonu',
    imageUrl: null,
    clubId: 'k2',
    clubName: 'Satranç Kulübü',
    organizerName: null,
    capacity: 20,
    status: 'PENDING_PRESIDENT',
  },
  {
    id: 'e8',
    title: 'Satranç dersi: açılışlar',
    description: 'Başlangıç düzeyi, iki saatlik ders.',
    startsAt: at(15, 18),
    endsAt: at(15, 20),
    audience: 'MEMBERS_ONLY',
    admission: 'AUTO_CONFIRM',
    location: null,
    imageUrl: null,
    clubId: 'k2',
    clubName: 'Satranç Kulübü',
    organizerName: null,
    capacity: null,
    status: 'REJECTED',
    rejectionReason: 'Yer belirtilmemiş; salon rezervasyonunu ekleyin',
  },
]

export const findEvent = (id: string) => campusEvents.find((e) => e.id === id) ?? managedEvents.find((e) => e.id === id)

export const eventRequests: Record<string, { id: string; eventId: string; eventTitle: string; studentId: string; studentName: string; studentEmail: string; status: 'PENDING' | 'WAITLISTED' | 'APPROVED' | 'REJECTED'; requestDate: string; processedDate: string | null; message: string | null; rejectionReason: string | null }[]> = {
  e4: [
    { id: 'er1', eventId: 'e4', eventTitle: 'Satranç turnuvası', studentId: 'st7', studentName: 'Burcu Öz', studentEmail: 'burcu.oz@ogr.educonnect.local', status: 'PENDING', requestDate: ago(30), processedDate: null, message: 'Lisanslı oyuncuyum, ELO 1650.', rejectionReason: null },
    { id: 'er2', eventId: 'e4', eventTitle: 'Satranç turnuvası', studentId: 'st8', studentName: 'Kaan Yıldız', studentEmail: 'kaan.yildiz@ogr.educonnect.local', status: 'PENDING', requestDate: ago(10), processedDate: null, message: null, rejectionReason: null },
    { id: 'er3', eventId: 'e4', eventTitle: 'Satranç turnuvası', studentId: 'st9', studentName: 'Selin Ak', studentEmail: 'selin.ak@ogr.educonnect.local', status: 'WAITLISTED', requestDate: ago(5), processedDate: null, message: null, rejectionReason: null },
  ],
}

export const attendance: Record<string, { studentId: string; firstName: string; lastName: string; studentNumber: string; status: 'REGISTERED' | 'CANCELLED' | 'NO_SHOW'; attended: boolean; checkedInAt: string | null; method: string | null }[]> = {
  e1: [
    { studentId: 'u1', firstName: 'Elif', lastName: 'Demir', studentNumber: '2023104012', status: 'REGISTERED', attended: false, checkedInAt: null, method: null },
    { studentId: 'st2', firstName: 'Can', lastName: 'Erdem', studentNumber: '2022104077', status: 'REGISTERED', attended: true, checkedInAt: ago(1), method: 'MANUAL' },
    { studentId: 'st7', firstName: 'Burcu', lastName: 'Öz', studentNumber: '2024104031', status: 'REGISTERED', attended: false, checkedInAt: null, method: null },
    { studentId: 'st8', firstName: 'Kaan', lastName: 'Yıldız', studentNumber: '2024104090', status: 'CANCELLED', attended: false, checkedInAt: null, method: null },
  ],
}

export function attendanceReport(eventId: string) {
  const e = findEvent(eventId)!
  const rows = attendance[eventId] ?? []
  return {
    eventId,
    title: e.title,
    status: e.status,
    startsAt: e.startsAt,
    registered: rows.filter((r) => r.status !== 'CANCELLED').length,
    attended: rows.filter((r) => r.attended).length,
    noShow: rows.filter((r) => r.status === 'NO_SHOW').length,
    cancelled: rows.filter((r) => r.status === 'CANCELLED').length,
    rows: rows.map((r) => ({ ...r })),
  }
}

export const eventChanges: Record<string, { id: string; kind: 'EDITED' | 'RESUBMITTED' | 'POSTPONED' | 'RELOCATED' | 'CANCELLED'; details: string | null; reason: string | null; createdAt: string }[]> = {
  e1: [{ id: 'ch1', kind: 'RELOCATED', details: 'B Blok 104 → B Blok 204', reason: 'Laboratuvar daha geniş.', createdAt: ago(20) }],
}

/** Önizlemede değiştirilebilen tercih kopyası. */
export const preferenceState = preferences.map((p) => ({ ...p }))

// ——— Profil ve hesap ———

export const changeRequests = [
  {
    id: 'cr1',
    currentName: 'Elif Demir',
    firstName: null,
    lastName: 'Demir Kaya',
    academicTitle: null,
    titleLabel: null,
    programId: null,
    departmentId: null,
    reason: 'Evlilik sonrası soyadım değişti.',
    status: 'REJECTED' as 'PENDING' | 'APPROVED' | 'REJECTED',
    reviewNote: 'Nüfus kaydınız henüz güncellenmemiş görünüyor; güncellendikten sonra yeniden başvurun.' as string | null,
    reviewedAt: ago(24 * 20) as string | null,
    createdAt: ago(24 * 25),
  },
]

export const leaderboardPreference = { visible: true, displayMode: 'FULL_NAME' as 'FULL_NAME' | 'INITIALS' }

export const academicCatalog = [
  {
    id: 'f1',
    code: 'MUH',
    name: 'Mühendislik Fakültesi',
    active: true,
    departments: [
      {
        id: 'd1',
        code: 'BIL',
        name: 'Bilgisayar Mühendisliği',
        active: true,
        programs: [
          { id: 'p1', code: 'BIL-L', name: 'Bilgisayar Mühendisliği', level: 'BACHELOR', durationYears: 4, active: true },
          { id: 'p2', code: 'BIL-YL', name: 'Bilgisayar Mühendisliği', level: 'MASTER', durationYears: 2, active: true },
        ],
      },
      {
        id: 'd2',
        code: 'EEM',
        name: 'Elektrik-Elektronik Mühendisliği',
        active: true,
        programs: [{ id: 'p3', code: 'EEM-L', name: 'Elektrik-Elektronik Mühendisliği', level: 'BACHELOR', durationYears: 4, active: true }],
      },
    ],
  },
]

// ——— Liderlik tablosu ———

const LEADERS: [string, number, string[]][] = [
  ['Mert Aksu', 1240, ['POINTS_MASTER', 'STREAK_LEGEND', 'FORTNIGHT_WARRIOR', 'WEEK_WARRIOR', 'POINTS_EXPLORER', 'PROFILE_COMPLETE', 'FIRST_STEP']],
  ['B. Ö.', 980, ['POINTS_EXPLORER', 'FORTNIGHT_WARRIOR', 'WEEK_WARRIOR', 'FIRST_STEP']],
  ['Can Erdem', 760, ['POINTS_EXPLORER', 'WEEK_WARRIOR', 'FIRST_STEP']],
  ['Ece Yalçın', 610, ['POINTS_EXPLORER', 'PROFILE_COMPLETE', 'FIRST_STEP']],
  ['Kaan Yıldız', 455, ['POINTS_EXPLORER', 'FIRST_STEP']],
  ['S. A.', 390, ['WEEK_WARRIOR', 'FIRST_STEP']],
  ['Elif Demir', 245, ['WEEK_WARRIOR', 'FIRST_STEP']],
  ['Deniz Kara', 230, ['FIRST_STEP']],
  ['Oğuz Tan', 180, ['FIRST_STEP']],
  ['Zehra Uçar', 120, ['FIRST_STEP']],
]

export function leaderboard(period: string, limit: number) {
  const scale = period === 'ALL_TIME' ? 3 : 1
  const entries = LEADERS.slice(0, limit).map(([displayName, points, badges], i) => ({
    rank: i + 1,
    displayName,
    points: points * scale,
    badges,
    me: displayName === 'Elif Demir',
  }))
  return {
    period,
    termLabel: period === 'TERM' ? '2026-2027 Güz' : null,
    from: period === 'TERM' ? '2026-09-14' : null,
    to: period === 'TERM' ? '2027-01-31' : null,
    facultyId: null,
    entries,
    me: { rank: 7, points: 245 * scale, visible: leaderboardPreference.visible },
  }
}
