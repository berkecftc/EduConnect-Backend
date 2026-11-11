import { API_BASE, api, refreshSession } from '@/lib/api/client'
import { getSession } from '@/lib/auth/session'

/**
 * Yapay zekâ asistanı (F-04). Öğrenci asistanı `text/event-stream` döner ve yanıt parça parça gelir;
 * akademisyen yardımcısı tek yanıtlı JSON'dur. Sohbet geçmişini sunucu tutar, istek yalnız yeni mesajı taşır.
 */

export const MESSAGE_MAX = 2000

export type ChatTurn = { id: number; from: 'user' | 'assistant'; text: string; failed?: boolean }

/** SSE gövdesini olaylara ayırır; `rest` henüz tamamlanmamış son parçadır. Çok satırlı `data:` alanları satır sonuyla birleşir. */
export function parseSse(buffer: string): { chunks: string[]; rest: string } {
  const events = buffer.split(/\r?\n\r?\n/)
  const rest = events.pop() ?? ''
  const chunks = events
    .map((event) =>
      event
        .split(/\r?\n/)
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5))
        .join('\n'),
    )
    .filter((data) => data !== '')
  return { chunks, rest }
}

/** Sunucunun hata gövdesinden kullanıcıya gösterilecek cümle. */
export function assistantFailure(status: number, body: string): string {
  if (status === 429) return 'Kısa sürede çok soru sordunuz. Biraz bekleyip yeniden deneyin.'
  try {
    const parsed = JSON.parse(body) as { message?: string; detail?: string }
    if (parsed.message || parsed.detail) return (parsed.message ?? parsed.detail)!
  } catch {
    // Gövde JSON değil; genel cümle kullanılır.
  }
  return status >= 500 ? 'Asistan şu an yanıt veremiyor. Biraz sonra yeniden deneyin.' : 'Mesaj gönderilemedi.'
}

async function postStream(message: string, token: string, signal: AbortSignal) {
  return fetch(`${API_BASE}/ai/student-assistant`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ message }),
    signal,
  })
}

/** Öğrenci asistanına sorar; her parça geldiğinde `onChunk` çağrılır. Hata ileti cümlesiyle fırlatılır. */
export async function askStudentAssistant(message: string, onChunk: (text: string) => void, signal: AbortSignal): Promise<void> {
  const session = getSession()
  if (!session) throw new Error('Oturumunuz kapanmış. Yeniden giriş yapın.')
  let res = await postStream(message, session.token, signal)
  if (res.status === 401) {
    const next = await refreshSession(session.token).catch(() => null)
    if (!next) throw new Error('Oturumunuz kapanmış. Yeniden giriş yapın.')
    res = await postStream(message, next.token, signal)
  }
  if (!res.ok || !res.body) throw new Error(assistantFailure(res.status, await res.text().catch(() => '')))

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    const { chunks, rest } = parseSse(done ? `${buffer}\n\n` : buffer)
    buffer = rest
    chunks.forEach(onChunk)
    if (done) return
  }
}

/** Akademisyen yardımcısı: doğal dille yazılan isteği işler (ör. ders duyurusu oluşturur) ve sonucu yazar. */
export async function askInstructorCopilot(message: string, signal: AbortSignal): Promise<string> {
  return (await api.post<{ reply: string }>('/ai/instructor-copilot', { message }, { signal })).data.reply
}
