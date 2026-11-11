import { useId, useState } from 'react'
import { Download, ExternalLink } from 'lucide-react'
import { toast } from 'sonner'
import { downloadFile } from '@/lib/api/client'
import { toApiError } from '@/lib/api/problem'
import { formatInstant } from '@/lib/time'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ChoiceGroup } from '@/components/ui/ChoiceGroup'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { FileField } from '@/components/ui/FileField'
import { Input } from '@/components/ui/Input'
import { EmptyState, QueryBoundary } from '@/components/ui/States'
import { Switch } from '@/components/ui/Switch'
import { Textarea } from '@/components/ui/Textarea'
import { groupMaterials, useAnnouncements, useMaterials, type Course, type Material, type StaffRole } from './api'
import {
  abilities,
  useAddMaterial,
  useDeleteAnnouncement,
  useDeleteMaterial,
  usePostAnnouncement,
  useUpdateMaterial,
  type MaterialDraft,
} from './teach'

const LINK = 'text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline'
const DANGER_LINK = 'text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline'
const MATERIAL_MAX_BYTES = 20 * 1024 * 1024

// ——— Duyurular ———

/** Duyurular (kadro): koordinatör ve hoca yazar ve siler; süren ve tamamlanan derste açık. */
export function CourseAnnouncementsManage({ course: c, role }: { course: Course; role: StaffRole }) {
  const list = useAnnouncements(c.id)
  const can = abilities(role).announce
  const open = c.status === 'OPEN' || c.status === 'ACTIVE' || c.status === 'COMPLETED'
  const [writing, setWriting] = useState(false)

  return (
    <div className="flex flex-col gap-8">
      {can &&
        open &&
        (writing ? (
          <AnnouncementForm course={c} onDone={() => setWriting(false)} />
        ) : (
          <div>
            <Button onClick={() => setWriting(true)}>Duyuru yaz</Button>
          </div>
        ))}
      <QueryBoundary query={list} what="Duyurular">
        {(items) =>
          items.length === 0 ? (
            <EmptyState title="Duyuru yok">
              {can && open
                ? 'Yazdığınız duyurular kayıtlı öğrencilere bildirim olarak gider ve burada listelenir.'
                : 'Bu derste henüz duyuru yapılmadı.'}
            </EmptyState>
          ) : (
            <ul className="divide-y divide-rule border-y border-rule">
              {items.map((a) => (
                <li key={a.id} className="py-6">
                  <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                    <h3 className="text-lg font-semibold">{a.title}</h3>
                    {can && c.status !== 'ARCHIVED' && <DeleteAnnouncement courseId={c.id} id={a.id} title={a.title} />}
                  </div>
                  <p className="mt-0.5 text-sm text-ink-3">
                    {a.createdByName ? `${a.createdByName}, ` : ''}
                    {formatInstant(a.createdAt, 'datetime')}
                  </p>
                  <p className="mt-3 max-w-[68ch] whitespace-pre-line">{a.content}</p>
                </li>
              ))}
            </ul>
          )
        }
      </QueryBoundary>
    </div>
  )
}

function AnnouncementForm({ course: c, onDone }: { course: Course; onDone: () => void }) {
  const post = usePostAnnouncement(c.id)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [errors, setErrors] = useState<{ title?: string; content?: string; general?: string }>({})

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    const next = { title: title.trim() ? undefined : 'Başlık yazın.', content: content.trim() ? undefined : 'Duyuru metnini yazın.' }
    setErrors(next)
    if (next.title || next.content) return
    post.mutate(
      { title, content },
      {
        onSuccess: () => {
          toast.success('Duyuru yayımlandı; öğrencilere bildirim gitti')
          onDone()
        },
        onError: (err) => setErrors({ general: toApiError(err).message }),
      },
    )
  }

  return (
    <form onSubmit={submit} noValidate className="flex max-w-[44rem] flex-col gap-5 border-l-[3px] border-ders pl-5">
      <p className="text-lg font-heavy">Yeni duyuru</p>
      <Field label="Başlık" required error={errors.title}>
        <Input autoFocus maxLength={255} value={title} onChange={(e) => setTitle(e.target.value)} />
      </Field>
      <Field label="Duyuru" required error={errors.content} hint="Kayıtlı öğrencilere bildirim olarak gider.">
        <Textarea rows={6} maxLength={5000} valueLength={content.length} value={content} onChange={(e) => setContent(e.target.value)} />
      </Field>
      {errors.general && <p className="text-sm font-semibold text-danger">{errors.general}</p>}
      <div className="flex items-center gap-5">
        <Button type="submit" variant="primary" loading={post.isPending}>
          Yayımla
        </Button>
        <button
          type="button"
          onClick={onDone}
          className="text-md font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
        >
          Vazgeç
        </button>
      </div>
    </form>
  )
}

