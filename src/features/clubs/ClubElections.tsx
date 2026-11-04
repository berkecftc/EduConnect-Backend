import { useState } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { useSession } from '@/lib/auth/session'
import { formatInstant } from '@/lib/time'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Meter } from '@/components/ui/Meter'
import { Panel } from '@/components/ui/Panel'
import { Select } from '@/components/ui/Select'
import { EmptyState, QueryBoundary } from '@/components/ui/States'
import { StationLine, type Station, type StationState } from '@/components/ui/StationLine'
import { Textarea } from '@/components/ui/Textarea'
import type { ClubDetails } from './api'
import {
  AUDIT_SEATS_MAX,
  BALLOTS,
  BOARD_SEATS_MAX,
  ELECTION_NOTE_MAX,
  ELECTION_STATUS,
  ballotsOf,
  candidateName,
  candidatesOf,
  isRunning,
  seatsOf,
  useCandidacy,
  useClubElections,
  useElectionStep,
  useOpenElection,
  useVote,
  type Election,
  type ElectionBallot,
} from './elections'
import { can, type ClubAccess } from './manage'
import { PersonLink } from '@/features/people/PersonLink'

type BallotMeta = (typeof BALLOTS)[number]

const fail = (e: unknown) => toast.error(toApiError(e).message)

/**
 * Seçim sekmesi: sürmekte olan seçim (süreç çizgisi, kurul kurul adaylar ve oy, sayımdan sonra sonuç),
 * genel sekreter ya da başkan için seçim açma ve adımlar; altta geçmiş seçimler.
 */
export function ClubElections({ club, access }: { club: ClubDetails; access: ClubAccess }) {
  const elections = useClubElections(club.id)
  const prepares = can(access, 'PREPARE_ELECTION') && club.status !== 'CLOSED'
  const route = access.actingPresident ? 'danışman onayına' : 'başkan ve danışman onayına'

  return (
    <QueryBoundary query={elections} what="Seçimler" skeletonRows={4}>
      {(list) => {
        const current = list.find((e) => isRunning(e.status))
        const past = list.filter((e) => !isRunning(e.status))
        return (
          <div className="flex flex-col gap-20">
            {current ? (
              <Current club={club} election={current} access={access} prepares={prepares} route={route} />
            ) : prepares ? (
              <OpenElection clubId={club.id} route={route} />
            ) : (
              <EmptyState title="Şu anda seçim yok">
                Genel kurul seçimi açıldığında burada aday olabilir ve oy kullanabilirsiniz.
              </EmptyState>
            )}
            {past.length > 0 && <Past list={past} />}
          </div>
        )
      }}
    </QueryBoundary>
  )
}

// ——— Seçim açma ———

