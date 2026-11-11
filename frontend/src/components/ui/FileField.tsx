import { useId, useRef, useState, type DragEvent } from 'react'
import { FileText, Upload, X } from 'lucide-react'
import { cn } from '@/lib/cn'

function formatSize(bytes: number) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`
}

/**
 * Dosya alanı: "Dosya seç" düğmesiyle çalışır; sürükle-bırak yalnız kolaylıktır (WCAG 2.2 sürükleme alternatifi).
 * Tür ve boyut istemcide ön kontrol edilir, asıl doğrulama sunucudadır.
 */
export function FileField({
  label,
  hint,
  accept,
  maxBytes,
  value,
  onChange,
  error,
  required,
}: {
  label: string
  hint?: string
  /** `accept` özniteliği, ör. "image/jpeg,image/png,application/pdf". Boşsa her tür kabul edilir. */
  accept?: string
  maxBytes: number
  value: File | null
  onChange: (file: File | null) => void
  error?: string
  required?: boolean
}) {
  const id = useId()
  const input = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [localError, setLocalError] = useState<string | null>(null)
  const shownError = error ?? localError ?? undefined
  const describedBy = [hint ? `${id}-hint` : null, shownError ? `${id}-error` : null].filter(Boolean).join(' ') || undefined

  const pick = (file: File | undefined) => {
    if (!file) return
    const allowed = accept ? accept.split(",").map((t) => t.trim()) : null
    if (allowed && !allowed.includes(file.type)) {
      setLocalError('Bu dosya türü kabul edilmiyor. ' + (hint ?? ''))
      return
    }
    if (file.size > maxBytes) {
      setLocalError(`Dosya en fazla ${formatSize(maxBytes)} olabilir.`)
      return
    }
    setLocalError(null)
    onChange(file)
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    setDragging(false)
    pick(e.dataTransfer.files[0])
  }

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-md font-semibold text-ink">
        {label}
        {required && <span aria-hidden className="text-danger"> *</span>}
      </label>
      <input
        ref={input}
        id={id}
        type="file"
        accept={accept}
        required={required}
        aria-describedby={describedBy}
        aria-invalid={shownError ? true : undefined}
        className="sr-only"
        onChange={(e) => {
          pick(e.target.files?.[0])
          e.target.value = ''
        }}
      />
      {value ? (
        <div className="flex items-center gap-3 rounded-md border border-control bg-surface px-3 py-2.5">
          <FileText aria-hidden className="size-5 shrink-0 text-ink-2" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-md font-semibold">{value.name}</p>
            <p className="text-sm text-ink-3">{formatSize(value.size)}</p>
          </div>
          <button
            type="button"
            onClick={() => onChange(null)}
            className="grid size-10 place-items-center rounded-md text-ink-3 hover:bg-sunken hover:text-ink"
            aria-label={`${value.name} dosyasını kaldır`}
          >
            <X aria-hidden className="size-4" />
          </button>
        </div>
      ) : (
        <div
          onDragOver={(e) => {
            e.preventDefault()
            setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
          className={cn(
            'flex flex-col items-center gap-2 rounded-md border border-dashed px-4 py-6 text-center transition-colors',
            dragging ? 'border-ink bg-sunken' : shownError ? 'border-danger' : 'border-control',
          )}
        >
          <Upload aria-hidden className="size-5 text-ink-2" />
          <button
            type="button"
            onClick={() => input.current?.click()}
            className="rounded-md px-2 py-1 font-semibold text-ders underline-offset-2 hover:underline"
          >
            Dosya seç
          </button>
          <p className="text-sm text-ink-3">ya da buraya sürükleyin</p>
        </div>
      )}
      {hint && !shownError && (
        <p id={`${id}-hint`} className="text-sm text-ink-3">
          {hint}
        </p>
      )}
      {shownError && (
        <p id={`${id}-error`} className="text-sm font-semibold text-danger">
          {shownError}
        </p>
      )}
    </div>
  )
}