function DeleteAnnouncement({ courseId, id, title }: { courseId: string; id: string; title: string }) {
  const remove = useDeleteAnnouncement(courseId)
  const [open, setOpen] = useState(false)
  return (
    <>
      <button type="button" onClick={() => setOpen(true)} className={DANGER_LINK}>
        Sil<span className="sr-only">: {title}</span>
      </button>
      <ConfirmDialog
        open={open}
        onOpenChange={setOpen}
        destructive
        title="Duyuruyu sil"
        description={`“${title}” ders sayfasından kaldırılır. Gönderilmiş bildirimler geri alınmaz.`}
        confirmLabel="Sil"
        loading={remove.isPending}
        onConfirm={() =>
          remove.mutate(id, {
            onSuccess: () => {
              toast.success('Duyuru silindi')
              setOpen(false)
            },
            onError: (e) => toast.error(toApiError(e).message),
          })
        }
      />
    </>
  )
}

// ——— Materyaller ———

/** Materyaller (kadro): bölümlere göre; gizli olanlar işaretli. Koordinatör ve hoca ekler, gizler, siler (arşivde salt okunur). */
export function CourseMaterialsManage({ course: c, role }: { course: Course; role: StaffRole }) {
  const materials = useMaterials(c.id)
  const manage = abilities(role).materials && c.status !== 'ARCHIVED'
  const [adding, setAdding] = useState(false)
  const sections = [...new Set((materials.data ?? []).map((m) => m.section?.trim()).filter((s): s is string => !!s))]

  return (
    <div className="flex flex-col gap-8">
      {manage &&
        (adding ? (
          <MaterialForm course={c} sections={sections} onDone={() => setAdding(false)} />
        ) : (
          <div>
            <Button onClick={() => setAdding(true)}>Materyal ekle</Button>
          </div>
        ))}
      <QueryBoundary query={materials} what="Materyaller">
        {(list) => {
          if (list.length === 0) {
            return (
              <EmptyState title="Henüz materyal yok">
                {manage
                  ? 'Ders notu, sunum ya da bağlantı ekleyin; bölümlere ayırırsanız öğrenciler haftalara göre bulur.'
                  : 'Bu derse henüz materyal eklenmedi.'}
              </EmptyState>
            )
          }
          const hidden = list.filter((m) => !m.visible).length
          return (
            <div className="flex flex-col gap-10">
              {hidden > 0 && <p className="text-md text-ink-2">{hidden} materyal öğrencilere gizli; yalnız kadro görür.</p>}
              {groupMaterials(list).map(([section, items]) => (
                <section key={section} aria-label={section}>
                  <h2 className="border-b border-ink pb-2 text-lg font-heavy">{section}</h2>
                  <ul className="divide-y divide-rule">
                    {items.map((m) => (
                      <MaterialRow key={m.id} course={c} material={m} manage={manage} />
                    ))}
                  </ul>
                </section>
              ))}
            </div>
          )
        }}
      </QueryBoundary>
    </div>
  )
}

