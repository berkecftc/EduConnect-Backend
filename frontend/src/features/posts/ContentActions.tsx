import { useState } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { ChoiceGroup } from '@/components/ui/ChoiceGroup'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Notice } from '@/components/ui/Notice'
import { Textarea } from '@/components/ui/Textarea'
import { REPORT_REASONS, useAppeal, useHide, useReport, type ReportReason, type Target } from './api'

const LINK = 'text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline'

/**
 * Şikâyet et (F-69): neden seçimi ve isteğe bağlı açıklama. Taciz ve tehdit gibi hassas nedenlerde
 * backend'in destek mesajı pencerede gösterilir; kapanmadan önce okunabilsin.
 */
export function ReportButton({ target }: { target: Target }) {
  const report = useReport()
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState<ReportReason | undefined>()
  const [details, setDetails] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [support, setSupport] = useState<string | null>(null)

  const close = () => {
    setOpen(false)
    setReason(undefined)
    setDetails('')
    setError(null)
    setSupport(null)
  }

  return (
    <>
      <button type="button" onClick={() => setOpen(true)} className={LINK}>
        Şikâyet et
      </button>
      {support ? (
        <ConfirmDialog open={open} onOpenChange={(o) => !o && close()} title="Şikâyetiniz alındı" description="" confirmLabel="Tamam" hideCancel onConfirm={close}>
          <Notice variant="line" tone="info">
            {support}
          </Notice>
        </ConfirmDialog>
      ) : (
        <ConfirmDialog
          open={open}
          onOpenChange={(o) => !o && close()}
          title={target.kind === 'post' ? 'Gönderiyi şikâyet et' : 'Yorumu şikâyet et'}
          description="Şikâyetiniz moderatörlere iletilir; kimliğiniz içerik sahibine gösterilmez."
          confirmLabel="Şikâyet et"
          loading={report.isPending}
          onConfirm={() => {
            if (!reason) return setError('Bir neden seçin.')
            setError(null)
            report.mutate(
              { target, reason, details },
              {
                onSuccess: (r) => {
                  if (r.sensitive && r.supportMessage) setSupport(r.supportMessage)
                  else {
                    toast.success('Şikâyetiniz moderatörlere iletildi')
                    close()
                  }
                },
                onError: (e) => {
                  const ae = toApiError(e)
                  setError(ae.code === 'ALREADY_REPORTED' ? 'Bu içeriği daha önce şikâyet ettiniz.' : ae.message)
                },
              },
            )
          }}
        >
          <div className="flex max-h-[55vh] flex-col gap-4 overflow-y-auto pr-1">
            <ChoiceGroup layout="rows" label="Neden" value={reason} onChange={setReason} choices={REPORT_REASONS.map(([value, title]) => ({ value, title }))} />
            <Field label="Açıklama" hint="İsteğe bağlı.">
              <Textarea maxLength={1000} valueLength={details.length} value={details} onChange={(e) => setDetails(e.target.value)} rows={3} />
            </Field>
            {error && <p className="text-sm font-semibold text-danger">{error}</p>}
          </div>
        </ConfirmDialog>
      )}
    </>
  )
}

/** Gizle (kapsam sahibi, F-69): gerekçe zorunlu; içerik yazara "Gizlendi" etiketiyle görünür. */
export function HideButton({ target }: { target: Target }) {
  const hide = useHide()
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  return (
    <>
      <button type="button" onClick={() => setOpen(true)} className={LINK}>
        Gizle
      </button>
      <ConfirmDialog
        open={open}
        onOpenChange={setOpen}
        title={target.kind === 'post' ? 'Gönderiyi gizle' : 'Yorumu gizle'}
        description="İçerik herkesten gizlenir; yazarı gerekçeyle birlikte görür ve itiraz edebilir."
        confirmLabel="Gizle"
        destructive
        loading={hide.isPending}
        onConfirm={() =>
          reason.trim()
            ? hide.mutate(
                { target, reason },
                {
                  onSuccess: () => {
                    toast.success('İçerik gizlendi')
                    setOpen(false)
                    setReason('')
                  },
                  onError: (e) => toast.error(toApiError(e).message),
                },
              )
            : toast.error('Gerekçe yazın.')
        }
      >
        <Field label="Gerekçe" required>
          <Textarea maxLength={1000} valueLength={reason.length} value={reason} onChange={(e) => setReason(e.target.value)} rows={3} />
        </Field>
      </ConfirmDialog>
    </>
  )
}

/** İtiraz et (F-70): reddedilen, gizlenen ya da kaldırılan içerik için bir kez; gerekçe zorunlu. */
export function AppealButton({ target }: { target: Target }) {
  const appeal = useAppeal()
  const [open, setOpen] = useState(false)
  const [statement, setStatement] = useState('')
  const [error, setError] = useState<string | null>(null)
  return (
    <>
      <button type="button" onClick={() => setOpen(true)} className={LINK}>
        İtiraz et
      </button>
      <ConfirmDialog
        open={open}
        onOpenChange={setOpen}
        title="Karara itiraz et"
        description="İtirazınızı ilk kararı vermeyen bir moderatör değerlendirir. Her karar için bir kez itiraz edebilirsiniz."
        confirmLabel="İtirazı gönder"
        loading={appeal.isPending}
        onConfirm={() => {
          if (!statement.trim()) return setError('Gerekçe yazın.')
          setError(null)
          appeal.mutate(
            { target, statement },
            {
              onSuccess: () => {
                toast.success('İtirazınız alındı')
                setOpen(false)
                setStatement('')
              },
              onError: (e) => {
                const ae = toApiError(e)
                setError(
                  ae.code === 'APPEAL_EXISTS'
                    ? 'Bu karara zaten itiraz ettiniz.'
                    : ae.code === 'NOT_APPEALABLE'
                      ? 'Bu karar itirazla değil, içeriği düzenleyip yeniden göndererek çözülür.'
                      : ae.message,
                )
              },
            },
          )
        }}
      >
        <Field label="Gerekçe" required>
          <Textarea maxLength={2000} valueLength={statement.length} value={statement} onChange={(e) => setStatement(e.target.value)} rows={4} />
        </Field>
        {error && <p className="mt-2 text-sm font-semibold text-danger">{error}</p>}
      </ConfirmDialog>
    </>
  )
}
