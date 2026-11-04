import { useId, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { hasRole, useSession } from '@/lib/auth/session'
import { usePageTitle } from '@/lib/usePageTitle'
import { useMyCourses } from '@/features/courses/api'
import { Button } from '@/components/ui/Button'
import { ChoiceGroup } from '@/components/ui/ChoiceGroup'
import { Field } from '@/components/ui/Field'
import { FileField } from '@/components/ui/FileField'
import { Input } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { Panel } from '@/components/ui/Panel'
import { Select } from '@/components/ui/Select'
import { EmptyState, PageHeader, QueryBoundary, Skeleton } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import { CATEGORY_LABEL, uploadAttachment, useAttachment, usePost, useSavePost, type Post, type PostInput } from './api'
import { usePublishers, type Publisher } from './publishers'

const ATTACHMENT_MAX = 20 * 1024 * 1024
const CONTENT_MAX = 10_000

/** Sunucu hata kodu → alan (F-67). */
const CODE_FIELD: Record<string, 'publisher' | 'publisherName' | 'course' | 'declaration'> = {
  PUBLISHER_REQUIRED: 'publisher',
  SCOPE_REQUIRED: 'publisher',
  NOT_CLUB_PUBLISHER: 'publisher',
  NOT_COURSE_PUBLISHER: 'publisher',
  NOT_CAMPUS_PUBLISHER: 'publisher',
  COURSE_ARCHIVED: 'publisher',
  PUBLISHER_NAME_REQUIRED: 'publisherName',
  NOT_COURSE_MEMBER: 'course',
  SCOPE_NOT_ALLOWED: 'course',
  DECLARATION_REQUIRED: 'declaration',
}

/**
 * Gönderi yaz / düzenle (F-67, F-71). Öğrenci soru, ders notu ve genel gönderi yazar; kulüp, ders ya da kampüs adına
 * yetkisi olan resmî duyuru da yayımlar. Öğrenci olmayan yayıncı yalnız duyuru yazar.
 */
export function PostComposerPage() {
  const { postId } = useParams()
  const session = useSession()
  const student = hasRole(session, 'ROLE_STUDENT', 'ROLE_CLUB_OFFICIAL')
  const title = postId ? 'Gönderiyi düzenle' : student ? 'Gönderi yaz' : 'Duyuru yaz'
  usePageTitle(title)
  const post = usePost(postId ?? '', !!postId)
  const { publishers, loading } = usePublishers()

  if (!postId && !student) {
    if (loading) return <Skeleton rows={4} />
    if (publishers.length === 0) {
      return (
        <div className="mx-auto max-w-[56rem]">
          <EmptyState title="Gönderi yazma öğrencilere açık">Akışı okuyabilir ve gönderilere yorum yazabilirsiniz.</EmptyState>
        </div>
      )
    }
  }
  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader title={title} />
      <div className="mt-10 grid gap-14 lg:grid-cols-12 lg:gap-10">
        <div className="min-w-0 lg:col-span-8">
          {postId ? (
            <QueryBoundary query={post} what="Gönderi" skeletonRows={4}>
              {(p) => <Composer existing={p} publishers={publishers} student={student} />}
            </QueryBoundary>
          ) : (
            <Composer publishers={publishers} student={student} />
          )}
        </div>
        {student && <PointsNote />}
      </div>
    </div>
  )
}

function PointsNote() {
  return (
    <aside className="lg:col-span-4">
      <Panel title="Puan">
        <p className="text-md text-ink-2">Puan girişle değil katkıyla kazanılır:</p>
        <dl className="mt-4">
          {(
            [
              ['Kabul edilen cevap', '+15'],
              ['Haklı çıkan şikâyet', '+10'],
              ['Kaydedilen ders notu', '+3'],
              ['Beğenilen ders notu', '+2'],
            ] as const
          ).map(([k, v]) => (
            <div key={k} className="flex items-baseline justify-between border-b border-rule py-2.5 first:pt-0">
              <dt className="text-md">{k}</dt>
              <dd className="tabular text-lg font-heavy">{v}</dd>
            </div>
          ))}
        </dl>
        <p className="mt-4 text-sm text-ink-3">Günlük sınırlar vardır. İçerik kaldırılırsa kazandırdığı puan geri alınır.</p>
      </Panel>
    </aside>
  )
}

