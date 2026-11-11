import type { ReactNode } from 'react'
import { AlertDialog } from 'radix-ui'
import { Button } from './Button'

/**
 * Onay penceresi: geri alınamaz ya da sonuçları olan işlemler için.
 * Eylem düğmesi işlemin adını taşır ("Dersten çekil"), "Tamam" değil. Esc ve "Vazgeç" kapatır.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  destructive = false,
  loading = false,
  onConfirm,
  children,
  hideCancel = false,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: ReactNode
  confirmLabel: string
  destructive?: boolean
  loading?: boolean
  onConfirm: () => void
  /** Ek alanlar (ör. isteğe bağlı gerekçe). */
  children?: ReactNode
  /** Yalnız bilgi veren pencerede (tek "Tamam") iptal düğmesi gizlenir; Esc yine kapatır. */
  hideCancel?: boolean
}) {
  return (
    <AlertDialog.Root open={open} onOpenChange={onOpenChange}>
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="fixed inset-0 z-50 bg-scrim data-[state=open]:animate-fade-in" />
        <AlertDialog.Content className="fixed top-1/2 left-1/2 z-50 w-[min(28rem,calc(100vw-2rem))] -translate-x-1/2 -translate-y-1/2 rounded-lg border border-rule bg-surface p-6 shadow-pop">
          <AlertDialog.Title className="text-xl font-heavy">{title}</AlertDialog.Title>
          <AlertDialog.Description asChild>
            <div className="mt-2 text-md text-ink-2">{description}</div>
          </AlertDialog.Description>
          {children && <div className="mt-5">{children}</div>}
          <div className="mt-6 flex flex-wrap justify-end gap-2">
            {!hideCancel && (
              <AlertDialog.Cancel asChild>
                <Button variant="ghost">Vazgeç</Button>
              </AlertDialog.Cancel>
            )}
            <Button
              variant={destructive ? 'danger' : 'primary'}
              loading={loading}
              onClick={(e) => {
                e.preventDefault()
                onConfirm()
              }}
            >
              {confirmLabel}
            </Button>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  )
}
