import { Link, useParams, useSearchParams } from 'react-router-dom'
import { nowLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { Field } from '@/components/ui/Field'
import { Select } from '@/components/ui/Select'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import { Tabs, type TabDef } from '@/components/ui/Tabs'
import { useClub, type ClubDetails } from './api'
import { can, useClubAccess, type ClubAccess } from './manage'
import { academicYearOf, recentYears, yearLabel } from './records'
import { RecordsFinance } from './RecordsFinance'
import { DecisionLog, PositionHistory } from './RecordsHistory'
import { RecordsMeetings } from './RecordsMeetings'
import { RecordsReports } from './RecordsReports'

/** Yıla bağlı sekmeler; diğerlerinde yıl seçici görünmez. */
const BY_YEAR = new Set(['toplantilar', 'mali', 'raporlar'])

/**
 * Kulüp kayıtları: toplantılar ve karar defteri, mali işler, dönem raporları, karar günlüğü, görev geçmişi.
 * Her sekme yetkiye bağlı (F-85); yıl seçimi adreste (`?yil=2027`).
 */
export function ClubRecordsPage() {
  const { clubId = '' } = useParams()
  const club = useClub(clubId)
  const access = useClubAccess(clubId)
  usePageTitle(club.data ? `${club.data.name} kayıtları` : 'Kulüp kayıtları')

  return (
    <div className="mx-auto max-w-[80rem]">
      <QueryBoundary query={club} what="Kulüp" skeletonRows={3}>
        {(c) => (
          <QueryBoundary query={access} what="Yetkiler" skeletonRows={3}>
            {(a) => <Records club={c} access={a} />}
          </QueryBoundary>
        )}
      </QueryBoundary>
    </div>
  )
}

function Records({ club: c, access }: { club: ClubDetails; access: ClubAccess }) {
  const [params, setParams] = useSearchParams()
  const current = academicYearOf(nowLocalIso().slice(0, 10), c.academicYearEnd)
  const years = recentYears(current)
  const asked = Number(params.get('yil'))
  const year = years.includes(asked) ? asked : current
  const closed = c.status === 'CLOSED'
  const props = { clubId: c.id, year, access, closed }

  const tabs: TabDef[] = [
    ...(can(access, 'VIEW_DECISIONS') ? [{ value: 'toplantilar', label: 'Toplantılar', content: <RecordsMeetings {...props} /> }] : []),
    ...(can(access, 'VIEW_FINANCE', 'PREPARE_SPONSORSHIP')
      ? [{ value: 'mali', label: 'Mali işler', content: <RecordsFinance {...props} /> }]
      : []),
    ...(can(access, 'VIEW_DECISIONS', 'PREPARE_AUDIT_REPORT')
      ? [{ value: 'raporlar', label: 'Raporlar', content: <RecordsReports {...props} /> }]
      : []),
    ...(can(access, 'VIEW_DECISIONS') ? [{ value: 'gunluk', label: 'Karar günlüğü', content: <DecisionLog clubId={c.id} /> }] : []),
    ...(can(access, 'VIEW_MEMBERS') ? [{ value: 'gorevler', label: 'Görev geçmişi', content: <PositionHistory clubId={c.id} /> }] : []),
  ]
  const active = tabs.find((t) => t.value === params.get('sekme'))?.value ?? tabs[0]?.value

  return (
    <>
      <Link to={`/clubs/${c.id}`} className="text-sm font-semibold text-kulup underline-offset-2 hover:underline">
        {c.name}
      </Link>
      <PageHeader title="Kulüp kayıtları" meta="Toplantı tutanakları, mali işler, dönem raporları ve kararların izi." />
      {tabs.length === 0 ? (
        <div className="mt-8">
          <EmptyState title="Bu kulübün kayıtlarını göremezsiniz">Kayıtlar kulübün görevlilerine ve danışmanına açıktır.</EmptyState>
        </div>
      ) : (
        <>
          {active && BY_YEAR.has(active) && (
            <div className="mt-6">
              <Field label="Akademik yıl" className="w-[12rem]">
                <Select
                  value={String(year)}
                  onChange={(e) =>
                    setParams(
                      (p) => {
                        const next = new URLSearchParams(p)
                        next.set('yil', e.target.value)
                        return next
                      },
                      { replace: true },
                    )
                  }
                >
                  {years.map((y) => (
                    <option key={y} value={y}>
                      {yearLabel(y)}
                      {y === current ? ' (bu yıl)' : ''}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>
          )}
          <div className="mt-10">
            <Tabs label="Kayıt bölümleri" tabs={tabs} />
          </div>
        </>
      )}
    </>
  )
}