/** Kaydettikten sonra yazara söylenen: duyuru başkan onayına gitti, yayımlandı ya da kontrol ediliyor. */
function savedMessage(p: Post, editing: boolean) {
  if (p.status === 'AWAITING_APPROVAL') return 'Duyuru kulüp başkanının onayına gönderildi'
  if (editing) return 'Gönderiniz güncellendi'
  return p.status === 'PUBLISHED' ? 'Gönderiniz yayımlandı' : 'Gönderiniz kontrol edildikten sonra yayımlanacak'
}

function Composer({ existing, publishers, student }: { existing?: Post; publishers: Publisher[]; student: boolean }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const save = useSavePost(existing?.id)
  const courses = useMyCourses(student)
  const declarationId = useId()
  const commentsId = useId()
  const editing = !!existing
  const [category, setCategory] = useState<PostInput['category']>(existing?.category ?? (student ? 'SORU' : 'DUYURU'))
  const [title, setTitle] = useState(existing?.title ?? '')
  const [content, setContent] = useState(existing?.content ?? '')
  const [courseId, setCourseId] = useState(existing?.courseId ?? '')
  const [declared, setDeclared] = useState(false)
  const [publisherKey, setPublisherKey] = useState('')
  const [publisherName, setPublisherName] = useState('')
  const [commentsDisabled, setCommentsDisabled] = useState(existing?.commentsDisabled ?? false)
  const [file, setFile] = useState<File | null>(null)
  const [errors, setErrors] = useState<Partial<Record<'title' | 'content' | 'declaration' | 'course' | 'publisher' | 'publisherName', string>>>({})
  const [failure, setFailure] = useState<ApiError | null>(null)
  const [uploading, setUploading] = useState(false)
  const note = category === 'DERS_NOTU'
  const announcement = category === 'DUYURU'
  const attachable = note || announcement
  // Tek seçenek varsa sorulmaz.
  const publisher = publishers.find((p) => p.key === publisherKey) ?? (publishers.length === 1 ? publishers[0] : undefined)

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    const next: typeof errors = {}
    if (!title.trim()) next.title = 'Başlık yazın.'
    if (!content.trim()) next.content = note ? 'Notunuzu yazın.' : announcement ? 'Duyuruyu yazın.' : 'İçeriği yazın.'
    if (note && !editing && !declared) next.declaration = 'Paylaşmadan önce bu beyanı onaylayın.'
    if (announcement && !editing && !publisher) next.publisher = 'Duyurunun kimin adına yayımlanacağını seçin.'
    if (announcement && !editing && publisher?.type === 'CAMPUS' && !publisherName.trim()) next.publisherName = 'Yayımlayan birimin adını yazın.'
    setErrors(next)
    setFailure(null)
    if (Object.keys(next).length) return
    save.mutate(
      {
        title,
        content,
        category,
        courseId: category === 'SORU' || note ? courseId || null : null,
        sharingDeclaration: declared,
        publisher,
        publisherName,
        commentsDisabled,
      },
      {
        onSuccess: async (p) => {
          if (attachable && file) {
            setUploading(true)
            try {
              await uploadAttachment(p.id, file)
              void qc.invalidateQueries({ queryKey: ['posts', p.id] })
            } catch (err) {
              toast.error(`Gönderi kaydedildi ama ek yüklenemedi: ${toApiError(err).message}`)
            } finally {
              setUploading(false)
            }
          }
          toast.success(savedMessage(p, editing))
          navigate(`/posts/${p.id}`)
        },
        onError: (err) => {
          const ae = toApiError(err)
          const field = ae.code ? CODE_FIELD[ae.code] : undefined
          if (field) setErrors({ [field]: ae.message })
          else setFailure(ae)
        },
      },
    )
  }

  return (
    <form onSubmit={submit} noValidate className="flex max-w-[44rem] flex-col gap-7">
      {editing ? (
        <p className="text-md text-ink-2">
          Tür: <span className="font-semibold text-ink">{CATEGORY_LABEL[category]}</span>
          {existing?.official ? `, ${existing.publisherName ?? ''}. Tür ve yayımlayan sonradan değiştirilemez.` : `${existing?.courseLabel ? `, ${existing.courseLabel}` : ''}. Tür ve ders sonradan değiştirilemez.`}
        </p>
      ) : (
        student && (
          <ChoiceGroup
            layout="rows"
            label="Ne paylaşıyorsunuz?"
            value={category}
            onChange={setCategory}
            choices={[
              { value: 'SORU', title: 'Soru', description: 'Takıldığınız bir konuyu sorun; gelen cevaplardan birini seçebilirsiniz.' },
              { value: 'DERS_NOTU', title: 'Ders notu', description: 'Kendi hazırladığınız notu paylaşın; dosya ekleyebilirsiniz.' },
              { value: 'GENEL', title: 'Genel', description: 'Derse bağlı olmayan tartışma ve paylaşımlar.' },
              ...(publishers.length > 0
                ? [{ value: 'DUYURU' as const, title: 'Duyuru', description: 'Yetkili olduğunuz kulüp ya da birim adına akışta herkese duyurun.' }]
                : []),
            ]}
          />
        )
      )}

      {announcement && !editing && (
        <PublisherFields
          publishers={publishers}
          publisher={publisher}
          publisherKey={publisherKey}
          onPublisher={(k) => {
            setPublisherKey(k)
            setErrors((x) => ({ ...x, publisher: undefined, publisherName: undefined }))
          }}
          publisherName={publisherName}
          onPublisherName={setPublisherName}
          errors={errors}
        />
      )}

      {!editing && (category === 'SORU' || note) && (
        <Field label="Ders" hint="İsteğe bağlı. Yalnız kayıtlı olduğunuz ya da aldığınız dersler." error={errors.course}>
          <Select value={courseId} onChange={(e) => setCourseId(e.target.value)} disabled={courses.isPending}>
            <option value="">Derse bağlı değil</option>
            {(courses.data ?? []).map((c) => (
              <option key={c.id} value={c.id}>
                {c.code} {c.title}
              </option>
            ))}
          </Select>
        </Field>
      )}

      <Field label="Başlık" required error={errors.title}>
        <Input
          maxLength={255}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder={category === 'SORU' ? 'Ör. Kırmızı-siyah ağaçta silme nasıl dengelenir?' : undefined}
        />
      </Field>
      <Field label={note ? 'Not' : category === 'SORU' ? 'Soru' : announcement ? 'Duyuru' : 'İçerik'} required error={errors.content}>
        <Textarea
          maxLength={CONTENT_MAX}
          valueLength={content.length}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          rows={10}
        />
      </Field>

      {announcement && (
        <label htmlFor={commentsId} className="flex cursor-pointer items-start gap-3 text-md">
          <input
            id={commentsId}
            type="checkbox"
            checked={commentsDisabled}
            onChange={(e) => setCommentsDisabled(e.target.checked)}
            className="mt-0.5 size-[18px] shrink-0 cursor-pointer accent-[var(--ec-ink)]"
          />
          <span>Yorumlara kapalı</span>
        </label>
      )}

      {attachable &&
        (editing ? (
          <AttachmentEditor post={existing!} />
        ) : (
          <FileField
            label="Ek dosya"
            hint="İsteğe bağlı, en fazla 20 MB. PDF, görsel ya da belge; çalıştırılabilir dosyalar kabul edilmez."
            maxBytes={ATTACHMENT_MAX}
            value={file}
            onChange={setFile}
          />
        ))}

      {note && !editing && (
        <div>
          <label htmlFor={declarationId} className="flex cursor-pointer items-start gap-3 text-md">
            <input
              id={declarationId}
              type="checkbox"
              checked={declared}
              onChange={(e) => setDeclared(e.target.checked)}
              aria-invalid={errors.declaration ? true : undefined}
              className="mt-0.5 size-[18px] shrink-0 cursor-pointer accent-[var(--ec-ink)]"
            />
            <span>Bu not bana ait; sınav sorusu, cevap anahtarı ya da telifli materyal içermiyor.</span>
          </label>
          {errors.declaration && <p className="mt-1.5 text-sm font-semibold text-danger">{errors.declaration}</p>}
        </div>
      )}

      {failure && (
        <Notice variant="line" tone="danger" title="Kaydedilemedi">
          {failure.message}
        </Notice>
      )}
      <div className="flex items-center gap-5">
        <Button type="submit" variant="primary" size="lg" loading={save.isPending || uploading}>
          {editing ? 'Kaydet' : publisher?.type === 'CLUB' && announcement && publisher.needsApproval ? 'Onaya gönder' : announcement ? 'Yayımla' : 'Paylaş'}
        </Button>
        <Link
          to={existing ? `/posts/${existing.id}` : '/posts'}
          className="text-md font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
        >
          Vazgeç
        </Link>
      </div>
    </form>
  )
}

