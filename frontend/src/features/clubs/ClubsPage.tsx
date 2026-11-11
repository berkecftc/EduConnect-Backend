import { useId, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { hasRole, useSession } from '@/lib/auth/session'
import { cn } from '@/lib/cn'
import { formatNumber } from '@/lib/format'
import { usePageTitle } from '@/lib/usePageTitle'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import {
  CATEGORY_LABEL,
  isActiveMembership,
  useClubs,
  useMyMembershipRequests,
  useMyMemberships,
  type ClubCategory,
  type ClubSummary,
} from './api'
import { ClubLogo } from './ClubMark'

const CATEGORIES = Object.keys(CATEGORY_LABEL) as ClubCategory[]

const ROW = 'grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-6 gap-y-2 sm:grid-cols-[minmax(0,1fr)_7rem_11rem]'

/** Tüm kulüpler: kategori dizini ve arama; kulüpler baş harfe göre gruplu, satırda üye sayısı ve sizin durumunuz (F-33). */
export function ClubsPage() {
  usePageTitle('Tüm kulüpler')
  const session = useSession()
  const student = hasRole(session, 'ROLE_STUDENT')
  const clubs = useClubs()
  const memberships = useMyMemberships(student)
  const requests = useMyMembershipRequests(student)
  const [params, setParams] = useSearchParams()
  const [q, setQ] = useState('')
  const category = (CATEGORIES as string[]).includes(params.get('kategori') ?? '') ? (params.get('kategori') as ClubCategory) : null

  const relation = useMemo(() => {
    const map = new Map<string, 'member' | 'pending'>()
    for (const r of requests.data ?? []) if (r.status === 'PENDING') map.set(r.clubId, 'pending')
    for (const m of memberships.data ?? []) if (isActiveMembership(m)) map.set(m.clubId, 'member')
    return map
  }, [memberships.data, requests.data])

  const setCategory = (c: ClubCategory | null) => {
    const next = new URLSearchParams(params)
    if (c) next.set('kategori', c)
    else next.delete('kategori')
    setParams(next, { replace: true })
  }

  return (
    <div className="mx-auto max-w-[80rem]">
      <PageHeader
        title="Kulüpler"
        meta={clubs.data ? `Kampüste ${formatNumber(clubs.data.length)} kulüp var.` : undefined}
        actions={
          student ? (
            <Button asChild>
              <Link to="/clubs/mine">Kulüplerim</Link>
            </Button>
          ) : undefined
        }
      />

      <QueryBoundary query={clubs} what="Kulüpler" skeletonRows={6}>
        {(list) => {
          const counts = new Map<ClubCategory, number>()
          for (const c of list) if (c.category) counts.set(c.category, (counts.get(c.category) ?? 0) + 1)
          const needle = q.trim().toLocaleLowerCase('tr-TR')
          const shown = list
            .filter((c) => !category || c.category === category)
            .filter((c) => !needle || [c.name, c.advisorName ?? ''].some((s) => s.toLocaleLowerCase('tr-TR').includes(needle)))
            .sort((a, b) => a.name.localeCompare(b.name, 'tr-TR'))
          return (
            <>
              <CategoryIndex total={list.length} counts={counts} value={category} onChange={setCategory} />
              <div className="mt-6 max-w-[32rem]">
                <Field label="Ara">
                  <Input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Kulüp adı ya da danışman" />
                </Field>
              </div>
              {list.length === 0 ? (
                <div className="mt-10">
                  <EmptyState title="Henüz kulüp yok">Kampüste kurulan kulüpler burada listelenir.</EmptyState>
                </div>
              ) : shown.length === 0 ? (
                <div className="mt-10">
                  <EmptyState
                    title="Eşleşen kulüp yok"
                    action={
                      <Button
                        onClick={() => {
                          setQ('')
                          setCategory(null)
                        }}
                      >
                        Süzgeçleri temizle
                      </Button>
                    }
                  >
                    Başka bir kategori seçin ya da aramayı değiştirin.
                  </EmptyState>
                </div>
              ) : (
                <>
                  <p className="tabular mt-8 text-sm text-ink-3" aria-live="polite">
                    {shown.length === list.length ? `${list.length} kulüp` : `${shown.length} kulüp gösteriliyor, toplam ${list.length}`}
                  </p>
                  <div className="mt-3 flex flex-col gap-12">
                    {groupByCategory(shown).map(([title, group]) => (
                      <ClubGroup key={title} title={title} clubs={group} relation={relation} />
                    ))}
                  </div>
                </>
              )}
            </>
          )
        }}
      </QueryBoundary>
    </div>
  )
}

/** Kategori dizini: tarife başlığı gibi yazı dizisi; seçili olanın altında kulüp hattının çizgisi. */
function CategoryIndex({
  total,
  counts,
  value,
  onChange,
}: {
  total: number
  counts: Map<ClubCategory, number>
  value: ClubCategory | null
  onChange: (c: ClubCategory | null) => void
}) {
  const items: [ClubCategory | null, string, number][] = [
    [null, 'Tümü', total],
    ...CATEGORIES.filter((c) => counts.has(c)).map((c): [ClubCategory, string, number] => [c, CATEGORY_LABEL[c], counts.get(c)!]),
  ]
  return (
    <nav aria-label="Kategoriler" className="mt-8 border-y border-rule">
      <ul className="-mx-3 flex flex-wrap">
        {items.map(([c, label, n]) => {
          const active = value === c
          return (
            <li key={c ?? 'all'}>
              <button
                type="button"
                aria-pressed={active}
                onClick={() => onChange(c)}
                className={cn(
                  'relative flex h-12 items-center gap-2 px-3 text-md font-semibold transition-colors',
                  active ? 'text-ink' : 'text-ink-3 hover:text-ink',
                )}
              >
                {label}
                <span className="tabular text-sm font-normal text-ink-3">{n}</span>
                {active && <span aria-hidden className="absolute inset-x-3 -bottom-px h-[3px] bg-kulup" />}
              </button>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}

/** Kategoriye göre gruplar (dizindeki sırayla); kategorisi olmayanlar en sonda. */
function groupByCategory(list: ClubSummary[]): [string, ClubSummary[]][] {
  const groups = new Map<string, ClubSummary[]>()
  for (const cat of [...CATEGORIES, null]) {
    const members = list.filter((c) => (c.category ?? null) === cat)
    if (members.length > 0) groups.set(cat ? CATEGORY_LABEL[cat] : 'Diğer kulüpler', members)
  }
  return [...groups.entries()]
}

function ClubGroup({ title, clubs, relation }: { title: string; clubs: ClubSummary[]; relation: Map<string, 'member' | 'pending'> }) {
  const id = useId()
  return (
    <section aria-labelledby={id}>
      <h2 id={id} className="flex items-baseline gap-4 border-b-2 border-ink pb-2">
        <span className="text-3xl leading-none font-heavy tracking-[-0.02em]">{title}</span>
        <span className="tabular text-md text-ink-3">{clubs.length} kulüp</span>
      </h2>
      <ul>
        {clubs.map((c) => {
          const rel = relation.get(c.id)
          return (
            <li key={c.id} className="border-b border-rule">
              <Link to={`/clubs/${c.id}`} className={`${ROW} row-fill group py-6`}>
                <span className="flex min-w-0 items-center gap-5">
                  <ClubLogo name={c.name} logoUrl={c.logoUrl} />
                  <span className="min-w-0">
                    <span className="block text-2xl leading-tight font-heavy text-balance transition-transform duration-300 ease-rail group-hover:translate-x-1">
                      {c.name}
                    </span>
                    {c.advisorName && <span className="mt-1 block text-md text-ink-3">Danışman {c.advisorName}</span>}
                  </span>
                </span>
                <span className="hidden text-right sm:block">
                  <span className="tabular block text-3xl leading-none font-heavy">
                    {c.memberCount != null ? formatNumber(c.memberCount) : '–'}
                  </span>
                  <span className="mt-1 block text-xs text-ink-3">üye</span>
                </span>
                <span className="flex justify-end">
                  {rel === 'member' && <Badge tone="success">Üyesiniz</Badge>}
                  {rel === 'pending' && <Badge tone="warning">Başvurunuz onayda</Badge>}
                </span>
              </Link>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
