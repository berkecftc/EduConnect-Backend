import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { api } from '@/lib/api/client'
import { setSession } from '@/lib/auth/session'
import { localStamp } from '@/lib/time'
import * as fx from './fixtures'
import * as px from './postFixtures'
import * as ax from './assessPreview'
import * as cx from './clubPreview'
import * as dx from './advisePreview'
import * as mx from './managePreview'
import * as tx from './teachPreview'

type Handler = (m: RegExpMatchArray, config: InternalAxiosRequestConfig) => unknown

/** Sunucunun varsayılanı gibi: giriş etkinlikten bir saat önce açılır. */
const gateOpens = (startsAt: string) => new Date(localStamp(startsAt) - 3_600_000).toISOString().slice(0, 19)

/** Öğrenci yanıtı: başka grupların üye adları gizli. */
const studentGroupView = (courseId: string) =>
  fx.studentGroupSets
    .filter((s) => s.courseId === courseId)
    .map((s) => ({
      ...s,
      groups: s.groups.map((g) => ({ ...g, memberCount: g.members.length, members: g.id === s.myGroupId ? [...g.members] : [] })),
    }))

/** Yol → örnek yanıt. Sıra önemli: daha özel yollar önce. */
const GET: [RegExp, Handler][] = [
  [/^\/users\/me$/, () => ({ ...fx.me })],
  [/^\/posts$/, (_m, config) => px.feed('all', (config.params ?? {}) as { category?: string; official?: boolean; page?: number; size?: number })],
  [/^\/posts\/saved$/, (_m, config) => px.feed('saved', (config.params ?? {}) as { page?: number })],
  [/^\/posts\/me$/, (_m, config) => px.feed('mine', (config.params ?? {}) as { page?: number })],
  [/^\/posts\/appeals\/me$/, () => px.appeals.map((a) => ({ ...a }))],
  [/^\/posts\/club\/([^/]+)\/awaiting-approval$/, (m) => px.awaitingOf(m[1]!)],
  [/^\/posts\/([^/]+)\/comments$/, (m) => px.commentsOf(m[1]!)],
  [/^\/posts\/([^/]+)$/, (m) => ({ ...(px.posts.find((p) => p.id === m[1]) ?? notFound()) })],
  [/^\/users\/profile\/me\/change-requests$/, () => fx.changeRequests.map((r) => ({ ...r }))],
  [/^\/users\/academic\/catalog$/, () => fx.academicCatalog],
  [/^\/gamification\/users\/me\/leaderboard-preference$/, () => ({ ...fx.leaderboardPreference })],
  [
    /^\/gamification\/leaderboard$/,
    (_m, config) => {
      const { period = 'TERM', limit = 20 } = (config.params ?? {}) as { period?: string; limit?: number }
      return fx.leaderboard(period, limit)
    },
  ],
  [/^\/courses\/instructor\/([^/]+)$/, (m) => fx.catalog(fx.term.id).filter((c) => c.instructorId === m[1])],
  [/^\/courses\/my-courses$/, () => fx.courses],
  // Kopya: başvuru ve geri çekme diziyi yerinde değiştirir; aynı nesne dönerse React Query değişikliği görmez.
  [/^\/courses\/my-applications$/, () => fx.applications.map((a) => ({ ...a }))],
  [/^\/courses\/terms\/current$/, () => fx.term],
  [/^\/courses\/terms$/, () => fx.terms],
  [/^\/courses$/, (_m, config) => fx.catalog((config.params as { termId?: string } | undefined)?.termId)],
  [/^\/courses\/([^/]+)\/staff$/, (m) => fx.staff[m[1]!] ?? []],
  [/^\/courses\/([^/]+)\/materials$/, (m) => fx.materials[m[1]!] ?? []],
  [/^\/courses\/([^/]+)\/announcements$/, (m) => fx.announcements[m[1]!] ?? []],
  [/^\/courses\/([^/]+)$/, (m) => fx.courseDetail(m[1]!) ?? notFound()],
  [/^\/assignments\/my-assignments$/, () => fx.assignments],
  [/^\/assignments\/course\/([^/]+)\/group-sets$/, (m) => studentGroupView(m[1]!)],
  [/^\/assignments\/course\/([^/]+)\/my-grades$/, (m) => fx.gradesFor(m[1]!)],
  [/^\/assignments\/submissions\/([^/]+)\/versions$/, () => []],
  [
    /^\/events\/my-registrations$/,
    () =>
      fx.registrations.map((r) => {
        const e = fx.findEvent(r.eventId)
        return { ...r, endsAt: e?.endsAt ?? null, cancellationReason: e?.cancellationReason ?? null, checkInOpensAt: gateOpens(r.eventDate) }
      }),
  ],
  [/^\/events\/my-participation-requests$/, () => fx.participationRequests.map((r) => ({ ...r }))],
  [
    /^\/events$/,
    (_m, config) =>
      (config.params as { page?: number } | undefined)?.page != null
        ? { content: fx.campusEvents, number: 0, size: 6, totalElements: fx.campusEvents.length, totalPages: 1, first: true, last: true }
        : fx.campusEvents,
  ],
  [/^\/events\/([^/]+)\/availability$/, (m) => (fx.campusEvents.some((e) => e.id === m[1]) ? fx.availability(m[1]!) : notFound())],
  [/^\/events\/manage\/club\/([^/]+)\/events$/, (m) => [...fx.campusEvents, ...fx.managedEvents].filter((e) => e.clubId === m[1]).map((e) => ({ ...e }))],
  [
    /^\/events\/president\/pending$/,
    () => fx.managedEvents.filter((e) => e.status === 'PENDING_PRESIDENT' && fx.clubAccess(e.clubId ?? '').actingPresident).map((e) => ({ ...e })),
  ],
  [/^\/events\/advisor\/pending$/, () => []],
  [/^\/events\/([^/]+)\/participation-requests$/, (m) => (fx.eventRequests[m[1]!] ?? []).map((r) => ({ ...r }))],
  [/^\/events\/manage\/([^/]+)\/attendance$/, (m) => fx.attendanceReport(m[1]!)],
  [/^\/events\/manage\/([^/]+)\/changes$/, (m) => [...(fx.eventChanges[m[1]!] ?? [])]],
  [/^\/notifications\/unread-count$/, () => ({ unread: fx.notifications.filter((n) => !n.read).length })],
  [
    /^\/notifications$/,
    (_m, config) => {
      const { unreadOnly, page = 0, size = 20 } = (config.params ?? {}) as { unreadOnly?: boolean; page?: number; size?: number }
      const all = [...fx.notifications].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).filter((n) => !unreadOnly || !n.read)
      const content = all.slice(page * size, page * size + size).map((n) => ({ ...n }))
      return { content, totalElements: all.length, totalPages: Math.ceil(all.length / size), number: page, last: (page + 1) * size >= all.length }
    },
  ],
  [/^\/notifications\/preferences$/, () => fx.preferenceState.map((p) => ({ ...p }))],
  [/^\/gamification\/users\/me\/summary$/, () => fx.summary],
  [/^\/clubs$/, () => fx.clubs],
  [/^\/clubs\/my-access$/, () => fx.clubAccesses],
  [
    /^\/clubs\/approvals\/inbox$/,
    () =>
      Object.values(fx.clubApprovals)
        .flat()
        .filter((r) => r.status === 'PENDING_PRESIDENT' && fx.clubAccess(r.clubId).actingPresident)
        .map((r) => ({ ...r })),
  ],
  [
    /^\/clubs\/approvals\/mine$/,
    () =>
      [...cx.requestsAboutMe, ...Object.values(fx.clubApprovals).flat().filter((r) => r.preparedBy === 'u1' || r.subjectUserId === 'u1')]
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map((r) => ({ ...r })),
  ],
  [/^\/clubs\/([^/]+)\/my-access$/, (m) => fx.clubAccess(m[1]!)],
  [/^\/clubs\/([^/]+)\/membership-requests\/pending$/, (m) => (fx.pendingMembershipRequests[m[1]!] ?? []).map((r) => ({ ...r }))],
  [/^\/clubs\/([^/]+)\/approvals$/, (m) => (fx.clubApprovals[m[1]!] ?? []).map((r) => ({ ...r }))],
  [/^\/clubs\/my-memberships$/, () => fx.myMemberships.map((m) => ({ ...m }))],
  [/^\/clubs\/my-memberships\/history$/, () => [...fx.myMemberships, ...fx.membershipHistory].map((m) => ({ ...m }))],
  [/^\/clubs\/my-membership-requests$/, () => fx.membershipRequests.map((r) => ({ ...r }))],
  [/^\/clubs\/my-positions$/, () => fx.myPositions],
  [/^\/clubs\/([^/]+)\/board-members$/, (m) => fx.clubBoard[m[1]!] ?? []],
  [
    /^\/clubs\/([^/]+)\/announcements$/,
    (m) =>
      fx.myMemberships.some((x) => x.clubId === m[1])
        ? { content: [...(fx.clubAnnouncements[m[1]!] ?? [])], number: 0, size: 20, totalElements: 0, totalPages: 1, first: true, last: true }
        : notFound(),
  ],
  [/^\/clubs\/([^/]+)$/, (m) => fx.clubDetail(m[1]!) ?? notFound()],
  [/^\/events\/club\/([^/]+)$/, (m) => fx.campusEvents.filter((e) => e.clubId === m[1])],
  [
    /^\/events\/([^/]+)$/,
    (m) => {
      const e = fx.findEvent(m[1]!) ?? notFound()
      return { ...e, checkInOpensAt: gateOpens(e.startsAt) }
    },
  ],
]

