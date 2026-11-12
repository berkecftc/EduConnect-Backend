import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { toApiError } from '@/lib/api/problem'
import { usePanel, viewRoles } from '@/lib/auth/panel'
import { useSession } from '@/lib/auth/session'
import { cn } from '@/lib/cn'
import { usePageTitle } from '@/lib/usePageTitle'
import { Button } from '@/components/ui/Button'
import { Notice } from '@/components/ui/Notice'
import { PageHeader } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import { MESSAGE_MAX, askInstructorCopilot, askStudentAssistant, type ChatTurn } from './api'

const STUDENT_STARTERS = ['Bu hafta teslim etmem gereken ödevler hangileri?', 'İlgi alanıma uygun kulüp önerir misin?', 'Yaklaşan ödevlerimi tarih sırasıyla listele.']
const ACADEMICIAN_STARTERS = ['Ders kodu ve mesajla başlayın: "SEED101 dersi yarın iptal, telafisi haftaya yapılacak"']

/**
 * Yapay zekâ asistanı (F-04). Öğrenci ödevlerini ve kulüpleri sorar; akademisyen doğal dille ders duyurusu hazırlatır,
 * taslağı "onayla" ya da "iptal" yazarak sonuçlandırır. Konuşma yalnız bu sayfada durur; sayfadan çıkınca ekrandan silinir.
 */
export function AssistantPage() {
  usePageTitle('Asistan')
  const session = useSession()
  const { academician } = viewRoles(session, usePanel())
  const [turns, setTurns] = useState<ChatTurn[]>([])
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const nextId = useRef(1)
  const abort = useRef<AbortController | null>(null)
  const endRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => () => abort.current?.abort(), [])

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'nearest' })
  }, [turns])

  async function send(text: string) {
    const message = text.trim()
    if (!message || busy) return
    const replyId = nextId.current + 1
    setTurns((t) => [...t, { id: nextId.current, from: 'user', text: message }, { id: replyId, from: 'assistant', text: '' }])
    nextId.current += 2
    setDraft('')
    setBusy(true)
    const controller = new AbortController()
    abort.current = controller
    const patch = (fn: (turn: ChatTurn) => ChatTurn) => setTurns((t) => t.map((turn) => (turn.id === replyId ? fn(turn) : turn)))
    try {
      if (academician) {
        const reply = await askInstructorCopilot(message, controller.signal)
        patch((turn) => ({ ...turn, text: reply }))
      } else {
        await askStudentAssistant(message, (chunk) => patch((turn) => ({ ...turn, text: turn.text + chunk })), controller.signal)
        patch((turn) => (turn.text ? turn : { ...turn, text: 'Asistan boş yanıt döndürdü. Sorunuzu başka sözcüklerle yeniden deneyin.', failed: true }))
      }
    } catch (err) {
      if (controller.signal.aborted) {
        patch((turn) => ({ ...turn, text: turn.text || 'Yanıt durduruldu.', failed: !turn.text }))
      } else {
        const reason = err instanceof Error && !('status' in err) ? err.message : toApiError(err).message
        patch((turn) => ({ ...turn, text: reason, failed: true }))
      }
    } finally {
      setBusy(false)
      inputRef.current?.focus()
    }
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    void send(draft)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      void send(draft)
    }
  }

  return (
    <div className="mx-auto max-w-[56rem]">
      <PageHeader
        title="Asistan"
        meta={
          academician
            ? 'Ders duyurusunu doğal dille yazın; taslağı görüp onayladığınızda öğrencilere gider.'
            : 'Ödevlerinizi ve kulüpleri sorun. Yanıtlar yapay zekâ ile üretilir, yanlış olabilir.'
        }
        actions={
          turns.length > 0 && !busy ? (
            <Button size="sm" onClick={() => setTurns([])}>
              Konuşmayı temizle
            </Button>
          ) : undefined
        }
      />

      {turns.length === 0 ? (
        <section aria-label="Örnek sorular" className="mt-10 border-t border-rule">
          {(academician ? ACADEMICIAN_STARTERS : STUDENT_STARTERS).map((s) =>
            academician ? (
              <p key={s} className="border-b border-rule py-4 text-md text-ink-2">
                {s}. Taslağı yayımlamak için “onayla”, vazgeçmek için “iptal” yazın.
              </p>
            ) : (
              <button
                key={s}
                type="button"
                onClick={() => void send(s)}
                className="block w-full border-b border-rule py-4 text-left text-lg font-semibold text-ink transition-colors hover:bg-sunken"
              >
                {s}
              </button>
            ),
          )}
        </section>
      ) : (
        <ol aria-label="Konuşma" className="mt-10 border-t border-rule">
          {turns.map((turn) => (
            <li key={turn.id} className="grid grid-cols-1 gap-x-6 gap-y-1 border-b border-rule py-5 sm:grid-cols-[6rem_minmax(0,1fr)]">
              <span className="text-sm font-semibold text-ink-3">{turn.from === 'user' ? 'Siz' : 'Asistan'}</span>
              {turn.from === 'assistant' && !turn.text ? (
                <span role="status" className="text-md text-ink-3">
                  Yanıt hazırlanıyor…
                </span>
              ) : turn.failed ? (
                <Notice tone="danger" variant="line">
                  {turn.text}
                </Notice>
              ) : (
                <p
                  aria-live={turn.from === 'assistant' && busy ? 'off' : undefined}
                  className={cn('whitespace-pre-wrap break-words text-base', turn.from === 'user' ? 'text-ink-2' : 'text-ink')}
                >
                  {turn.text}
                </p>
              )}
            </li>
          ))}
        </ol>
      )}
      <div ref={endRef} />

      <form onSubmit={onSubmit} className="mt-8 flex flex-col gap-3">
        <label htmlFor="asistan-mesaj" className="text-md font-semibold text-ink">
          Mesajınız
        </label>
        <Textarea
          id="asistan-mesaj"
          ref={inputRef}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKeyDown}
          maxLength={MESSAGE_MAX}
          valueLength={draft.length}
          placeholder={academician ? 'SEED101 dersi yarın iptal, telafisi haftaya yapılacak' : 'Sorunuzu yazın'}
          className="min-h-24"
        />
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-ink-3">Enter gönderir, Shift+Enter yeni satır açar.</p>
          {busy ? (
            <Button onClick={() => abort.current?.abort()}>Durdur</Button>
          ) : (
            <Button type="submit" variant="primary" disabled={!draft.trim()}>
              Gönder
            </Button>
          )}
        </div>
      </form>
    </div>
  )
}
