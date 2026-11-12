import { useEffect } from 'react'

/** Her ekranın kendi sekme başlığı olur (WCAG 2.4.2). */
export function usePageTitle(title: string) {
  useEffect(() => {
    document.title = title ? `${title} – EduConnect` : 'EduConnect'
  }, [title])
}