function MaterialRow({ course: c, material: m, manage }: { course: Course; material: Material; manage: boolean }) {
  const update = useUpdateMaterial(c.id)
  const remove = useDeleteMaterial(c.id)
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const fail = (e: unknown) => toast.error(toApiError(e).message)

  const download = async () => {
    setBusy(true)
    try {
      await downloadFile(`/courses/${c.id}/materials/${m.id}/file`, m.fileName ?? m.title)
    } catch (e) {
      fail(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className="grid gap-x-6 gap-y-3 py-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
      <div className="min-w-0">
        <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <span className={m.visible ? 'font-semibold' : 'font-semibold text-ink-2'}>{m.title}</span>
          {!m.visible && <Badge tone="warning">Öğrencilere gizli</Badge>}
        </p>
        {m.description && <p className="mt-0.5 text-sm text-ink-2">{m.description}</p>}
        <p className="mt-0.5 truncate text-xs text-ink-3">
          {m.kind === 'LINK' ? m.linkUrl : m.fileName}, {formatInstant(m.updatedAt, 'date')} güncellendi
        </p>
      </div>
      <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
        {m.kind === 'LINK' && m.linkUrl ? (
          <Button asChild size="sm">
            <a href={m.linkUrl} target="_blank" rel="noopener noreferrer">
              <ExternalLink className="size-4" aria-hidden />
              Aç<span className="sr-only">: {m.title} (yeni sekmede)</span>
            </a>
          </Button>
        ) : (
          <Button size="sm" loading={busy} onClick={() => void download()}>
            <Download className="size-4" aria-hidden />
            İndir<span className="sr-only">: {m.fileName ?? m.title}</span>
          </Button>
        )}
        {manage && (
          <>
            <button
              type="button"
              disabled={update.isPending}
              onClick={() =>
                update.mutate(
                  { id: m.id, visible: !m.visible },
                  {
                    onSuccess: () => toast.success(m.visible ? 'Materyal öğrencilerden gizlendi' : 'Materyal öğrencilere açıldı'),
                    onError: fail,
                  },
                )
              }
              className={LINK}
            >
              {m.visible ? 'Gizle' : 'Göster'}
              <span className="sr-only">: {m.title}</span>
            </button>
            <button type="button" onClick={() => setConfirm(true)} className={DANGER_LINK}>
              Sil<span className="sr-only">: {m.title}</span>
            </button>
            <ConfirmDialog
              open={confirm}
              onOpenChange={setConfirm}
              destructive
              title="Materyali sil"
              description={`“${m.title}” ve dosyası kalıcı olarak silinir. Yalnız gizlemek isterseniz Gizle’yi kullanın.`}
              confirmLabel="Sil"
              loading={remove.isPending}
              onConfirm={() =>
                remove.mutate(m.id, {
                  onSuccess: () => {
                    toast.success('Materyal silindi')
                    setConfirm(false)
                  },
                  onError: fail,
                })
              }
            />
          </>
        )}
      </div>
    </li>
  )
}

/** Şemasız yazılan adres (www.ornek.edu.tr) https:// ile tamamlanır; başka şemalar olduğu gibi kalır ve doğrulamada elenir. */
const withScheme = (url: string) => {
  const u = url.trim()
  return !u || /^[a-z][a-z0-9+.-]*:/i.test(u) ? u : `https://${u}`
}

const EMPTY_MATERIAL: MaterialDraft = { title: '', description: '', section: '', kind: 'FILE', linkUrl: '', visible: true }

function MaterialForm({ course: c, sections, onDone }: { course: Course; sections: string[]; onDone: () => void }) {
  const add = useAddMaterial(c.id)
  const listId = useId()
  const [d, setD] = useState<MaterialDraft>(EMPTY_MATERIAL)
  const [file, setFile] = useState<File | null>(null)
  const [errors, setErrors] = useState<{ title?: string; file?: string; linkUrl?: string; general?: string }>({})
  const set = (k: 'title' | 'description' | 'section' | 'linkUrl') => (e: { target: { value: string } }) =>
    setD((x) => ({ ...x, [k]: e.target.value }))

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    const next: typeof errors = {}
    if (!d.title.trim()) next.title = 'Başlık yazın.'
    if (d.kind === 'FILE' && !file) next.file = 'Dosya seçin.'
    const linkUrl = withScheme(d.linkUrl)
    if (d.kind === 'LINK' && !/^https?:\/\/[^\s/.]+\.\S+$/i.test(linkUrl))
      next.linkUrl = 'Geçerli bir web adresi yazın (ör. https://ornek.edu.tr).'
    setErrors(next)
    if (Object.keys(next).length) return
    add.mutate(
      { draft: { ...d, linkUrl }, file },
      {
        onSuccess: () => {
          toast.success(d.visible ? 'Materyal eklendi; öğrenciler görebilir' : 'Materyal eklendi; öğrencilere gizli')
          onDone()
        },
        onError: (err) => {
          const ae = toApiError(err)
          if (ae.code === 'MATERIAL_FILE_REQUIRED') setErrors({ file: ae.message })
          else if (ae.code === 'MATERIAL_LINK_REQUIRED' || ae.code === 'INVALID_MATERIAL_LINK') setErrors({ linkUrl: ae.message })
          else setErrors({ general: ae.message })
        },
      },
    )
  }

  return (
    <form onSubmit={submit} noValidate className="flex max-w-[44rem] flex-col gap-5 border-l-[3px] border-ders pl-5">
      <p className="text-lg font-heavy">Yeni materyal</p>
      <ChoiceGroup
        layout="rows"
        label="Tür"
        value={d.kind}
        onChange={(kind) => setD((x) => ({ ...x, kind }))}
        choices={[
          { value: 'FILE', title: 'Dosya', description: 'Ders notu, sunum ya da kod; en fazla 20 MB.' },
          { value: 'LINK', title: 'Bağlantı', description: 'Video, makale ya da başka bir site.' },
        ]}
      />
      <Field label="Başlık" required error={errors.title}>
        <Input autoFocus maxLength={255} value={d.title} onChange={set('title')} />
      </Field>
      <Field label="Bölüm" hint="İsteğe bağlı. Ör. Hafta 3. Aynı bölümdekiler birlikte listelenir.">
        <Input maxLength={100} list={listId} value={d.section} onChange={set('section')} className="max-w-[16rem]" />
      </Field>
      <datalist id={listId}>
        {sections.map((s) => (
          <option key={s} value={s} />
        ))}
      </datalist>
      <Field label="Açıklama" hint="İsteğe bağlı.">
        <Textarea rows={3} maxLength={10000} value={d.description} onChange={set('description')} />
      </Field>
      {d.kind === 'FILE' ? (
        <FileField
          label="Dosya"
          required
          maxBytes={MATERIAL_MAX_BYTES}
          value={file}
          onChange={setFile}
          error={errors.file}
          hint="En fazla 20 MB."
        />
      ) : (
        <Field label="Bağlantı" required error={errors.linkUrl}>
          <Input type="text" inputMode="url" maxLength={2000} placeholder="https://" value={d.linkUrl} onChange={set('linkUrl')} />
        </Field>
      )}
      <div className="flex items-center justify-between gap-6 border-y border-rule py-2">
        <span>
          <span className="block font-semibold">Öğrencilere görünsün</span>
          <span className="block text-sm text-ink-3">Kapalıysa yalnız kadro görür; sonradan açabilirsiniz.</span>
        </span>
        <Switch checked={d.visible} onCheckedChange={(visible) => setD((x) => ({ ...x, visible }))} label="Öğrencilere görünsün" />
      </div>
      {errors.general && <p className="text-sm font-semibold text-danger">{errors.general}</p>}
      <div className="flex items-center gap-5">
        <Button type="submit" variant="primary" loading={add.isPending}>
          Ekle
        </Button>
        <button
          type="button"
          onClick={onDone}
          className="text-md font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
        >
          Vazgeç
        </button>
      </div>
    </form>
  )
}
