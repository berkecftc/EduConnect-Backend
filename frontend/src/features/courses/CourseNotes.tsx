import { Link } from 'react-router-dom'
import { useFeed } from '@/features/posts/api'
import { HideButton } from '@/features/posts/ContentActions'
import { PostRow } from '@/features/posts/PostRow'
import { Button } from '@/components/ui/Button'
import { EmptyState, QueryBoundary } from '@/components/ui/States'

/**
 * Ders notları (F-71): öğrencilerin bu derse bağlayıp paylaştığı notlar. Öğrenci yenisini paylaşır; koordinatör ve hoca
 * uygun bulmadığı notu gerekçeyle gizler (F-69), asistan yalnız görür.
 */
export function CourseNotes({ courseId, canHide = false, canShare = false }: { courseId: string; canHide?: boolean; canShare?: boolean }) {
  const feed = useFeed({ list: 'all', category: 'DERS_NOTU', courseId })
  const share = (
    <Button asChild variant="primary">
      <Link to={`/posts/new?tur=DERS_NOTU&ders=${courseId}`}>Ders notu paylaş</Link>
    </Button>
  )

  return (
    <QueryBoundary query={feed} what="Ders notları" skeletonRows={3}>
      {(data) => {
        const posts = data.pages.flatMap((p) => p.content)
        if (posts.length === 0) {
          return (
            <EmptyState title="Henüz ders notu yok" action={canShare ? share : undefined}>
              {canShare ? 'Kendi hazırladığınız notu paylaşan ilk kişi siz olabilirsiniz.' : 'Öğrenciler bu derse not paylaştığında burada görünür.'}
            </EmptyState>
          )
        }
        return (
          <>
            {canShare && <div className="mb-6">{share}</div>}
            <ul className="border-t-2 border-ink">
              {posts.map((p) => (
                <PostRow key={p.id} post={p} hideCourse actions={canHide ? <HideButton target={{ kind: 'post', id: p.id, postId: p.id }} /> : undefined} />
              ))}
            </ul>
            {feed.hasNextPage && (
              <div className="mt-8">
                <Button loading={feed.isFetchingNextPage} onClick={() => void feed.fetchNextPage()}>
                  Daha eski notlar
                </Button>
              </div>
            )}
          </>
        )
      }}
    </QueryBoundary>
  )
}
