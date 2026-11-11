/** Unvan kodları (AcademicTitle) → okunur biçim; yanıtta etiket boşsa yedek. */
const TITLE_LABEL: Record<string, string> = {
  PROFESSOR: 'Prof. Dr.',
  ASSOCIATE_PROFESSOR: 'Doç. Dr.',
  ASSISTANT_PROFESSOR: 'Dr. Öğr. Üyesi',
  LECTURER_PHD: 'Öğr. Gör. Dr.',
  LECTURER: 'Öğr. Gör.',
  RESEARCH_ASSISTANT_PHD: 'Arş. Gör. Dr.',
  RESEARCH_ASSISTANT: 'Arş. Gör.',
}

/** Gösterilecek unvan: önce etiket (`title`), yoksa koddan (`academicTitle`) türetilen; ikisi de yoksa boş (F-84). */
export function titleLabel(title: string | null | undefined, academicTitle?: string | null): string {
  return title?.trim() || (academicTitle ? (TITLE_LABEL[academicTitle] ?? '') : '')
}

/** "Doç. Dr. Ayşe Yılmaz": unvanı adın önüne ekler; ad zaten unvanla başlıyorsa tekrar etmez. */
export function withTitle(name: string | null | undefined, title: string | null | undefined, academicTitle?: string | null): string {
  const n = (name ?? '').trim()
  const t = titleLabel(title, academicTitle)
  if (!t || !n) return n
  return n.toLocaleLowerCase('tr-TR').startsWith(t.toLocaleLowerCase('tr-TR')) ? n : `${t} ${n}`
}
