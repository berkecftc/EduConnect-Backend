import { useState } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatInstant } from '@/lib/time'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Panel } from '@/components/ui/Panel'
import { Textarea } from '@/components/ui/Textarea'
import { PersonLink } from '@/features/people/PersonLink'
import { useDecideClubPost, type Post } from './api'

const NOTE_MAX = 1000

/** Akış duyuruları: görevlinin kulüp adına hazırladığı, başkan onayı bekleyen resmî duyurular (F-67). */
export function ClubPostApprovals({ posts }: { posts: Post[] }) {
  if (posts.length === 0) return null
  return (
    <Panel title="Akış duyuruları" action={<span className="tabular text-md text-warning">{posts.length} onay bekliyor</span>}>
      <p className="max-w-[64ch] text-md text-ink-2">
        Onayladığınız duyuru kulüp adına akışta herkese açılır. Yalnız üyelere giden kulüp duyuruları kulübün isteklerinde ayrıca listelenir.
      </p>
      <ul className="mt-5 border-t border-rule">
        {posts.map((p) => (
          <Row key={p.id} post={p} />
        ))}
      </ul>
    </Panel>
  )
}

function Row({ post: p }: { post: Post }) {
  const decide = useDecideClubPost()
  const [rejecting, setRejecting] = useState(false)
  const [note, setNote] = useState('')
  const fail = (e: unknown) => toast.error(toApiError(e).message)

  return (
    <li className="grid gap-x-8 gap-y-3 border-b border-rule py-5 md:grid-cols-[minmax(0,1fr)_auto]">
      <div className="min-w-0 border-l-[3px] border-kulup pl-4">
        <p className="text-sm font-semibold text-kulup">{p.publisherName}</p>
        <p className="mt-0.5 text-lg font-semibold">{p.title}</p>
        <p className="mt-1 max-w-[68ch] text-md whitespace-pre-line text-ink-2">{p.content}</p>
        <p className="mt-2 text-sm text-ink-3">
          Hazırlayan{' '}
          <PersonLink id={p.authorId} className="font-semibold text-ink-2">
            {p.authorName ?? 'görevli'}
          </PersonLink>
          , {formatInstant(p.createdAt, 'datetime')}
          {p.attachmentName ? `. Ek: ${p.attachmentName}` : ''}
          {p.commentsDisabled ? '. Yorumlara kapalı' : ''}
        </p>
      </div>
      <div className="flex items-center gap-4 md:items-start">
        <button
          type="button"
          onClick={() => setRejecting(true)}
          className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline"
        >
          Reddet<span className="sr-only">: {p.title}</span>
        </button>
        <Button
          size="sm"
          variant="primary"
          loading={decide.isPending && !rejecting}
          onClick={() =>
            decide.mutate({ postId: p.id, approve: true }, { onSuccess: () => toast.success('Duyuru onaylandı'), onError: fail })
          }
        >
          Onayla<span className="sr-only">: {p.title}</span>
        </Button>
      </div>
      <ConfirmDialog
        open={rejecting}
        onOpenChange={setRejecting}
        title="Duyuru reddedilsin mi?"
        description="Gerekçeniz hazırlayana iletilir; duyuruyu düzeltip yeniden gönderebilir."
        confirmLabel="Reddet"
        destructive
        loading={decide.isPending}
        onConfirm={() =>
          note.trim()
            ? decide.mutate(
                { postId: p.id, approve: false, note },
                {
                  onSuccess: () => {
                    toast.success('Duyuru reddedildi')
                    setRejecting(false)
                  },
                  onError: fail,
                },
              )
            : toast.error('Ret gerekçesi yazın.')
        }
      >
        <Field label="Gerekçe" required>
          <Textarea rows={3} maxLength={NOTE_MAX} valueLength={note.length} value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
      </ConfirmDialog>
    </li>
  )
}
