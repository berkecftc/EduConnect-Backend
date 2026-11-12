import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { usePageTitle } from '@/lib/usePageTitle'
import { useClub } from '@/features/clubs/api'
import { can, useClubAccess } from '@/features/clubs/manage'
import { EmptyState, PageHeader, Skeleton } from '@/components/ui/States'
import { EventForm } from './EventForm'
import { EMPTY_DRAFT, useCreateEvent } from './manage'

/** Kulüp adına etkinlik oluştur (CREATE_EVENT ya da PREPARE_EVENT). Yayından önce başkan ve danışman onayından geçer. */
export function EventCreatePage() {
  usePageTitle('Etkinlik oluştur')
  const { clubId = '' } = useParams()
  const navigate = useNavigate()
  const club = useClub(clubId)
  const access = useClubAccess(clubId)
  const create = useCreateEvent()
  const [failure, setFailure] = useState<ApiError | null>(null)

  if (club.isPending || access.isPending) return <Skeleton rows={5} />
  if (!can(access.data, 'CREATE_EVENT', 'PREPARE_EVENT') || !club.data) {
    return (
      <div className="mx-auto max-w-[56rem]">
        <EmptyState title="Bu kulüp adına etkinlik oluşturamazsınız">
          Etkinlik oluşturma kulübün yönetim kuruluna ve etkinlik koordinatörüne açıktır.{' '}
          <Link to={`/clubs/${clubId}`} className="font-semibold underline-offset-4 hover:underline">
            Kulübe dön
          </Link>
        </EmptyState>
      </div>
    )
  }
  const name = club.data.name

  return (
    <div className="mx-auto max-w-[72rem]">
      <p className="flex items-center gap-3 text-md">
        <span aria-hidden className="h-5 w-[4px] bg-kulup" />
        <Link to={`/clubs/${clubId}?sekme=yonetim`} className="font-semibold underline-offset-4 hover:underline">
          {name}
        </Link>
      </p>
      <PageHeader
        title="Etkinlik oluştur"
        meta={
          access.data?.actingPresident
            ? 'Başkan olarak oluşturduğunuz etkinlik doğrudan danışman onayına gider; onaylanınca yayımlanır.'
            : 'Etkinlik önce başkanın, ardından danışmanın onayına gider; onaylanınca yayımlanır.'
        }
      />
      <div className="mt-8">
        <EventForm
          initial={EMPTY_DRAFT}
          submitLabel="Onaya gönder"
          busy={create.isPending}
          failure={failure}
          withPoster
          onSubmit={(draft, poster) => {
            setFailure(null)
            create.mutate(
              { draft, clubName: name, poster },
              {
                onSuccess: (e) => {
                  toast.success(e.status === 'PENDING' ? 'Etkinlik danışman onayına gönderildi' : 'Etkinlik başkan onayına gönderildi')
                  navigate(`/events/${e.id}/yonetim`)
                },
                onError: (err) => setFailure(toApiError(err)),
              },
            )
          }}
        />
      </div>
    </div>
  )
}