/** Yazma istekleri (yöntem, yol): başvuru, geri çekme, ayrılma ve yenileme önizleme verisini değiştirir; diğerleri boş yanıt döner. */
const WRITE: [string, RegExp, Handler][] = [
  [
    'post',
    /^\/assignments\/groups\/([^/]+)\/(join|leave)$/,
    (m, config) => {
      const set = fx.studentGroupSets.find((s) => s.groups.some((g) => g.id === m[1]))!
      const group = set.groups.find((g) => g.id === m[1])!
      const related = fx.assignments.filter((a) => a.groupSetId === set.id)
      if (!set.signupOpen) cx.fail(config, 409, 'SIGNUP_CLOSED', 'Bu grup setinde öğrenci seçimi kapalı.')
      if (m[2] === 'join') {
        if (set.myGroupId) cx.fail(config, 409, 'ALREADY_IN_GROUP', 'Bu sette zaten bir gruptasınız; önce ayrılın.')
        if (set.maxMembers != null && group.members.length >= set.maxMembers) cx.fail(config, 409, 'GROUP_FULL', 'Grup kontenjanı dolu.')
        group.members.push({ studentId: 'u1', name: 'Elif Demir', studentNumber: null, joinedAt: new Date().toISOString() })
        set.myGroupId = group.id
      } else {
        if (related.some((a) => a.submission))
          cx.fail(config, 409, 'GROUP_HAS_SUBMISSIONS', 'Grubunuz teslim yaptığı için gruptan ayrılamazsınız; hocanıza başvurun.')
        group.members = group.members.filter((x) => x.studentId !== 'u1')
        set.myGroupId = null
      }
      for (const a of related) Object.assign(a, { groupId: set.myGroupId, groupName: set.myGroupId ? group.name : null })
      return studentGroupView(set.courseId)
    },
  ],
  ['post', /^\/posts$/, (_m, config) => px.newPost(JSON.parse(String(config.data)))],
  [
    'post',
    /^\/posts\/([^/]+)\/(approve|reject)$/,
    (m, config) => {
      const p = px.posts.find((x) => x.id === m[1])!
      if (m[2] === 'approve') Object.assign(p, { status: 'PUBLISHED', reviewNote: null })
      else Object.assign(p, { status: 'REJECTED', reviewNote: (JSON.parse(String(config.data)) as { note: string }).note })
      return { ...p }
    },
  ],
  [
    'put',
    /^\/posts\/([^/]+)$/,
    (m, config) => {
      const p = px.posts.find((x) => x.id === m[1])!
      const body = JSON.parse(String(config.data)) as { title: string; content: string }
      Object.assign(p, { title: body.title, content: body.content, updatedAt: new Date().toISOString() })
      return { ...p }
    },
  ],
  [
    'delete',
    /^\/posts\/([^/]+)$/,
    (m) => {
      const i = px.posts.findIndex((x) => x.id === m[1])
      if (i >= 0) px.posts.splice(i, 1)
      return null
    },
  ],
  [
    'put',
    /^\/posts\/([^/]+)\/like$/,
    (m) => {
      const p = px.posts.find((x) => x.id === m[1])!
      if (!p.liked) Object.assign(p, { liked: true, likeCount: p.likeCount + 1 })
      return { liked: p.liked, likeCount: p.likeCount }
    },
  ],
  [
    'delete',
    /^\/posts\/([^/]+)\/like$/,
    (m) => {
      const p = px.posts.find((x) => x.id === m[1])!
      if (p.liked) Object.assign(p, { liked: false, likeCount: p.likeCount - 1 })
      return { liked: p.liked, likeCount: p.likeCount }
    },
  ],
  [
    'post',
    /^\/posts\/([^/]+)\/bookmark$/,
    (m) => {
      const p = px.posts.find((x) => x.id === m[1])!
      p.bookmarked = !p.bookmarked
      return { bookmarked: p.bookmarked }
    },
  ],
  ['post', /^\/posts\/([^/]+)\/comments$/, (m, config) => px.newComment(m[1]!, (JSON.parse(String(config.data)) as { content: string }).content)],
  [
    'post',
    /^\/posts\/comments\/([^/]+)\/replies$/,
    (m, config) => px.newComment(px.findComment(m[1]!)!.postId, (JSON.parse(String(config.data)) as { content: string }).content, m[1]!),
  ],
  [
    'delete',
    /^\/posts\/([^/]+)\/comments\/([^/]+)$/,
    (m) => {
      const i = px.comments.findIndex((c) => c.id === m[2])
      if (i >= 0) px.comments.splice(i, 1)
      else for (const c of px.comments) c.replies = c.replies.filter((r) => r.id !== m[2])
      px.recount(m[1]!)
      return null
    },
  ],
  [
    'put',
    /^\/posts\/([^/]+)\/accepted-answer$/,
    (m, config) => {
      px.posts.find((x) => x.id === m[1])!.acceptedCommentId = (JSON.parse(String(config.data)) as { commentId: string }).commentId
      return null
    },
  ],
  [
    'delete',
    /^\/posts\/([^/]+)\/accepted-answer$/,
    (m) => {
      px.posts.find((x) => x.id === m[1])!.acceptedCommentId = null
      return null
    },
  ],
  [
    'post',
    /^\/posts\/(?:comments\/)?([^/]+)\/report$/,
    (_m, config) => {
      const { reason } = JSON.parse(String(config.data)) as { reason: string }
      const sensitive = reason === 'HARASSMENT' || reason === 'THREAT'
      return {
        sensitive,
        reasonLabel: reason,
        supportMessage: sensitive
          ? "Bildirimin öncelikli olarak bir moderatöre iletildi. Acil bir tehlike varsa kampüs güvenliğine veya 112'ye başvur; konuşmak istersen üniversitenin psikolojik danışmanlık birimi sana destek olabilir."
          : null,
      }
    },
  ],
  [
    'post',
    /^\/posts\/comments\/([^/]+)\/hide$/,
    (m, config) => {
      const c = px.findComment(m[1]!)!
      Object.assign(c, { status: 'HIDDEN', moderationNote: (JSON.parse(String(config.data)) as { reason: string }).reason })
      px.recount(c.postId)
      return null
    },
  ],
  [
    'post',
    /^\/posts\/(comments\/)?([^/]+)\/appeal$/,
    (m, config) => {
      const comment = !!m[1]
      const a = {
        id: 'ap-' + Date.now(),
        targetType: comment ? ('COMMENT' as const) : ('POST' as const),
        targetId: m[2]!,
        postId: comment ? (px.findComment(m[2]!)?.postId ?? null) : m[2]!,
        statement: (JSON.parse(String(config.data)) as { statement: string }).statement,
        status: 'OPEN' as const,
        decisionNote: null,
        createdAt: new Date().toISOString(),
      }
      px.appeals.unshift(a)
      return a
    },
  ],
  [
    'put',
    /^\/posts\/([^/]+)\/attachment$/,
    (m, config) => {
      const file = (config.data as FormData).get('file') as File
      px.posts.find((x) => x.id === m[1])!.attachmentName = file.name
      return null
    },
  ],
  [
    'delete',
    /^\/posts\/([^/]+)\/attachment$/,
    (m) => {
      px.posts.find((x) => x.id === m[1])!.attachmentName = null
      return null
    },
  ],
  [
    'post',
    /^\/users\/profile\/me\/change-requests$/,
    (_m, config) => {
      if (fx.changeRequests.some((r) => r.status === 'PENDING')) previewError(config, 409, 'CHANGE_REQUEST_PENDING', 'Bekleyen bir değişiklik talebiniz var.')
      const body = JSON.parse(String(config.data)) as Record<string, string>
      const r = { id: 'cr-' + Date.now(), currentName: 'Elif Demir', firstName: body.firstName ?? null, lastName: body.lastName ?? null, academicTitle: null, titleLabel: null, programId: body.programId ?? null, departmentId: body.departmentId ?? null, reason: body.reason!, status: 'PENDING', reviewNote: null, reviewedAt: null, createdAt: new Date().toISOString() }
      ;(fx.changeRequests as unknown as (typeof r)[]).unshift(r)
      return r
    },
  ],
  [
    'put',
    /^\/users\/profile\/([^/]+)$/,
    (_m, config) => {
      Object.assign(fx.me, JSON.parse(String(config.data)))
      return { ...fx.me }
    },
  ],
  [
    'post',
    /^\/users\/me\/profile-picture$/,
    (_m, config) => {
      fx.me.profileImageUrl = URL.createObjectURL((config.data as FormData).get('file') as File)
      return fx.me.profileImageUrl
    },
  ],
  [
    'put',
    /^\/gamification\/users\/me\/leaderboard-preference$/,
    (_m, config) => Object.assign(fx.leaderboardPreference, JSON.parse(String(config.data))),
  ],
  [
    'post',
    /^\/auth\/change-password$/,
    (_m, config) => {
      // Önizleme kuralı: mevcut şifre alanına "yanlis" yazılırsa backend'in yanlış şifre hatası taklit edilir.
      const body = JSON.parse(String(config.data)) as { currentPassword: string }
      if (body.currentPassword === 'yanlis') previewError(config, 400, 'WRONG_CURRENT_PASSWORD', 'Wrong current password')
      return 'Password changed successfully.'
    },
  ],
  [
    'post',
    /^\/notifications\/([^/]+)\/read$/,
    (m) => {
      const n = fx.notifications.find((x) => x.id === m[1])
      if (n) n.read = true
      return n ?? null
    },
  ],
  [
    'post',
    /^\/notifications\/read-all$/,
    () => {
      const unread = fx.notifications.filter((n) => !n.read)
      unread.forEach((n) => (n.read = true))
      return { updated: unread.length }
    },
  ],
  [
    'put',
    /^\/notifications\/preferences$/,
    (_m, config) => {
      const body = JSON.parse(String(config.data)) as { category: string; emailEnabled: boolean }
      const p = fx.preferenceState.find((x) => x.category === body.category)!
      p.emailEnabled = body.emailEnabled
      return { ...p }
    },
  ],
  [
    'post',
    /^\/events\/manage$/,
    async (_m, config) => {
      const part = (config.data as FormData).get('data') as Blob
      const d = JSON.parse(await part.text()) as Record<string, unknown> & { clubName: string }
      const club = fx.clubs.find((c) => c.name === d.clubName)!
      const e = {
        ...(d as object),
        id: 'e-' + Date.now(),
        imageUrl: null,
        clubId: club.id,
        clubName: club.name,
        organizerName: null,
        status: fx.clubAccess(club.id).actingPresident ? ('PENDING' as const) : ('PENDING_PRESIDENT' as const),
      } as unknown as (typeof fx.managedEvents)[number]
      fx.managedEvents.unshift(e)
      return e
    },
  ],
  [
    'put',
    /^\/events\/manage\/([^/]+)$/,
    (m, config) => {
      const e = fx.findEvent(m[1]!)!
      const body = JSON.parse(String(config.data)) as Record<string, unknown> & { note?: string }
      const resubmit = e.status === 'REJECTED'
      Object.assign(e, body, resubmit ? { status: 'PENDING_PRESIDENT', rejectionReason: null } : {})
      ;(fx.eventChanges[e.id] ??= []).unshift({ id: 'ch-' + Date.now(), kind: resubmit ? 'RESUBMITTED' : 'EDITED', details: null, reason: body.note ?? null, createdAt: new Date().toISOString() })
      return e
    },
  ],
  [
    'post',
    /^\/events\/manage\/([^/]+)\/(postpone|relocate|cancel)$/,
    (m, config) => {
      const e = fx.findEvent(m[1]!)!
      const body = JSON.parse(String(config.data)) as { startsAt: string | null; endsAt: string | null; location: string | null; reason: string }
      const kind = m[2] === 'postpone' ? 'POSTPONED' : m[2] === 'relocate' ? 'RELOCATED' : 'CANCELLED'
      const details = m[2] === 'postpone' ? `${e.startsAt.slice(0, 16)} → ${body.startsAt?.slice(0, 16)}` : m[2] === 'relocate' ? `${e.location ?? '–'} → ${body.location}` : null
      if (m[2] === 'postpone') Object.assign(e, { startsAt: body.startsAt, endsAt: body.endsAt ?? mx.keepDuration(e.startsAt, e.endsAt, body.startsAt!), status: 'PENDING' })
      if (m[2] === 'relocate') e.location = body.location
      if (m[2] === 'cancel') Object.assign(e, { status: 'CANCELLED', cancellationReason: body.reason })
      ;(fx.eventChanges[e.id] ??= []).unshift({ id: 'ch-' + Date.now(), kind, details, reason: body.reason, createdAt: new Date().toISOString() })
      return e
    },
  ],
  [
    'post',
    /^\/events\/(president|advisor)\/([^/]+)\/(approve|reject)$/,
    (m, config) => {
      const e = fx.findEvent(m[2]!)!
      if (m[3] === 'approve') e.status = m[1] === 'president' ? 'PENDING' : 'ACTIVE'
      else Object.assign(e, { status: 'REJECTED', rejectionReason: (JSON.parse(String(config.data ?? '{}')) as { reason?: string }).reason ?? null })
      return e
    },
  ],
  [
    'post',
    /^\/events\/participation-requests\/([^/]+)\/(approve|reject)$/,
    (m) => {
      const r = Object.values(fx.eventRequests).flat().find((x) => x.id === m[1])!
      Object.assign(r, { status: m[2] === 'approve' ? 'APPROVED' : 'REJECTED', processedDate: new Date().toISOString() })
      return r
    },
  ],
  [
    'post',
    /^\/events\/manage\/verify-qr$/,
    (_m, config) => {
      const { qrCode: code, eventId } = JSON.parse(String(config.data ?? '{}')) as { qrCode?: string; eventId?: string }
      const ticket = fx.registrations.find((r) => r.qrCode === code)
      const row = ticket && (fx.attendance[ticket.eventId] ?? []).find((r) => r.studentId === 'u1')
      if (row && eventId && ticket.eventId !== eventId)
        throw Object.assign(new Error('Başka etkinlik'), {
          isAxiosError: true,
          config,
          response: { status: 400, data: { status: 400, errorCode: 'TICKET_WRONG_EVENT', message: 'Bu bilet başka bir etkinliğe ait.' } },
        })
      if (!row) throw Object.assign(new Error('Bilet bulunamadı'), { isAxiosError: true, config, response: { status: 400, data: { status: 400, errorCode: 'VERIFICATION_FAILED', message: 'Bilet doğrulanamadı.' }, headers: {}, config } })
      Object.assign(row, { attended: true, checkedInAt: new Date().toISOString(), method: 'QR' })
      return 'ACCESS GRANTED'
    },
  ],
  [
    'post',
    /^\/events\/manage\/([^/]+)\/registrations\/([^/]+)\/check-in$/,
    (m) => {
      const row = (fx.attendance[m[1]!] ?? []).find((r) => r.studentId === m[2])
      if (row) Object.assign(row, { attended: true, checkedInAt: new Date().toISOString(), method: 'MANUAL' })
      return null
    },
  ],
  [
    'delete',
    /^\/events\/manage\/([^/]+)\/registrations\/([^/]+)\/check-in$/,
    (m) => {
      const row = (fx.attendance[m[1]!] ?? []).find((r) => r.studentId === m[2])
      if (row) Object.assign(row, { attended: false, checkedInAt: null, method: null })
      return null
    },
  ],
  [
    'post',
    /^\/events\/([^/]+)\/participation-request$/,
    (m) => {
      const e = fx.campusEvents.find((x) => x.id === m[1])!
      const a = fx.availability(e.id)
      const status = e.admission === 'APPROVAL_REQUIRED' ? 'PENDING' : a.remaining === 0 ? 'WAITLISTED' : 'APPROVED'
      if (status === 'APPROVED') {
        fx.registrations.unshift({
          eventId: e.id,
          eventTitle: e.title,
          eventDescription: e.description,
          eventDate: e.startsAt,
          eventLocation: e.location,
          qrCode: crypto.randomUUID(),
          registrationTime: new Date().toISOString(),
          attended: false,
          registrationStatus: 'REGISTERED',
          eventStatus: 'ACTIVE',
        })
      } else {
        fx.participationRequests.unshift({ id: 'pr-' + Date.now(), eventId: e.id, eventTitle: e.title, status, requestDate: new Date().toISOString(), processedDate: null, message: null, rejectionReason: null })
      }
      const message =
        status === 'APPROVED'
          ? 'Kaydınız tamamlandı. Biletiniz e-posta adresinize gönderilecektir.'
          : status === 'WAITLISTED'
            ? 'Kontenjan dolu; bekleme listesine alındınız. Yer açılınca kaydınız otomatik yapılır.'
            : 'Katılım isteğiniz alındı. Kulüp yetkilisi onayladıktan sonra biletiniz e-posta adresinize gönderilecektir.'
      return { message, requestId: 'pr', status }
    },
  ],
  [
    'delete',
    /^\/events\/([^/]+)\/participation-request$/,
    (m) => {
      for (const r of fx.participationRequests) if (r.eventId === m[1] && (r.status === 'PENDING' || r.status === 'WAITLISTED')) r.status = 'WITHDRAWN'
      return null
    },
  ],
  [
    'delete',
    /^\/events\/([^/]+)\/registration$/,
    (m) => {
      const r = fx.registrations.find((x) => x.eventId === m[1] && x.registrationStatus === 'REGISTERED')
      if (r) r.registrationStatus = 'CANCELLED'
      return null
    },
  ],
  [
    'put',
    /^\/clubs\/([^/]+)\/membership-requests\/([^/]+)\/(approve|reject|recommendation)$/,
    (m, config) => {
      const list = fx.pendingMembershipRequests[m[1]!] ?? []
      const i = list.findIndex((r) => r.id === m[2])
      if (i < 0) return null
      if (m[3] === 'recommendation') {
        const body = JSON.parse(String(config.data ?? '{}')) as { recommendation: 'APPROVE' | 'REJECT'; note: string | null }
        Object.assign(list[i]!, { recommendation: body.recommendation, recommendationNote: body.note })
        return list[i]
      }
      const [r] = list.splice(i, 1)
      const club = fx.clubs.find((c) => c.id === m[1])
      if (m[3] === 'approve' && club?.memberCount != null) club.memberCount += 1
      return r
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/approvals\/([^/]+)\/(approve|reject|withdraw)$/,
    (m, config) => {
      const r = (fx.clubApprovals[m[1]!] ?? []).find((x) => x.id === m[2])
      if (!r) return null
      const now = new Date().toISOString()
      if (m[3] === 'approve') {
        Object.assign(r, { status: 'APPROVED', presidentDecidedByName: 'Elif Demir', presidentDecidedAt: now })
        if (r.announcement) (fx.clubAnnouncements[r.clubId] ??= []).unshift({ ...r.announcement, publishedAt: now })
      } else if (m[3] === 'reject') {
        Object.assign(r, { status: 'REJECTED', rejectionReason: (JSON.parse(String(config.data ?? '{}')) as { reason?: string }).reason ?? null, decidedByName: 'Elif Demir', decidedAt: now })
      } else Object.assign(r, { status: 'WITHDRAWN' })
      return r
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/announcements$/,
    (m, config) => {
      const body = JSON.parse(String(config.data ?? '{}')) as { title: string; body: string }
      const now = new Date().toISOString()
      const direct = fx.clubAccess(m[1]!).actingPresident
      const announcement = { id: 'an-' + Date.now(), clubId: m[1]!, title: body.title, body: body.body, createdAt: now, publishedAt: direct ? now : null }
      if (direct) (fx.clubAnnouncements[m[1]!] ??= []).unshift(announcement)
      const r = {
        ...fx.clubApprovals.k2![0]!,
        id: 'ap-' + Date.now(),
        clubId: m[1]!,
        type: 'CLUB_ANNOUNCEMENT',
        status: direct ? ('APPROVED' as const) : ('PENDING_PRESIDENT' as const),
        createdAt: now,
        preparedBy: 'u1',
        preparedByName: 'Elif Demir',
        announcement,
      }
      ;(fx.clubApprovals[m[1]!] ??= []).unshift(r)
      return r
    },
  ],
  [
    'delete',
    /^\/clubs\/([^/]+)\/announcements\/([^/]+)$/,
    (m) => {
      const list = fx.clubAnnouncements[m[1]!] ?? []
      const i = list.findIndex((a) => a.id === m[2])
      if (i >= 0) list.splice(i, 1)
      return null
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/membership-requests$/,
    (m) => {
      const c = fx.clubs.find((x) => x.id === m[1])!
      const r = { id: 'mr-' + c.id, clubId: c.id, clubName: c.name, clubLogoUrl: null, status: 'PENDING' as const, requestDate: new Date().toISOString(), processedDate: null, message: null, rejectionReason: null }
      fx.membershipRequests.unshift(r)
      return r
    },
  ],
  [
    'delete',
    /^\/clubs\/([^/]+)\/membership-requests$/,
    (m) => {
      const i = fx.membershipRequests.findIndex((r) => r.clubId === m[1] && r.status === 'PENDING')
      if (i >= 0) fx.membershipRequests.splice(i, 1)
      return null
    },
  ],
  [
    'delete',
    /^\/clubs\/([^/]+)\/leave$/,
    (m) => {
      const i = fx.myMemberships.findIndex((x) => x.clubId === m[1])
      if (i >= 0) fx.membershipHistory.unshift({ ...fx.myMemberships.splice(i, 1)[0]!, active: false, endedAt: new Date().toISOString(), endReason: 'LEFT' })
      return null
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/membership\/renew$/,
    (m) => {
      const x = fx.myMemberships.find((y) => y.clubId === m[1])!
      x.validUntil = `${Number(x.validUntil!.slice(0, 4)) + 1}${x.validUntil!.slice(4)}`
      return x
    },
  ],
  [
    'post',
    /^\/courses\/([^/]+)\/apply$/,
    (m) => {
      const c = fx.catalog('t1').find((x) => x.id === m[1])!
      const app = { id: 'ap-' + c.id, courseId: c.id, courseTitle: c.title, courseCode: c.code, status: 'PENDING' as const, applicationDate: new Date().toISOString(), processedDate: null, rejectionReason: null }
      fx.applications.unshift(app)
      return app
    },
  ],
  [
    'post',
    /^\/courses\/applications\/([^/]+)\/withdraw$/,
    (m) => {
      const app = fx.applications.find((a) => a.id === m[1])!
      Object.assign(app, { status: 'WITHDRAWN', processedDate: new Date().toISOString() })
      return app
    },
  ],
]

class PreviewNotFound extends Error {}
function notFound(): never {
  throw new PreviewNotFound()
}

/** Önizlemede backend hatası taklidi (ProblemDetail biçimi). */
function previewError(config: InternalAxiosRequestConfig, status: number, errorCode: string, message: string): never {
  throw Object.assign(new Error(message), { isAxiosError: true, config, response: { status, statusText: '', headers: {}, config, data: { status, errorCode, message } } })
}

/** Ağa gitmeden örnek veriyle yanıt veren axios adaptörü; gerçekçi olsun diye kısa gecikme. */
const fixtureAdapter: AxiosAdapter = async (config: InternalAxiosRequestConfig) => {
  await new Promise((r) => setTimeout(r, 250))
  const url = (config.url ?? '').split('?')[0]!
  const reply = (status: number, data: unknown): AxiosResponse => ({ status, statusText: '', data, headers: {}, config })
  const method = config.method ?? 'get'
  if (method !== 'get') {
    const hit = (tx.state.academician ? [...dx.WRITE, ...ax.WRITE, ...tx.WRITE, ...WRITE] : mx.state.role ? [...mx.WRITE, ...WRITE] : [...cx.WRITE, ...WRITE]).filter(([verb]) => verb === method).map(([, re, handle]) => [url.match(re), handle] as const).find(([m]) => m)
    return reply(200, hit ? await hit[1](hit[0]!, config) : {})
  }
  for (const [re, handle] of tx.state.academician ? [...dx.GET, ...ax.GET, ...tx.GET, ...GET] : mx.state.role ? [...mx.GET, ...GET] : [...cx.GET, ...GET]) {
    const m = url.match(re)
    if (!m) continue
    try {
      return reply(200, handle(m, config))
    } catch (e) {
      if (e instanceof PreviewNotFound) break
      throw e
    }
  }
  const error = Object.assign(new Error('Önizleme verisi yok: ' + url), {
    isAxiosError: true,
    config,
    response: reply(404, { status: 404, errorCode: 'NOT_FOUND', message: `Önizlemede bu veri yok (${url}).` }),
  })
  throw error
}

/**
 * Geliştirme önizlemesi: oturumu yalnız bellekte açar ve istekleri örnek verilerle yanıtlar.
 * Gerçek hesaba ve backend'e dokunmaz; sayfa yenilenince biter.
 */
export function PreviewPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  useEffect(() => {
    // ?rol=akademisyen: Dr. Öğr. Üyesi Mehmet Kaya; ?rol=gorevli: öğrenci ve personel doğrulayıcısı;
    // ?rol=yonetici: sistem yöneticisi; yoksa öğrenci Elif Demir.
    const rol = new URLSearchParams(window.location.search).get('rol')
    const academician = rol === 'akademisyen'
    const manager = rol === 'gorevli' ? 'staff' : rol === 'yonetici' ? 'admin' : null
    tx.state.academician = academician
    mx.state.role = manager
    api.defaults.adapter = fixtureAdapter
    qc.clear()
    const role = academician ? 'ROLE_ACADEMICIAN' : manager === 'staff' ? 'ROLE_STAFF' : manager === 'admin' ? 'ROLE_ADMIN' : 'ROLE_STUDENT'
    setSession(
      {
        token: 'onizleme',
        refreshToken: 'onizleme',
        userId: academician ? tx.me.id : manager ? mx.STAFF.id : 'u1',
        email: academician ? tx.me.email! : manager ? mx.STAFF.email : fx.me.email!,
        roles: [role],
        primaryRole: role,
        pendingRequests: [],
        permissions: manager === 'staff' ? ['PERM_STUDENT_VERIFIER', 'PERM_STAFF_VERIFIER', 'PERM_ACCOUNT_MANAGER', 'PERM_MODERATOR', 'PERM_CAMPUS_PUBLISHER'] : [],
        expiresAt: Date.now() + 24 * 3_600_000,
      },
      { persist: false },
    )
    // ?git=/yol: önizleme o sayfayla açılır (yalnız uygulama içi yollar).
    const go = new URLSearchParams(window.location.search).get('git')
    navigate(go?.startsWith('/') && !go.startsWith('//') ? go : academician ? '/courses' : '/', { replace: true })
  }, [navigate, qc])
  return null
}
