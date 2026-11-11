import type { ReactNode } from 'react'
import { Dialog as RDialog } from 'radix-ui'
import { X } from 'lucide-react'

/**
 * Geniş çalışma penceresi (ör. teslim puanlama): başlık ve kısa bilgi sabit, gövde kayar, eylemler altta sabit.
 * Onay soruları için ConfirmDialog kullanılır; bu pencere içerik okuyup karar vermek içindir.
 */
export function Dialog({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: ReactNode
  description?: ReactNode
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <RDialog.Root open={open} onOpenChange={onOpenChange}>
      <RDialog.Portal>
        <RDialog.Overlay className="fixed inset-0 z-50 bg-scrim data-[state=open]:animate-fade-in" />
        <RDialog.Content className="fixed top-1/2 left-1/2 z-50 flex max-h-[min(52rem,calc(100dvh-2rem))] w-[min(46rem,calc(100vw-2rem))] -translate-x-1/2 -translate-y-1/2 flex-col rounded-lg border border-rule bg-surface shadow-pop outline-none">
          <div className="flex items-start justify-between gap-4 border-b border-rule px-6 pt-5 pb-4">
            <div className="min-w-0">
              <RDialog.Title className="text-xl font-heavy">{title}</RDialog.Title>
              {description ? (
                <RDialog.Description asChild>
                  <div className="mt-1 text-md text-ink-2">{description}</div>
                </RDialog.Description>
              ) : (
                <RDialog.Description className="sr-only">{title}</RDialog.Description>
              )}
            </div>
            <RDialog.Close
              className="-mt-1 -mr-2 grid size-10 shrink-0 place-items-center rounded-md text-ink-3 hover:text-ink"
              aria-label="Kapat"
            >
              <X className="size-5" aria-hidden />
            </RDialog.Close>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">{children}</div>
          {footer && <div className="flex flex-wrap items-center justify-end gap-x-4 gap-y-2 border-t border-rule px-6 py-4">{footer}</div>}
        </RDialog.Content>
      </RDialog.Portal>
    </RDialog.Root>
  )
}