/** Seçim aç: koltuk sayıları ve üyelere not. Açılınca bütün üyelere bildirim gider, aday başvuruları başlar. */
function OpenElection({ clubId, route }: { clubId: string; route: string }) {
  const open = useOpenElection(clubId)
  const [board, setBoard] = useState('4')
  const [audit, setAudit] = useState('3')
  const [note, setNote] = useState('')
  const [confirming, setConfirming] = useState(false)
  const range = (max: number) => Array.from({ length: max + 1 }, (_, i) => String(i))

  return (
    <Panel title="Genel kurul seçimi">
      <p className="max-w-[64ch] text-md text-ink-2">
        Seçim kulübün yönetimini yeniler: başkan, yönetim kurulu ve denetim kurulu seçilir. Sonuç {route} gider; onaylanınca mevcut bütün
        görevler sona erer ve seçilenler göreve başlar. Diğer görevleri yeni başkan dağıtır.
      </p>
      <div className="mt-6 flex max-w-[40rem] flex-col gap-5">
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Yönetim kurulu koltuğu" hint={`0 ile ${BOARD_SEATS_MAX} arası.`}>
            <Select value={board} onChange={(e) => setBoard(e.target.value)}>
              {range(BOARD_SEATS_MAX).map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Denetim kurulu koltuğu" hint={`0 ile ${AUDIT_SEATS_MAX} arası.`}>
            <Select value={audit} onChange={(e) => setAudit(e.target.value)}>
              {range(AUDIT_SEATS_MAX).map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </Select>
          </Field>
        </div>
        <p className="text-sm text-ink-3">Başkanlık için her zaman bir koltuk vardır.</p>
        <Field label="Üyelere not" hint="İsteğe bağlı. Ör. genel kurulun tarihi ve yeri.">
          <Textarea
            rows={3}
            maxLength={ELECTION_NOTE_MAX}
            valueLength={note.length}
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        </Field>
        <div>
          <Button variant="primary" onClick={() => setConfirming(true)}>
            Seçimi aç
          </Button>
        </div>
      </div>
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title="Seçim açılsın mı?"
        description={`Bütün üyelere bildirim gider ve aday başvuruları başlar. Koltuklar: 1 başkan, ${board} yönetim kurulu, ${audit} denetim kurulu.`}
        confirmLabel="Seçimi aç"
        loading={open.isPending}
        onConfirm={() =>
          open.mutate(
            { boardSeats: Number(board), auditSeats: Number(audit), note },
            {
              onSuccess: () => {
                toast.success('Seçim açıldı; aday başvuruları başladı')
                setConfirming(false)
              },
              onError: fail,
            },
          )
        }
      />
    </Panel>
  )
}

// ——— Süren seçim ———

const PHASES: { label: string; at: (e: Election) => string | null }[] = [
  { label: 'Aday başvurusu', at: (e) => e.openedAt },
  { label: 'Oylama', at: (e) => e.votingStartedAt },
  { label: 'Sonuç onayı', at: (e) => e.closedAt },
  { label: 'Görev devri', at: (e) => e.completedAt },
]
const PHASE_INDEX: Record<Election['status'], number> = { CANDIDACY: 0, VOTING: 1, AWAITING_APPROVAL: 2, COMPLETED: 3, CANCELLED: 0 }

function stationsOf(e: Election): Station[] {
  const at = PHASE_INDEX[e.status]
  return PHASES.map((p, i) => {
    const state: StationState = e.status === 'COMPLETED' || i < at ? 'done' : i === at ? 'current' : 'pending'
    const when = p.at(e)
    return { label: p.label, state, detail: when ? formatInstant(when, 'date') : undefined }
  })
}

function Current({
  club,
  election: e,
  access,
  prepares,
  route,
}: {
  club: ClubDetails
  election: Election
  access: ClubAccess
  prepares: boolean
  route: string
}) {
  const session = useSession()
  const mine = e.candidates.find((c) => c.studentId === session?.userId)
  const counted = e.status === 'AWAITING_APPROVAL' || e.status === 'COMPLETED'
  const voter = access.member && club.status !== 'CLOSED'

  return (
    <div className="grid gap-14 lg:grid-cols-12 lg:gap-10">
      <div className="min-w-0 lg:col-span-8">
        <Panel title="Genel kurul seçimi" action={<Badge tone={ELECTION_STATUS[e.status].tone}>{ELECTION_STATUS[e.status].label}</Badge>}>
          <StationLine line="kulup" stations={stationsOf(e)} label="Seçimin aşamaları" className="mt-2" />
          {e.openedByName && <p className="mt-4 text-sm text-ink-3">Seçimi {e.openedByName} açtı.</p>}
          {e.note && <p className="mt-8 max-w-[64ch] text-md whitespace-pre-line text-ink-2">{e.note}</p>}
          <p className="mt-6 max-w-[64ch] text-md text-ink-2">
            {e.status === 'CANDIDACY'
              ? 'Her üye bir kurula aday olabilir. Oylama başlayana kadar adaylıktan çekilebilirsiniz.'
              : e.status === 'VOTING'
                ? 'Her kurul için koltuk sayısı kadar adaya oy verebilirsiniz. Oy gizlidir ve gönderildikten sonra değiştirilemez.'
                : 'Oylar sayıldı. Sonuç onaylanınca mevcut görevler sona erer ve seçilenler göreve başlar; reddedilirse seçim geçersiz sayılır.'}
          </p>
          <div className="mt-10 flex flex-col gap-12">
            {ballotsOf(e).map((b) => (
              <Ballot
                key={b.code}
                clubId={club.id}
                election={e}
                meta={b}
                mine={mine}
                voter={voter}
                counted={counted}
                me={session?.userId}
              />
            ))}
          </div>
        </Panel>
      </div>
      <aside className="flex flex-col gap-14 lg:col-span-4">
        <Summary election={e} />
        {prepares && <Steps clubId={club.id} election={e} route={route} />}
      </aside>
    </div>
  )
}

/** Koltuklar ve (sayımdan sonra) katılım: büyük rakamlar, ince ölçek çizgisi. */
function Summary({ election: e }: { election: Election }) {
  return (
    <section aria-label="Seçim özeti" className="border-t-2 border-ink pt-5">
      <p className="text-sm text-ink-3">Koltuk</p>
      <dl className="mt-2 grid grid-cols-3 gap-4">
        {BALLOTS.map((b) => (
          <div key={b.code}>
            <dt className="text-sm text-ink-2">{b.label}</dt>
            <dd className="tabular mt-1 text-4xl leading-none font-heavy">{seatsOf(e, b.code)}</dd>
          </div>
        ))}
      </dl>
      {e.voters != null && e.eligibleVoters != null && (
        <div className="mt-8 border-t border-rule pt-5">
          <p className="text-sm text-ink-3">Katılım</p>
          <p className="mt-1">
            <span className="tabular text-4xl leading-none font-heavy">{e.voters}</span>
            <span className="tabular ml-1 text-lg text-ink-3">/ {e.eligibleVoters} üye</span>
          </p>
          <div className="mt-3">
            <Meter
              value={e.voters}
              max={Math.max(1, e.eligibleVoters)}
              color="var(--ec-kulup)"
              label={`${e.eligibleVoters} üyeden ${e.voters} oy kullandı`}
            />
          </div>
        </div>
      )}
    </section>
  )
}

/**
 * Bir kurulun bölümü. Aday başvurusunda adaylar ve "Aday ol"; oylamada oy pusulası (koltuk 1 ise tek seçim,
 * fazlaysa koltuk sayısı kadar); sayımdan sonra oylar ve seçilenler.
 */
function Ballot({
  clubId,
  election: e,
  meta,
  mine,
  voter,
  counted,
  me,
}: {
  clubId: string
  election: Election
  meta: BallotMeta
  mine: Election['candidates'][number] | undefined
  voter: boolean
  counted: boolean
  me: string | undefined
}) {
  const candidacy = useCandidacy(clubId)
  const [dialog, setDialog] = useState<'nominate' | 'withdraw' | null>(null)
  const seats = seatsOf(e, meta.code)
  const list = candidatesOf(e, meta.code)
  const voted = (e.myBallots ?? []).includes(meta.code)
  const mineHere = mine?.ballot === meta.code
  const top = Math.max(1, ...list.map((c) => c.votes ?? 0))

  return (
    <section aria-labelledby={`kurul-${meta.code}`} className="border-t border-rule pt-5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1">
        <h3 id={`kurul-${meta.code}`} className="text-xl font-heavy">
          {meta.label}
        </h3>
        <span className="tabular text-sm text-ink-3">
          {seats} koltuk, {list.length} aday
        </span>
      </div>

      {e.status === 'VOTING' && voter && !voted && list.length > 0 ? (
        <VoteForm clubId={clubId} election={e} meta={meta} />
      ) : list.length === 0 ? (
        <p className="mt-3 text-md text-ink-3">{e.status === 'CANDIDACY' ? 'Henüz aday yok.' : 'Bu kurulda aday yok.'}</p>
      ) : (
        <ul className="mt-3">
          {list.map((c) => (
            <li key={c.id} className="border-b border-rule py-3">
              <div className="flex items-baseline justify-between gap-6">
                <span className="min-w-0">
                  <PersonLink id={c.studentId} className="font-semibold">
                    {candidateName(c)}
                  </PersonLink>
                  {c.studentId === me && <span className="ml-2 text-sm text-ink-3">siz</span>}
                  {counted && c.elected && <span className="ml-3 text-sm font-semibold text-kulup">Seçildi</span>}
                </span>
                {counted && <span className="tabular text-2xl leading-none font-heavy">{c.votes ?? 0}</span>}
              </div>
              {counted && (
                <div className="mt-2">
                  <Meter
                    value={c.votes ?? 0}
                    max={top}
                    color={c.elected ? 'var(--ec-kulup)' : 'var(--ec-ink-3)'}
                    label={`${candidateName(c)}: ${c.votes ?? 0} oy`}
                  />
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {e.status === 'VOTING' && voted && <p className="mt-3 text-sm font-semibold text-success">Bu kurulda oyunuzu kullandınız.</p>}

      {e.status === 'CANDIDACY' && voter && (
        <div className="mt-4">
          {mineHere ? (
            <button
              type="button"
              onClick={() => setDialog('withdraw')}
              className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
            >
              Adaylıktan çekil
            </button>
          ) : !mine ? (
            <Button size="sm" onClick={() => setDialog('nominate')}>
              {meta.code === 'PRESIDENT' ? 'Başkanlığa aday ol' : `${meta.label} için aday ol`}
            </Button>
          ) : null}
        </div>
      )}

      <ConfirmDialog
        open={dialog === 'nominate'}
        onOpenChange={(o) => !o && setDialog(null)}
        title={meta.code === 'PRESIDENT' ? 'Başkanlığa aday olun' : `${meta.label} için aday olun`}
        description="Bir seçimde yalnız bir kurula aday olabilirsiniz; oylama başlayana kadar çekilebilirsiniz. Başka bir kulüpte yönetim görevi olan öğrenci aday olamaz."
        confirmLabel="Aday ol"
        loading={candidacy.isPending}
        onConfirm={() =>
          candidacy.mutate(
            { electionId: e.id, ballot: meta.code },
            {
              onSuccess: () => {
                toast.success(`${meta.label} için adaysınız`)
                setDialog(null)
              },
              onError: fail,
            },
          )
        }
      />
      <ConfirmDialog
        open={dialog === 'withdraw'}
        onOpenChange={(o) => !o && setDialog(null)}
        title="Adaylıktan çekil"
        description={`${meta.label} adaylığınız geri çekilecek. Aday başvuruları sürdükçe yeniden aday olabilirsiniz.`}
        confirmLabel="Adaylıktan çekil"
        loading={candidacy.isPending}
        onConfirm={() =>
          candidacy.mutate(
            { electionId: e.id, ballot: null },
            {
              onSuccess: () => {
                toast.success('Adaylığınız geri çekildi')
                setDialog(null)
              },
              onError: fail,
            },
          )
        }
      />
    </section>
  )
}

/** Oy pusulası: koltuk 1 ise tek seçim, fazlaysa en çok koltuk sayısı kadar. Gönderilmeden önce seçilenler bir kez daha gösterilir. */
function VoteForm({ clubId, election: e, meta }: { clubId: string; election: Election; meta: BallotMeta }) {
  const vote = useVote(clubId)
  const seats = seatsOf(e, meta.code)
  const list = candidatesOf(e, meta.code)
  const single = seats === 1
  const [picked, setPicked] = useState<string[]>([])
  const [confirming, setConfirming] = useState(false)
  const toggle = (id: string, on: boolean) => setPicked((p) => (single ? [id] : on ? [...p, id] : p.filter((x) => x !== id)))
  const names = list.filter((c) => picked.includes(c.id)).map(candidateName)

  return (
    <fieldset className="mt-3">
      <legend className="sr-only">{meta.label} oy pusulası</legend>
      <ul className="border-t border-rule">
        {list.map((c) => {
          const on = picked.includes(c.id)
          const full = !single && !on && picked.length >= seats
          return (
            <li key={c.id} className="border-b border-rule">
              <label className={`row-fill flex items-center gap-4 px-1 py-3 ${full ? 'cursor-not-allowed text-ink-3' : 'cursor-pointer'}`}>
                <input
                  type={single ? 'radio' : 'checkbox'}
                  name={`oy-${meta.code}`}
                  checked={on}
                  disabled={full}
                  onChange={(ev) => toggle(c.id, ev.target.checked)}
                  className="size-4 accent-[var(--ec-kulup)]"
                />
                <span className="font-semibold">{candidateName(c)}</span>
              </label>
            </li>
          )
        })}
      </ul>
      <div className="mt-4 flex flex-wrap items-center gap-x-6 gap-y-2">
        <Button variant="primary" size="sm" disabled={picked.length === 0} onClick={() => setConfirming(true)}>
          Oyumu gönder
        </Button>
        <span className="tabular text-sm text-ink-3">
          {single ? 'Bir aday seçin.' : `En çok ${seats} aday seçebilirsiniz; ${picked.length} seçili.`}
        </span>
      </div>
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={`${meta.label} oyunuz`}
        description="Oy gizlidir; gönderdikten sonra değiştiremezsiniz."
        confirmLabel="Oyu gönder"
        loading={vote.isPending}
        onConfirm={() =>
          vote.mutate(
            { electionId: e.id, ballot: meta.code, candidateIds: picked },
            {
              onSuccess: () => {
                toast.success(`${meta.label} oyunuz kaydedildi`)
                setConfirming(false)
              },
              onError: fail,
            },
          )
        }
      >
        <ul className="border-y border-rule">
          {names.map((n) => (
            <li key={n} className="border-b border-rule py-2 font-semibold last:border-b-0">
              {n}
            </li>
          ))}
        </ul>
      </ConfirmDialog>
    </fieldset>
  )
}

/** Seçimi yöneten (genel sekreter ya da başkan) için sıradaki adım ve iptal. */
function Steps({ clubId, election: e, route }: { clubId: string; election: Election; route: string }) {
  const step = useElectionStep(clubId)
  const [dialog, setDialog] = useState<'start' | 'close' | 'cancel' | null>(null)
  const hasPresident = e.candidates.some((c) => c.ballot === 'PRESIDENT')
  const run = (s: 'start-voting' | 'close' | 'cancel', done: string) =>
    step.mutate(
      { electionId: e.id, step: s },
      {
        onSuccess: () => {
          toast.success(done)
          setDialog(null)
        },
        onError: fail,
      },
    )

  return (
    <section aria-labelledby="secim-yonetimi" className="border-t-2 border-ink pt-5">
      <h2 id="secim-yonetimi" className="text-2xl font-heavy">
        Seçim yönetimi
      </h2>
      {e.status === 'CANDIDACY' && (
        <>
          <p className="mt-2 text-md text-ink-2">
            Oylamayı başlatınca aday başvuruları kapanır ve üyelere bildirim gider. Başkanlık için en az bir aday gerekir.
          </p>
          <Button variant="primary" className="mt-5" disabled={!hasPresident} onClick={() => setDialog('start')}>
            Oylamayı başlat
          </Button>
          {!hasPresident && <p className="mt-2 text-sm text-ink-3">Başkanlığa henüz aday yok.</p>}
        </>
      )}
      {e.status === 'VOTING' && (
        <>
          <p className="mt-2 text-md text-ink-2">Oylamayı bitirince oylar sayılır ve sonuç {route} gider.</p>
          <Button variant="primary" className="mt-5" onClick={() => setDialog('close')}>
            Oylamayı bitir
          </Button>
        </>
      )}
      {e.status === 'AWAITING_APPROVAL' && (
        <p className="mt-2 text-md text-ink-2">
          Sonuç {route} gönderildi. Onaylanınca görevler devredilir; reddedilirse seçim geçersiz sayılır.
        </p>
      )}
      {(e.status === 'CANDIDACY' || e.status === 'VOTING') && (
        <button
          type="button"
          onClick={() => setDialog('cancel')}
          className="mt-6 block text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline"
        >
          Seçimi iptal et
        </button>
      )}

      <ConfirmDialog
        open={dialog === 'start'}
        onOpenChange={(o) => !o && setDialog(null)}
        title="Oylama başlasın mı?"
        description="Aday başvuruları kapanır; üyeler oylarını kullanabilir."
        confirmLabel="Oylamayı başlat"
        loading={step.isPending}
        onConfirm={() => run('start-voting', 'Oylama başladı')}
      />
      <ConfirmDialog
        open={dialog === 'close'}
        onOpenChange={(o) => !o && setDialog(null)}
        title="Oylama bitsin mi?"
        description={`Oy kullanmayan üyeler artık oy veremez. Oylar sayılır ve sonuç ${route} gider; onaylanınca mevcut bütün görevler sona erer, seçilenler göreve başlar.`}
        confirmLabel="Oylamayı bitir"
        loading={step.isPending}
        onConfirm={() => run('close', `Oylar sayıldı; sonuç ${route} gönderildi`)}
      />
      <ConfirmDialog
        open={dialog === 'cancel'}
        onOpenChange={(o) => !o && setDialog(null)}
        title="Seçim iptal edilsin mi?"
        description={
          e.status === 'VOTING'
            ? 'Kullanılan oylar geçersiz sayılır. Görevler olduğu gibi kalır.'
            : 'Adaylıklar geçersiz sayılır. Görevler olduğu gibi kalır.'
        }
        confirmLabel="Seçimi iptal et"
        destructive
        loading={step.isPending}
        onConfirm={() => run('cancel', 'Seçim iptal edildi')}
      />
    </section>
  )
}

// ——— Geçmiş ———

function winners(e: Election, ballot: ElectionBallot) {
  return candidatesOf(e, ballot)
    .filter((c) => c.elected)
    .map(candidateName)
}

/** Geçmiş seçimler: tarih, seçilenler, katılım; iptal edilenler soluk. */
function Past({ list }: { list: Election[] }) {
  return (
    <Panel title="Geçmiş seçimler">
      <ul>
        {list.map((e) => (
          <li
            key={e.id}
            className="grid gap-x-8 gap-y-2 border-b border-rule py-4 first:pt-0 md:grid-cols-[9rem_minmax(0,1fr)_auto] md:items-baseline"
          >
            <span className="tabular text-md text-ink-2">{formatInstant(e.completedAt ?? e.closedAt ?? e.openedAt, 'date')}</span>
            {e.status === 'COMPLETED' ? (
              <dl className="grid gap-x-4 gap-y-1 sm:grid-cols-[9rem_minmax(0,1fr)]">
                {ballotsOf(e).map((b) => (
                  <div key={b.code} className="contents">
                    <dt className="text-sm text-ink-3">{b.label}</dt>
                    <dd className="text-md font-semibold">{winners(e, b.code).join(', ') || 'Seçilen yok'}</dd>
                  </div>
                ))}
              </dl>
            ) : (
              <span className="text-md text-ink-3">Seçim sonuçlanmadan iptal edildi.</span>
            )}
            <span className="flex items-baseline gap-4 md:justify-end">
              {e.voters != null && e.eligibleVoters != null && (
                <span className="tabular text-sm text-ink-3">
                  {e.voters}/{e.eligibleVoters} katılım
                </span>
              )}
              <Badge tone={ELECTION_STATUS[e.status].tone}>{ELECTION_STATUS[e.status].label}</Badge>
            </span>
          </li>
        ))}
      </ul>
    </Panel>
  )
}

/** Onay kaydındaki seçim sonucu (CLUB_ELECTION): seçilenler ve katılım. Seçim bulunamazsa kaydın notu. */
export function ElectionResult({
  clubId,
  requestId,
  note,
  election,
}: {
  clubId: string
  requestId: string
  note: string | null
  /** Onay kaydıyla gelen seçim; yoksa kulübün seçimlerinden bulunur. */
  election?: Election
}) {
  const elections = useClubElections(clubId, !election)
  const e = election ?? elections.data?.find((x) => x.requestId === requestId)
  if (!e) return note ? <p className="text-md text-ink-2">{note}</p> : null
  return (
    <div>
      <dl className="grid max-w-[64ch] gap-x-6 gap-y-1.5 sm:grid-cols-[9rem_minmax(0,1fr)]">
        {ballotsOf(e).map((b) => {
          const elected = candidatesOf(e, b.code).filter((c) => c.elected)
          return (
            <div key={b.code} className="contents">
              <dt className="text-sm text-ink-3">{b.label}</dt>
              <dd className="text-md">
                {elected.length
                  ? elected.map((c, i) => (
                      <span key={c.id}>
                        {i > 0 && ', '}
                        <span className="font-semibold">{candidateName(c)}</span>
                        <span className="tabular text-ink-3"> ({c.votes ?? 0} oy)</span>
                      </span>
                    ))
                  : 'Seçilen yok'}
              </dd>
            </div>
          )
        })}
      </dl>
      {e.voters != null && e.eligibleVoters != null && (
        <p className="tabular mt-2 text-sm text-ink-3">
          Katılım {e.voters}/{e.eligibleVoters}. Onaylanınca mevcut bütün görevler sona erer, seçilenler göreve başlar.
        </p>
      )}
    </div>
  )
}
