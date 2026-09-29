# EduConnect API Konvansiyonları

Backend'e eklenen her uç ve frontend'in API kullanımı bu kurallara uyar. Kurallarla çelişen mevcut uçlar en altta listelenmiştir; frontend yenilemesinde birlikte düzeltilecekler.

## 1. Adres ve sürüm

- Tüm istekler gateway'den geçer: `/api/<kaynak>/…`. Frontend göreli `/api` kullanır (Caddy aynı alan adında `/api/*`'ı gateway'e yönlendirir).
- Yolda sürüm yok. Geriye uyumsuz bir değişiklik gerekirse o kaynak için `/api/v2/…` açılır, eski yol bir geçiş süresi boyunca birlikte çalışır, sonra `410 Gone` olur.
- `/api/<servis>/internal/**` yalnız servisler arası içindir; gateway dışarıdan gelen isteği `404` ile keser ve bu uçlar API dokümanında görünmez.

## 2. Kimlik ve yetki

- `Authorization: Bearer <access token>` (RS256 JWT). Süresi dolunca `POST /api/auth/refresh` ile yenilenir; refresh token her kullanımda döner (rotasyon).
- Kimlik başlıklarını (`X-Authenticated-*`) yalnız gateway üretir; istemcinin gönderdikleri silinir.
- `401` = kimlik yok/geçersiz (yeniden giriş veya refresh), `403` = kimlik var ama yetki yok. Frontend yalnız `401`'de oturumu kapatır.
- Herkese açık uçlar (token gerekmez): giriş, kayıt ve başvuru, refresh/logout, şifre sıfırlama, e-posta doğrulama, kulüp ve etkinlik listesi/detayı, rozet görseli.

## 3. Yol adları

- Kaynaklar çoğul, kebab-case isim: `/clubs`, `/membership-requests`, `/role-change-requests`.
- İç içe en fazla iki seviye: `/clubs/{clubId}/membership-requests/{requestId}`.
- ID'ler UUID (metin). ID tahmin edilemez olsa da yetki kontrolünün yerini tutmaz; her uç sahipliği ayrıca doğrular.
- Durum değiştiren eylemler alt kaynak olarak ve **`POST`** ile: `POST /…/{id}/approve`, `POST /…/{id}/reject`, `POST /…/{id}/withdraw`.
- "Benim" listeleri `me` altında: `GET /api/<kaynak>/me/…` (ör. `GET /api/posts/me`).
- Arama: `GET /api/<kaynak>/search?q=…`.

## 4. Metotlar ve durum kodları

| Durum | Kod |
|---|---|
| Okuma | `200` |
| Oluşturma | `201` (+ gövdede oluşan kayıt) |
| Güncelleme / eylem | `200` (güncel kaydı döner) |
| Silme | `204` (gövde yok) |
| Doğrulama hatası, bozuk JSON, geçersiz parametre | `400` |
| Kimlik yok | `401` |
| Yetki yok | `403` |
| Kayıt / uç yok | `404` |
| Metot desteklenmiyor | `405` |
| Çakışma (aynı kayıt, eşzamanlı güncelleme, durum uygun değil) | `409` |
| Kapatılmış uç | `410` (`errorCode: ENDPOINT_GONE`, mesajda yerine geçen uç) |
| Dosya çok büyük | `413` |
| Çok fazla istek | `429` (`Retry-After` başlığı saniye) |
| Beklenmeyen hata | `500` (iç ayrıntı yok) |
| Bağlı servis kapalı | `503` |

`PUT` tam güncelleme, `PATCH` kısmi güncellemedir. Mevcut profil güncellemesi `PUT` ile kısmi çalışır (boş alanlar değişmez).

## 5. Hata biçimi

Tüm hatalar `application/problem+json` (RFC 9457):

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Girilen bilgiler geçersiz. title: Başlık boş olamaz",
  "instance": "/api/posts",
  "errorCode": "VALIDATION_FAILED",
  "message": "Girilen bilgiler geçersiz. title: Başlık boş olamaz",
  "timestamp": "2026-09-28T10:40:25.582Z",
  "errors": [ { "field": "title", "message": "Başlık boş olamaz" } ]
}
```

- Kullanıcıya **`message`** gösterilir (Türkçe, `detail` ile aynı).
- Kod mantığı **`errorCode`** ile kurulur; mesaj metnine bakılmaz. Genel kodlar: `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `BAD_REQUEST`, `UNAUTHENTICATED`, `BAD_CREDENTIALS`, `ACCESS_DENIED`, `NOT_FOUND`, `CONFLICT`, `CONCURRENT_UPDATE`, `DATA_CONFLICT`, `ENDPOINT_GONE`, `RATE_LIMITED`, `SERVICE_UNAVAILABLE`, `INTERNAL_ERROR`; alana özgü olanlar `<KAYNAK>_NOT_FOUND` gibi (`COURSE_NOT_FOUND`, `CLUB_NAME_TAKEN`, `INVALID_TICKET` …).
- `errors` yalnız doğrulama hatalarında var; form alanlarının altına `field` ile yazılır.
- Backend'de yeni hata için `common-web`'deki `NotFoundException`, `BadRequestException`, `ConflictException`, `ForbiddenException` (veya `ApiException(status, errorCode, mesaj)`) fırlatılır; controller'da `try/catch` ile hata gövdesi kurulmaz.

## 6. İstek gövdeleri ve doğrulama

