import type { ReactNode } from 'react'

/**
 * Uzun formların bölümü: ince üst çizgi, solda başlık ve kısa açıklama, sağda alanlar.
 * Başlık görsel; ekran okuyucu için fieldset adı `legend`'dan gelir.
 */
export function FormSection({ title, hint, children }: { title: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <fieldset className="grid gap-x-10 gap-y-4 border-t border-rule py-8 lg:grid-cols-[16rem_minmax(0,1fr)]">
      <legend className="sr-only">{title}</legend>
      <div>
        <p aria-hidden className="text-lg font-heavy">
          {title}
        </p>
        {hint && <p className="mt-1 text-sm text-ink-3">{hint}</p>}
      </div>
      <div className="flex max-w-[40rem] flex-col gap-5">{children}</div>
    </fieldset>
  )
}
