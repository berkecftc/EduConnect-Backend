import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import type { ApiError } from './problem'

/**
 * Backend `errors[]` alan hatalarını form alanlarına yazar (F-11).
 * Formda karşılığı olan en az bir alan hatası yazıldıysa `true` döner.
 */
export function applyFieldErrors<T extends FieldValues>(
  error: ApiError,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): boolean {
  let applied = false
  for (const fe of error.fieldErrors) {
    if ((fields as readonly string[]).includes(fe.field)) {
      setError(fe.field as Path<T>, { type: 'server', message: fe.message })
      applied = true
    }
  }
  return applied
}
