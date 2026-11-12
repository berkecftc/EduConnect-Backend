# Mevcut frontend envanteri (04.10.2026)

Yenileme çalışma sırasının 1. adımı. Kaynak: `EduConnect-Backend/docs/review/frontend-notlari.md` (F-01…F-82) ve `API-KONVANSIYONLARI.md`.

## Genel durum

- Düz JS/JSX; TypeScript, test, test betiği yok. ESLint flat config var.
- Tailwind 4 iki kez kayıtlı (Vite eklentisi + PostCSS). `@theme` token'ı yok; `index.css` varsayılan slate/blue paletini kullanıyor.
- Font tanımı `Inter` ama hiç yüklenmiyor; auth sayfaları ayrıca Google Fonts `Outfit` içe aktarıyor.
- Dört sayfa CSS'i hiç içe aktarılmıyor (`StudentDashboard.css`, `InstructorDashboard.css`, `ClubOfficialDashboard.css`, `admin/AdminDashboard.css`), `App.css` ve `replace_theme.cjs` artık.
- Ortak bileşen yok denecek kadar az: `ProtectedRoute`, `AdminButton`, `AIChatAssistant`, `UserProfileTab`. Her panel kendi kenar çubuğunu, sekme mantığını, tema düğmesini ve modalını yazıyor.
- Dev proxy yok; 404 rotası yok; yetkisiz kullanıcı `/dashboard` yer tutucusuna düşüyor.
- Yaklaşık 41 `alert(` ve 9 `window.confirm`; toast ve ortak hata yardımcısı yok.

## Sayfalar

| Rota | Bileşen | Satır | Not |
|---|---|---|---|
| `/`, `/login` | `pages/auth/Login` | 194 | F-12: nesne gövdede genel "Giriş başarısız" |
| `/register` | `pages/auth/Register` | 411 | F-52, F-53, F-54 (serbest bölüm/unvan metni) |
| `/forgot-password`, `/reset-password` | auth | 127 / 196 | F-57 (hesap kurulumu başlığı) |
| `/admin/dashboard` | `AdminDashboard` | 329 | F-41, F-53, F-55…F-57 eksik |
| `/student/dashboard` | `StudentDashboard` | 815 | Dersler, ödevler, kulüpler, etkinlikler, profil, asistan tek dosyada |
| `/instructor/dashboard` | `InstructorDashboard` | 822 | Ders yönetimi, danışman onayları |
| `/clubofficial/dashboard` | `ClubOfficialDashboard` | 1025 | 10 sekme; F-22 etkinlik formu bağlı değil |
| `/clubs` | `ClubList` | 207 | F-28, F-33 |
| `/posts…` | PostList/Detail/Create/Edit, CommentSection | 209–346 | F-67…F-72 |
| `/leaderboard` | `Leaderboard` | 167 | F-73 (rol kısıtı yok) |

Eksik rotalar (notlarda istenen): `/verify-email`, `/email-change/confirm`, `/notifications`, `/notifications/unsubscribe`, `/courses/{id}`, `/courses/{id}/assignments/{aid}`, `/clubs/{id}`, `/events/{id}`, `/me/tickets`, `/posts/{id}` (var), `/profile`, görevli paneli (F-57…F-59, F-64, F-68).

## Kimlik ve altyapı (F-01…F-05)

- `authSlice`: `{user, role, token, userId}`; refresh token yok (F-02), süre kontrolü yok.
- Bileşenler `state.auth`'tan `email`, `studentNumber`, `department` okuyor; slice bunları tutmuyor, hep `undefined`.
- `ProtectedRoute`: `role` null ise `role.includes` çöker.
- Login yönlendirmesi yanıttaki `roles[0]`'a göre; `primaryRole` / `pendingRequests` kullanılmıyor (F-23).
- axios: `baseURL` sabit `http://localhost:8080/api` (F-01). 401'de localStorage temizleyip `window.location` ile yönlendiriyor, Redux'a haber vermiyor.
- `localhost:8080` sabiti: `api/axiosConfig.js:5`, `api/aiService.js:3`, `api/eventService.js:58`, `components/profile/UserProfileTab.jsx:14-15,162`.
- `assignmentService` istemciden `X-Authenticated-User-Id` başlığı gönderiyor (gateway siliyor; kaldırılmalı).
- Asistan SSE: `fetch` + `getReader`, AbortController yok (F-04).

## Hata gösterimi (F-10…F-14)

- `data.message || data` + `typeof msg === 'string'` kalıbı panellerde ve auth sayfalarında; `errorCode` ve `errors[]` hiç kullanılmıyor.

## Ölü / bağlanmamış çağrılar (F-21, F-22, F-25, F-82)

- Ölü: `courseService.enrollStudentToCourse`, `adminService.getEventRequests` / `approveEvent` / `rejectEvent`, `downloadAssignmentFile` ve `downloadCourseFile` (`files/download?url=`).
- Hiç çağrılmayan: `createEvent` (ham fetch, sabit URL), `verifyQrCode`, `createRoleChangeRequest`, `removeMemberRole`, `getAllClubEvents`, `getPendingParticipationRequests`, `getAllParticipationRequests`, `getLikeCount`, `getLikeStatus`, `getSaveStatus` (`/save/` vs `/bookmark` tutarsız), `changeClubPresident`, `updateClubLogo`, `getAdvisorEvents`, `rejectRoleChangeRequest`, `getRoleChangeRequestCount`.
- `rejectApplication` `{reason}` gönderiyor (F-20, doğrusu `rejectionReason`); onay/ret uçları `PUT` (§12 sapması).

## Sonuç

Mevcut kod tasarım ve mimari olarak taşınmaya değmiyor: sayfalar 800–1000 satırlık tek dosyalar, ortak katman yok, sözleşmenin büyük kısmı (F-28…F-82) hiç yok. Öneri: aynı repoda yeni bir `src/` iskeleti (özellik bazlı klasörler, tasarım sistemi, API katmanı) kurup ekranları sözleşmeye göre sıfırdan yazmak; eski sayfalar referans olarak okunur, taşınmaz.