- Gövde DTO'ları `record` ya da düz sınıf; her `@RequestBody` ve JSON `@RequestPart` `@Valid` ile.
- Kısıtlar veritabanı kolonlarıyla aynı (`NOT NULL` → zorunlu, `varchar(255)` → en fazla 255) ve mevcut iş kurallarını yansıtır; mesajlar Türkçe.
- Dosya + veri birlikte: `multipart/form-data`, JSON parçası adlandırılmış (`course`, `assignment`, `data`, `request`) ve `Content-Type: application/json`.
- Kısmi güncellemede gönderilmeyen (`null`) alan değişmez.

## 7. Yanıt gövdeleri

- JPA entity asla dönmez; yanıt DTO'su (`XxxResponse`, `XxxDTO`) döner. `@Version`, parola, başka kullanıcının gereksiz kimlik/iletişim bilgisi yanıtta yer almaz (KVKK).
- Başarılı yanıtta zarf yok; gövde doğrudan kaynak veya listedir.
- Alan adları camelCase.
- Zaman:
  - kayıt anları (`createdAt`, `updatedAt`, `processedAt` …) UTC ISO-8601 (`2026-09-28T10:40:25.582Z`),
  - planlanan yerel zamanlar (`eventTime`, `dueDate`) bölgesiz ISO-8601 (`2026-10-05T18:30:00`) ve Türkiye saatidir.
- Görseller ve dosyalar URL olarak döner; özel dosyalar kısa ömürlü imzalı URL'dir, saklanmamalıdır.

## 8. Sayfalama

- `?page=0&size=20` (sıfırdan başlar, `size` en fazla 100; fazlası 100'e indirilir).
- Yanıt:

```json
{ "content": [ … ], "number": 0, "size": 20, "totalElements": 57, "totalPages": 3, "first": true, "last": false }
```

- Yeni liste uçları sayfalı olur. Eski sayfasız listeler (dizi döner) geriye uyumluluk için duruyor; bazıları `page` parametresiyle sayfalı sürüme geçer (ör. `GET /api/events?page=0`).

## 9. Dosyalar

- Yükleme `multipart/form-data`; tür ve boyut sunucuda doğrulanır (görsel: JPEG/PNG/GIF/WEBP).
- İndirme kaynağın ID'siyle: ör. `GET /api/courses/{courseId}/file`. İstemciden dosya yolu/URL alınmaz.
- İndirme yanıtı `Content-Disposition: attachment` ve `X-Content-Type-Options: nosniff` içerir.

## 10. Gözlemlenebilirlik ve limitler

- Her yanıtta `X-Trace-Id` başlığı vardır; hata bildirirken bu değer eklenir (loglarda ve Grafana/Tempo'da aranır).
- Genel limit: kullanıcı başına dakikada 300, anonim IP başına 600 istek; girişte IP başına dakikada 10. Aşımda `429` ve `Retry-After`.

## 11. API dokümanı

- Swagger arayüzü: `http://localhost:8080/swagger-ui.html` (üstteki listeden servis seçilir). Tüm servislerin OpenAPI 3.1 tanımı `http://localhost:8080/api-docs/<servis>`.
- Bu adresler Caddy'den dışarı açılmaz; sunucuda SSH tüneliyle (`ssh -L 8080:127.0.0.1:8080 sunucu`) kullanılır.
- "Try it out" için sağ üstteki **Authorize** ile access token girilir.

## 12. Bilinen sapmalar (frontend yenilemesinde düzeltilecek)

| Kural | Mevcut uç(lar) | Hedef |
|---|---|---|
| Eylemler `POST` | `PUT` ile: `/api/academician/club-creation-requests/{id}/approve\|reject`, `/api/academician/role-change-requests/{id}/approve\|reject`, `/api/clubs/{clubId}/membership-requests/{id}/approve\|reject`, `/api/courses/applications/{id}/approve\|reject` | `POST` |
| Tek yönetim öneki | `/api/auth/admin/…` (hesaplar), `/api/admin/clubs/…` (kulüpler, bayrakla kapalı), `/api/events/admin/all` | `/api/admin/<kaynak>` |
| Eylem adları alt kaynak | `/api/auth/admin/approve-academician/{id}`, `approve-student/{id}`, `reject-…` | `/api/admin/academician-requests/{id}/approve` vb. |
| "Benim" listeleri `me` altında | `/api/clubs/my-memberships`, `my-managed-clubs`, `my-membership-requests`; `/api/courses/my-courses`, `my-applications`, `instructor/me/courses`; `/api/events/my-registrations`, `my-participation-requests`, `manage/my-events`; `/api/assignments/my-assignments` | `/api/<kaynak>/me/…` |
| Rol adına göre yol yok | `/api/academician/…` (kulüp danışman işlemleri, club-service) | `/api/clubs/…` altında kaynak yolu |
| Kapatılmış uçlar kaldırılır | `410` dönen: kulüp yetkilisi başvurusu, `/api/events/{id}/register`, `/api/courses/{id}/enroll-student`, `/api/events/manage/pending\|approve\|reject`, profil `by-student-number`, admin promote/revoke | Frontend geçince silinir |
| Dosya indirme ID ile | `GET /api/assignments/files/download?url=…` (yetki kontrollü ama URL alıyor), `GET /api/courses/files/download?url=…` (yalnız derse bağlı dosya; yerine `/api/courses/{courseId}/file`) | `GET /api/assignments/{id}/file`, `GET /api/assignments/submissions/{id}/file` |
| Tek yol | `/api/llm/**` ve `/api/ai/**` aynı uçlar | `/api/ai/**` |
| Liste sayfalı | kulüp üyeleri, başvurular, kayıtlar gibi dizi dönen listeler | `PageResponse` |

Bu değişiklikler frontend'i bozacağı için frontend yenilemesiyle aynı anda yapılır; o zamana kadar eski yollar çalışır.