/** Duyuru kimin adına: birden çok seçenek varsa seçici, tek seçenekte düz metin; kampüste birim adı. */
function PublisherFields({
  publishers,
  publisher,
  publisherKey,
  onPublisher,
  publisherName,
  onPublisherName,
  errors,
}: {
  publishers: Publisher[]
  publisher: Publisher | undefined
  publisherKey: string
  onPublisher: (key: string) => void
  publisherName: string
  onPublisherName: (name: string) => void
  errors: { publisher?: string; publisherName?: string }
}) {
  const clubs = publishers.filter((p) => p.type === 'CLUB')
  const courses = publishers.filter((p) => p.type === 'COURSE')
  const campus = publishers.find((p) => p.type === 'CAMPUS')
  const hint =
    publisher?.type === 'CLUB'
      ? `${publisher.needsApproval ? 'Kulüp başkanı onaylayınca yayımlanır. ' : ''}Akışta herkes görür; yalnız üyelere duyuru için kulüp sayfasını kullanın.`
      : publisher?.type === 'COURSE'
        ? 'Akışta herkes görür; yalnız derse kayıtlı öğrenciler için ders sayfasındaki duyuruları kullanın.'
        : 'Akışta herkes görür.'

  return (
    <>
      {publishers.length === 1 ? (
        publisher?.type !== 'CAMPUS' && (
          <div>
            <p className="text-md">
              Yayımlayan: <span className="font-semibold">{publisher?.label}</span>
            </p>
            <p className="mt-1 text-sm text-ink-3">{hint}</p>
            {errors.publisher && <p className="mt-1.5 text-sm font-semibold text-danger">{errors.publisher}</p>}
          </div>
        )
      ) : (
        <Field label="Kimin adına?" required hint={hint} error={errors.publisher}>
          <Select value={publisherKey} onChange={(e) => onPublisher(e.target.value)}>
            <option value="">Seçin</option>
            {clubs.length > 0 && (
              <optgroup label="Kulüpler">
                {clubs.map((p) => (
                  <option key={p.key} value={p.key}>
                    {p.label}
                  </option>
                ))}
              </optgroup>
            )}
            {courses.length > 0 && (
              <optgroup label="Dersler">
                {courses.map((p) => (
                  <option key={p.key} value={p.key}>
                    {p.label}
                  </option>
                ))}
              </optgroup>
            )}
            {campus && <option value={campus.key}>{campus.label}</option>}
          </Select>
        </Field>
      )}
      {publisher?.type === 'CAMPUS' && (
        <Field label="Yayımlayan birim" required hint="Akışta bu ad görünür. Ör. Öğrenci İşleri Daire Başkanlığı." error={errors.publisherName}>
          <Input maxLength={255} value={publisherName} onChange={(e) => onPublisherName(e.target.value)} />
        </Field>
      )}
    </>
  )
}

