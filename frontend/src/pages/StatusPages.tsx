import { useEffect, useState } from 'react'
import { canReloadForNewVersion, isStaleChunkError, reloadForNewVersion } from '@/lib/staleVersion'
import { usePageTitle } from '@/lib/usePageTitle'
import { Link, isRouteErrorResponse, useLocation, useRouteError } from 'react-router-dom'
import { Button } from '@/components/ui/Button'

function StatusLayout({ title, body, children }: { title: string; body: string; children?: React.ReactNode }) {
  usePageTitle(title)
  return (
    <section className="max-w-[40rem] py-10">
      <h1 className="text-3xl">{title}</h1>
      <p className="mt-3 text-ink-2">{body}</p>
      <div className="mt-8 flex flex-wrap gap-3">{children}</div>
    </section>
  )
}

export function NotFoundPage() {
  const { pathname } = useLocation()
  return (
    <StatusLayout
      title="Bu sayfa bulunamadı"
      body={`${pathname} adresinde bir sayfa yok. Bağlantı eski olabilir ya da kayıt kaldırılmış olabilir.`}
    >
      <Button asChild variant="primary">
        <Link to="/">Bugün ekranına dön</Link>
      </Button>
    </StatusLayout>
  )
}

export function NotAllowedPage() {
  return (
    <StatusLayout
      title="Bu bölüme erişiminiz yok"
      body="Hesabınızın rolü bu sayfayı açmaya yetmiyor. Yetki gerektiğini düşünüyorsanız birim yöneticinize başvurun."
    >
      <Button asChild variant="primary">
        <Link to="/">Bugün ekranına dön</Link>
      </Button>
    </StatusLayout>
  )
}

/**
 * Rota hatası: bir sayfa çizilirken ya da paketi yüklenirken oluşan beklenmeyen hata. Kabuğun içinde olursa menü çalışır
 * kalır; kabuğun dışında (`standalone`) tek başına açılır. Eski sürümün paketi yüklenemediyse sayfa bir kez kendiliğinden
 * yenilenir. Ana sayfa bağlantısı tam yükleme yapar, çünkü o sayfanın paketi de eski olabilir.
 */
export function RouteErrorPage({ standalone = false }: { standalone?: boolean }) {
  const error = useRouteError()
  const stale = isStaleChunkError(error)
  const [reloading] = useState(() => stale && canReloadForNewVersion())

  useEffect(() => {
    if (reloading) reloadForNewVersion()
  }, [reloading])

  if (isRouteErrorResponse(error) && error.status === 404) return <NotFoundPage />

  const page = reloading ? (
    <StatusLayout title="Yeni sürüm yükleniyor" body="Uygulama güncellendi; sayfa birazdan yenilenecek." />
  ) : (
    <StatusLayout
      title={stale ? 'Uygulama güncellendi' : 'Bu sayfa açılamadı'}
      body={
        stale
          ? 'Sayfanın bu sürümü artık yüklenemiyor. Devam etmek için sayfayı yenileyin.'
          : 'Beklenmeyen bir hata oluştu. Sayfayı yenilemek çoğu zaman yeterli olur; sorun sürerse biraz sonra yeniden deneyin.'
      }
    >
      <Button variant="primary" onClick={() => window.location.reload()}>
        Sayfayı yenile
      </Button>
      <Button asChild>
        <a href="/">Bugün ekranına dön</a>
      </Button>
      {import.meta.env.DEV && error instanceof Error && (
        <pre className="mt-4 w-full overflow-x-auto border-l-[3px] border-danger py-1 pl-4 text-sm text-ink-2">{error.message}</pre>
      )}
    </StatusLayout>
  )

  return standalone ? (
    <main role="alert" className="mx-auto flex min-h-dvh max-w-[72rem] items-center px-4 sm:px-8">
      {page}
    </main>
  ) : (
    <div role="alert">{page}</div>
  )
}

/** Yenileme sürerken henüz yazılmamış rotalar için. */
export function NotBuiltPage() {
  const { pathname } = useLocation()
  return (
    <StatusLayout
      title="Bu ekran yeniden yazılıyor"
      body={`${pathname} yeni tasarıma taşınma sırasında. Hazır olduğunda burada açılacak.`}
    >
      <Button asChild variant="secondary">
        <Link to="/">Bugün ekranına dön</Link>
      </Button>
    </StatusLayout>
  )
}
