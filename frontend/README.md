# EduConnect Frontend

EduConnect'in web arayüzü: dersler, ödevler, kulüpler, etkinlikler, topluluk akışı, yönetim ekranları ve yapay zekâ asistanı.

## Teknoloji

React 19, TypeScript, Vite, Tailwind CSS 4, TanStack Query, React Router 7, Radix UI, Vitest ve Testing Library.

## Çalıştırma

Node.js 22 ya da üstü gerekir.

```bash
npm ci
npm run dev
```

Geliştirme sunucusu `http://localhost:5173` adresinde açılır ve `/api` isteklerini `http://localhost:8080` adresindeki API gateway'e yönlendirir. Başka bir adres için `VITE_API_PROXY` ortam değişkenini verin. Backend'i ayağa kaldırmak için depo kökündeki README'ye bakın.

Backend olmadan ekranları gezmek için geliştirme sunucusunda `/onizleme` adresini açın; öğrenci olarak açılır, `?rol=` ile `akademisyen`, `cift`, `gorevli` ya da `yonetici` seçilir. Bu kip bellekteki örnek veriyle çalışır ve yalnız geliştirmede vardır.

## Komutlar

| Komut | İş |
|---|---|
| `npm run dev` | Geliştirme sunucusu |
| `npm run build` | Tip kontrolü ve üretim derlemesi (`dist/`) |
| `npm run typecheck` | Yalnız tip kontrolü |
| `npm run lint` | ESLint |
| `npm test` | Vitest (tek sefer için `npx vitest run`) |

## Klasörler

| Yol | İçerik |
|---|---|
| `src/app` | Yönlendirici ve uygulama kabuğu |
| `src/features` | Alan başına ekranlar ve API kancaları (dersler, kulüpler, etkinlikler, gönderiler, asistan, yönetim) |
| `src/components` | Ortak arayüz bileşenleri ve yerleşim |
| `src/lib` | API istemcisi, oturum, tarih ve biçim yardımcıları |
| `src/design` | Renk, hat ve hareket belirteçleri |
| `src/dev` | Önizleme kipi ve örnek veriler |
| `design` | Tasarım envanteri ve yön notları |

## Yayın

`npm run build` çıktısı olan `dist/` klasörünü Caddy sunar; `docker-compose.yml` içindeki `FRONTEND_DIST` değişkeni bu klasörü gösterir. İçerik güvenlik politikası Caddy'de tanımlıdır: sayfa satır içi betik çalıştırmaz, bu yüzden tema betiği `public/theme.js` dosyasındadır.
