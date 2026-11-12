import { useState } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { TIME_ZONE, formatInstant } from '@/lib/time'
import { Badge } from '@/components/ui/Badge'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { APPLICATION_STATUS_LABEL, APPLICATION_TONE, useWithdrawApplication, type CourseApplication } from './api'

const DAY = new Intl.DateTimeFormat('tr-TR', { day: 'numeric', timeZone: TIME_ZONE })
const MONTH = new Intl.DateTimeFormat('tr-TR', { month: 'short', year: 'numeric', timeZone: TIME_ZONE })

/** Sonuç anının adı: hoca kararı, öğrencinin geri çekmesi ya da dönemin kapanması. */
const PROCESSED_LABEL: Record<CourseApplication['status'], string> = {
  PENDING: '',
  APPROVED: 'karar',
  REJECTED: 'karar',
  WITHDRAWN: 'geri çekme',
  CLOSED: 'kapanış',
}

const ROW ='grid grid-cols-[3.5rem_minmax(0,1fr)] gap-x-5 gap-y-2 sm:grid-cols-[4.5rem_7rem_minmax(0,1fr)_11rem]'

/**
 * Başvurularım (F-44): bekleyenler üstte, sonuçlananlar altta. Satırda başvuru günü, ders kodu ve adı,
 * sonuç bilgisi (karar tarihi, ret gerekçesi) ve durum; bekleyende "Geri çek".
 */
export function Applications({ list }: { list: CourseApplication[] }) {
  const withdraw = useWithdrawApplication()
  const [target, setTarget] = useState<CourseApplication | null>(null)
  const byDate = (a: CourseApplication, b: CourseApplication) => b.applicationDate.localeCompare(a.applicationDate)
  const pending = list.filter((a) => a.status === 'PENDING').sort(byDate)
  const done = list.filter((a) => a.status !== 'PENDING').sort(byDate)

  return (
    <>
      {pending.length > 0 && <Group title="Hoca onayında" list={pending} onWithdraw={setTarget} />}
      {done.length > 0 && <Group title="Sonuçlananlar" list={done} className={pending.length > 0 ? 'mt-12' : undefined} />}
      <ConfirmDialog
        open={!!target}
        onOpenChange={(o) => !o && setTarget(null)}
        title="Başvuruyu geri çek"
        description={
          <>
            <span className="font-semibold text-ink">{target?.courseCode}</span> başvurunuz geri çekilecek. Kayıt dönemi açıksa
            yeniden başvurabilirsiniz.
          </>
        }
        confirmLabel="Başvuruyu geri çek"
        loading={withdraw.isPending}
        onConfirm={() =>
          target &&
          withdraw.mutate(target.id, {
            onSuccess: () => {
              toast.success('Başvuru geri çekildi')
              setTarget(null)
            },
            onError: (e) => toast.error(toApiError(e).message),
          })
        }
      />
    </>
  )
}

function Group({
  title,
  list,
  onWithdraw,
  className,
}: {
  title: string
  list: CourseApplication[]
  onWithdraw?: (a: CourseApplication) => void
  className?: string
}) {
  return (
    <section aria-label={title} className={className}>
      <h3 className="flex items-baseline gap-3 border-b border-ink pb-2">
        <span className="text-lg font-heavy">{title}</span>
        <span className="tabular text-md text-ink-3">{list.length}</span>
      </h3>
      <ul>
        {list.map((a) => {
          const at = new Date(a.applicationDate)
          return (
            <li key={a.id} className={`${ROW} border-b border-rule py-5`}>
              <span className="row-span-2 sm:row-span-1">
                <span className="tabular block text-3xl leading-none font-heavy">{DAY.format(at)}</span>
                <span className="mt-1 block text-xs text-ink-3">{MONTH.format(at)}</span>
              </span>
              <span className="hidden items-center gap-2 pt-1 sm:flex">
                <span aria-hidden className="h-4 w-[3px] bg-ders" />
                <span className="tabular truncate font-heavy">{a.courseCode}</span>
              </span>
              <span className="min-w-0">
                <span className="block text-lg font-semibold">
                  <span className="tabular font-heavy text-ders sm:hidden">{a.courseCode} </span>
                  {a.courseTitle}
                </span>
                <span className="mt-0.5 block text-sm text-ink-3">
                  {a.processedDate
                    ? `Başvuru ${formatInstant(a.applicationDate, 'datetime')}, ${PROCESSED_LABEL[a.status]} ${formatInstant(a.processedDate, 'datetime')}`
                    : `${formatInstant(a.applicationDate, 'datetime')} tarihinde başvurdunuz`}
                </span>
                {a.rejectionReason && <span className="mt-1 block max-w-[60ch] text-md text-ink-2">Gerekçe: {a.rejectionReason}</span>}
              </span>
              <span className="col-start-2 flex flex-wrap items-center gap-x-4 gap-y-1 sm:col-start-auto sm:flex-col sm:items-end sm:text-right">
                <Badge tone={APPLICATION_TONE[a.status]}>{APPLICATION_STATUS_LABEL[a.status]}</Badge>
                {onWithdraw && (
                  <button
                    type="button"
                    onClick={() => onWithdraw(a)}
                    className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
                  >
                    Geri çek
                  </button>
                )}
              </span>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