/** Düzenlemede ek: değiştir ya da kaldır (tek ek; yenisi eskisinin yerine geçer). */
function AttachmentEditor({ post }: { post: Post }) {
  const attach = useAttachment(post.id)
  const [file, setFile] = useState<File | null>(null)
  const fail = (e: unknown) => toast.error(toApiError(e).message)
  return (
    <div className="flex flex-col gap-3">
      {post.attachmentName && (
        <p className="flex flex-wrap items-baseline gap-x-4 text-md">
          <span>
            Mevcut ek: <span className="font-semibold">{post.attachmentName}</span>
          </span>
          <button
            type="button"
            onClick={() => attach.mutate(null, { onSuccess: () => toast.success('Ek kaldırıldı'), onError: fail })}
            className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline"
          >
            Eki kaldır
          </button>
        </p>
      )}
      <FileField
        label={post.attachmentName ? 'Yeni ek' : 'Ek dosya'}
        hint="En fazla 20 MB."
        maxBytes={ATTACHMENT_MAX}
        value={file}
        onChange={setFile}
      />
      {file && (
        <div>
          <Button
            size="sm"
            loading={attach.isPending}
            onClick={() =>
              attach.mutate(file, {
                onSuccess: () => {
                  toast.success('Ek yüklendi')
                  setFile(null)
                },
                onError: fail,
              })
            }
          >
            Eki yükle
          </Button>
        </div>
      )}
    </div>
  )
}
