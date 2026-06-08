# 🎓 EduConnect Backend — Mikroservis Tabanlı Eğitim Platformu

<div align="center">

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-007396?logo=openjdk&logoColor=white)](#)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-6DB33F?logo=spring-boot&logoColor=white)](#)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.3-6DB33F)](#)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)](#)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14-336791?logo=postgresql&logoColor=white)](#)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3-FF6600?logo=rabbitmq&logoColor=white)](#)
[![License](https://img.shields.io/badge/License-MIT-green)](#)

**Yapay Zekâ Destekli, Yerel LLM Integre, Bulut-Yerel Mikroservis Mimarisi**

[📖 Proje Raporu](#proje-raporu) • [🚀 Hızlı Başlangıç](#hızlı-başlangıç) • [🏗️ Mimari](#mimari) • [📚 API Dokümantasyonu](#api-dokümantasyonu) • [🤝 Katkıda Bulunma](#katkıda-bulunma)

</div>

---

## 📋 İçindekiler

1. [Proje Özeti](#proje-özeti)
2. [Özellikler](#özellikler)
3. [Teknoloji Yığını](#teknoloji-yığını)
4. [Sistem Mimarisi](#sistem-mimarisi)
5. [Hızlı Başlangıç](#hızlı-başlangıç)
6. [Proje Yapısı](#proje-yapısı)
7. [Mikroservisler Detaylı Açıklama](#mikroservisler-detaylı-açıklama)
8. [API Dokümantasyonu](#api-dokümantasyonu)
9. [Servisler Arası İletişim](#servisler-arası-iletişim)
10. [Veritabanı Tasarımı](#veritabanı-tasarımı)
11. [Güvenlik Mimarisi](#güvenlik-mimarisi)
12. [Yapay Zekâ Entegrasyonu](#yapay-zekâ-entegrasyonu)
13. [Geliştirilme Rehberi](#geliştirilme-rehberi)
14. [Performans Optimizasyonu](#performans-optimizasyonu)
15. [Sorun Giderme](#sorun-giderme)
16. [Proje Raporu](#proje-raporu)

---

## 📌 Proje Özeti

**EduConnect Backend**, üniversiteler ve eğitim kurumları için tasarlanmış, **modern bulut-yerel (cloud-native) mimarisine** sahip, kapsamlı bir eğitim platformu arka ucudur.

### Temel Amaçlar
- ✅ **Ölçeklenebilirlik**: Mikroservis mimarisi ile bağımsız ölçeklendirme
- ✅ **Gizlilik**: Yerel olarak çalışan LLM ile KVKK/GDPR uyumu
- ✅ **Modülerlik**: Yeni özellikleri kolaylıkla ekleme
- ✅ **Performs**: Asenkron haberleşme, Cache, Vector Database
- ✅ **Oyunlaştırma**: Öğrenci motivasyonunu artıracak puan/rozet sistemi
- ✅ **Yapay Zekâ**: RAG tabanlı kulüp önerisi, içerik moderasyonu, akademisyen copilot

### Proje İstatistikleri
- **Mikro Servis Sayısı**: 13
- **Java Dosya Sayısı**: 374
- **Toplam Kod Satırı**: 25.000+
- **REST Endpoint**: 174
- **Feign İstemcisi**: 13
- **RabbitMQ Exchange**: 4
- **Veritabanı Şeması**: 8

---

## 🌟 Özellikler

### 🔐 Kimlik Doğrulama & Yetkilendirme
- JWT tabanlı oturumsuz kimlik doğrulama (JJWT 0.12.6)
- Çok aşamalı hesap onay süreci (öğrenci/akademisyen/kulüp görevlisi)
- Belgeli başvuru desteği (MinIO ile dosya yönetimi)
- Access/Refresh token akışı
- Şifre yönetimi (değiştir/unut/sıfırla)

### 📝 Kurs Yönetimi
- Akademisyen tarafından kurs oluşturma ve yönetme
- İki farklı kayıt modu: Doğrudan kayıt + Başvuru tabanlı (FCFS)
- Kurs materyalleri ve duyuruları
- Dosya depolama (MinIO)

### 📌 Ödev & Notlandırma
- Ödev oluşturma ve teslim yönetimi
- Akademisyen tarafından notlandırma
- Dosya yökleme/indirme (MinIO entegrasyonu)
- Kurs-ödev ilişkisi doğrulaması

### 🎉 Etkinlik Yönetimi
- Kulüp temelli etkinlik organizasyonu
- Danışman akademisyen onayı ve yönetimi
- **QR Kod Tabanlı Bilet**: Google ZXing ile benzersiz QR kod üretimi
- Kapasite yönetimi ve bekleme listesi
- Base64 encoded QR kod döndürme

### 👥 Kulüp Yönetimi
- Kulüp profili ve yönetim yapısı (başkan, yönetim kurulu)
- Üyelik modları: Doğrudan katılım + Onay tabanlı
- Görev değişikliği süreci (danışman onayı)
- Kulüp kurma talepleri
- Logo yönetimi (MinIO)

### 📱 Sosyal Modül (Post Service)
- Blog benzeri içerik paylaşımı
- Beğeni, yorum, yanıt, bookmark desteği
- **Idempotent PUT/DELETE** beğeni endpoint'leri
- Asenkron içerik moderasyonu (LLM)
- Sayfalanmış listeleme (Spring Data Pageable)

### 🏆 Oyunlaştırma (Gamification)
- 5 farklı eylem türü (POST_PUBLISHED, PROFILE_COMPLETED, DAILY_LOGIN, VALID_REPORT, ANSWER_ACCEPTED)
- Liderboard sistemi
- Rozet sistemi (3 farklı rozet): First Step, Profile Complete, Fortnight Warrior
- Eğzamanlılık güvenliği (JPA Optimistic Locking + 3x Retry)
- **10 Flyway göçü** ile sürüm kontrollü şema yönetimi

### 🤖 Yapay Zekâ Servisi
- **Öğrenci Asistanı**: Türkçe niyet (intent) tespiti
- **Akademisyen Copilot**: Kurs bağlamında rehberlik
- **RAG Kulüp Önerisi**: Semantik arama + LLM yanıtı
- **Otomatik İçerik Moderasyonu**: Yorum sınıflandırması (ZORBA | TEMIZ)
- **Semantik Önbellek**: 0.90 kosinüs benzerlik eşikli cache
- **Akışlı Yanıtlar**: Server-Sent Events benzeri token akışı

### 📧 Bildirimler
- Olay güdümlü e-posta billiktil
- Kurs duyuruları, ödev bildirimleri, hesap onayı
- Etkinlik registrasyonu (QR kodlu bilet e-postası)
- Şifre sıfırlama linki (tek kullanımlık)

---

## 🛠️ Teknoloji Yığını

### Backend Framework
| Teknoloji | Versiyon | Amaç |
|-----------|---------|------|
| **Java** | 21 LTS | Programlama dili |
| **Spring Boot** | 3.3.0 | Uygulama çerçevesi |
| **Spring Cloud** | 2023.0.3 | Bulut-yerel bileşenler |
| **Spring Cloud Gateway** | Reaktif (WebFlux + Netty) | API geçidi |
| **Spring Cloud Netflix Eureka** | Yerleşik | Servis keşfi |
| **Spring Cloud Config Server** | Yerleşik | Merkezi yapılandırma |
| **Spring Data JPA** | Yerleşik | ORM / Kalıcılık |
| **Spring Cache** | Yerleşik | Önbellekleme soyutlaması |
| **Spring AMQP** | Yerleşik | RabbitMQ entegrasyonu |
| **Spring Security** | Yerleşik | Kimlik doğrulama/yetkilendirme |
| **Spring AI** | 1.0.0-M6 | LLM abstraction / RAG |

### Altyapı & Kütüphaneleri
| Teknoloji | Versiyon | Amaç |
|-----------|---------|------|
| **PostgreSQL** | 14 | İlişkisel veritabanı |
| **Redis** | 6.2 (Stack) | Bellek içi cache, vektör araması |
| **RabbitMQ** | 3 | AMQP mesajlaşması |
| **MinIO** | 8.5.11 | S3 uyumlu nesne depolama |
| **Ollama** | latest | Yerel LLM runtime |
| **Mailpit** | latest | Geliştirme SMTP sunucusu |
| **Docker Compose** | v2 | Konteynerleştirilmiş altyapı |

### Spesifik Kütüphaneler
| Kütüphane | Versiyon | Amaç |
|-----------|---------|------|
| **JJWT** | 0.12.6 | JWT oluşturma/doğrulama |
| **Spring Cloud OpenFeign** | Yerleşik | Deklaratif HTTP istemci |
| **Resilience4j** | Yerleşik | Devre kesici, dayanıklılık |
| **Lombok** | Yerleşik | Boilerplate kod azaltma |
| **Jackson** | Yerleşik | JSON serileştirme |
| **Google Guava ZXing** | 3.5.x | QR kod oluşturma |
| **Flyway** | Maven | Sürüm kontrollü şema göçü |
| **Apache Maven** | 3.x | Multi-modül build aracı |

---

## 🏗️ Sistem Mimarisi

### Genel Diyagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              İSTEMCİ (Frontend)                              │
└────────────────────────────────────────┬────────────────────────────────────┘
                                         │
                                         ▼
                    ┌────────────────────────────────────┐
                    │       API Gateway (8080)           │
                    │  • JWT doğrulama                   │
                    │  • Header injection                │
                    │  • CORS & Yönlendirme              │
                    │  • Eureka servis keşfi             │
                    └────────────┬───────────────────────┘
                                 │
                ┌────────────────┼────────────────┐
                ▼                ▼                ▼
        ┌─────────────┐  ┌──────────────┐  ┌──────────────┐
        │ Config Srv  │  │ Eureka Srv   │  │   Services   │
        │ (8888)      │  │ (8761)       │  │   (8081-90)  │
        └─────────────┘  └──────────────┘  └──────────────┘


═══════════════════════════════════════════════════════════════════════════════

        İŞ MANTIKI SERVISLERI (9)                      ALTYAPISI:

        ┌────────────────┐
        │  Auth Service  │  ──────────────┐
        │  (kimlik doğ)  │                │
        └────────────────┘                │

        ┌────────────────┐         ┌──────────────────┐
        │  User Service  │         │   PostgreSQL 14  │
        │  (profil)      │         │   (8 şema)       │
        └────────────────┘         └──────────────────┘

        ┌────────────────┐         ┌──────────────────┐
        │ Course Service │  ◄──────┤   Redis 6.2      │
        │  (kurslar)     │         │   (cache, ANN)   │
        └────────────────┘         └──────────────────┘

        ┌────────────────┐         ┌──────────────────┐
        │Assignment Srv  │  ◄──────┤   RabbitMQ 3     │
        │  (ödev/notlar) │         │   (4 exchange)   │
        └────────────────┘         └──────────────────┘

        ┌────────────────┐         ┌──────────────────┐
        │  Event Service │  ◄──────┤   MinIO 8.5.11   │
        │ (etkinlik/QR)  │         │  (nesne stor.)   │
        └────────────────┘         └──────────────────┘

        ┌────────────────┐         ┌──────────────────┐
        │  Club Service  │  ◄──────┤  Mailpit/SMTP    │
        │  (kulüpler)    │         │  (geliş. mail)   │
        └────────────────┘         └──────────────────┘

        ┌────────────────┐         ┌──────────────────┐
        │  Post Service  │  ◄──────┤   Ollama         │
        │  (sosyal)      │         │  (yerel LLM)     │
        └────────────────┘         └──────────────────┘

        ┌────────────────┐
        │ Notification   │
        │  (e-posta)     │
        └────────────────┘

        ┌────────────────┐
        │Gamification Sv │
        │  (puan/rozet)  │
        └────────────────┘

        ┌────────────────┐
        │  LLM Service   │
        │  (AI asistan)  │
        └────────────────┘
```

### Servis Keşfi & Konfigürasyon Akışı

```
Servis Başlatılması:
  1. Config Server'dan (port 8888) `application.yml` indir
  2. Eureka Server'a (port 8761) kayıt ol
     - Servis adı ve port bilgisini gönder
  3. Eureka'ya kaydıolan diğer servisleri öğren
  4. HTTP istekleri geldiğinde Eureka'dan aranan servisi bul
  5. Feign client ile senkron çağrı yap (yük dengelemeli)
  6. RabbitMQ'dan asenkron mesaj dinle
```

---

## 🚀 Hızlı Başlangıç

### Ön Koşullar

- **Bilgisayar**: macOS / Linux / Windows (WSL2)
- **Docker**: Docker Engine 4.x + Docker Compose v2
- **Git**: Sürüm kontrol
- **IDE**: IntelliJ IDEA Ultimate / VS Code + Extension Pack for Java
- **Java**: OpenJDK 21+ (Maven otomatik indir)

### 1️⃣ Depoyu Klonlayın

```bash
git clone https://github.com/[username]/EduConnect-Backend.git
cd EduConnect-Backend
```

### 2️⃣ Docker Altyapısını Başlatın

```bash
docker-compose up -d
```

Konteynerlar başlatılacak:
- ✅ **PostgreSQL** (port 5432)
- ✅ **Redis** (port 6379, admin UI: 8001)
- ✅ **RabbitMQ** (port 5672, admin UI: 15672)
- ✅ **MinIO** (port 9000, admin UI: 9001)
- ✅ **Mailpit** (port 1025 SMTP, UI: 8025)
- ✅ **Ollama** (port 11434)

MinIO ve Mailpit için varsayılan kimlik bilgileri `docker-compose.yml` dosyasında bulunur.

### 3️⃣ Modelleri İndir (LLM için)

Eğer Ollama modelleri yüklü değilse:

```bash
# Sohbet modeli
docker exec ollama ollama pull llama3.2:1b

# Gömme modeli (RAG için)
docker exec ollama ollama pull nomic-embed-text
```

### 4️⃣ Spring Boot Uygulamalarını Başlatın

**Option A: IDE'den (IntelliJ IDEA)**

1. Proje klasörünü IntelliJ'de açın
2. `File → Project Structure → Project` → Java 21 seçin
3. Maven auto-import etkinleştirin
4. Her servisin `src/main/java/.../Application.java` sınıfına sağ tıklayıp `Run` seçin

**Option B: Terminal'den**

```bash
# Tüm servisleri arka planda başlat
./mvnw clean install

# Config Server (gereklidir, önce başlat)
cd config-server
../mvnw spring-boot:run &

# Eureka Server
cd ../eureka-server
../mvnw spring-boot:run &

# API Gateway
cd ../api-gateway
../mvnw spring-boot:run &

# Diğer servisler (paralel başlatabilirsiniz)
cd ../auth-services && ../mvnw spring-boot:run &
cd ../user-service && ../mvnw spring-boot:run &
# ... vesaire
```

### 5️⃣ Başarıyı Doğrulayın

```bash
# API Gateway'in çalışıp çalışmadığını kontrol edin
curl -X GET http://localhost:8080/api/clubs

# Eureka Dashboard
open http://localhost:8761

# RabbitMQ Management UI
open http://localhost:15672  # guest:guest

# Redis Insight
open http://localhost:8001

# MinIO Console
open http://localhost:9001  # your_minio_user:your_minio_password

# Mailpit SMTP
open http://localhost:8025
```

### 6️⃣ Test API Çağrısı Yapın

```bash
# Kayıt
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test1234!",
    "firstName": "Test",
    "lastName": "User"
  }'

# Giriş (token al)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test1234!"
  }' | jq '.accessToken' # Token'ı al

# Korumalı endpoint (token ile)
curl -X GET http://localhost:8080/api/users/profile/[userId] \
  -H "Authorization: Bearer [YOUR_TOKEN]"
```

---

## 📂 Proje Yapısı

```
EduConnect-Backend/ (Maven Multi-Module)
│
├── config-server/               # Merkezi yapılandırma sunucusu (port 8888)
│   ├── src/main/java/.../
│   └── pom.xml
│
├── eureka-server/               # Servis kayıt sunucusu (port 8761)
│   ├── src/main/java/.../
│   └── pom.xml
│
├── api-gateway/                 # Reaktif API geçidi (port 8080)
│   ├── src/main/java/.../
│   │   ├── config/           # CORS, JWT filter konfigürasyonu
│   │   └── filter/           # AuthenticationFilter
│   └── pom.xml
│
├── auth-services/               # Kimlik doğrulama servisi (port 8081)
│   ├── src/main/java/.../
│   │   ├── controller/       # 16+ endpoint
│   │   ├── service/          # JWT, şifre, rol yönetimi
│   │   ├── entity/           # User, RefreshToken, StudentRequest vb.
│   │   └── repository/       # JPA repositories
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/     # Flyway SQL (3 migration)
│   └── pom.xml
│
├── user-service/                # Profil yönetimi servisi (port 8082)
│   ├── src/main/java/.../
│   │   ├── controller/       # Profil CRUD, agregasyon
│   │   ├── service/          # API Composition (Feign)
│   │   ├── dto/              # DTO'lar
│   │   └── client/           # GamificationClient, PostClient
│   ├── src/main/resources/
│   │   └── application.yml
│   └── pom.xml
│
├── course-service/              # Kurs yönetimi servisi (port 8083)
│   ├── src/main/java/.../
│   │   ├── controller/       # 20+ endpoint (kayıt, duyuru)
│   │   ├── service/          # Kurs CRUD, başvuru işleme
│   │   └── entity/           # Course, StudentEnrollment, Announcement
│   └── pom.xml
│
├── assignment-service/          # Ödev servisi (port 8084)
│   ├── src/main/java/.../
│   │   ├── controller/       # Ödev, teslim, notlandırma
│   │   ├── service/          # İş mantığı
│   │   └── listener/         # CourseEventListener (RabbitMQ)
│   └── pom.xml
│
├── event-service/               # Etkinlik servisi (port 8085)
│   ├── src/main/java/.../
│   │   ├── controller/       # Event, advisor, registration
│   │   ├── service/          # QR kod, bilet yönetimi
│   │   └── util/             # ZXing QR kod generator
│   └── pom.xml
│
├── club-service/                # Kulüp yönetimi servisi (port 8086)
│   ├── src/main/java/.../
│   │   ├── controller/       # 25+ endpoint
│   │   ├── service/          # Kulüp, üyelik, yönetim kurulu
│   │   └── entity/           # Club, ClubMembership, RoleChangeRequest
│   └── pom.xml
│
├── post-service/                # Sosyal akış servisi (port 8087)
│   ├── src/main/java/.../
│   │   ├── controller/       # Post, Comment, Like, Bookmark
│   │   ├── service/          # İçerik, beğeni, yorum
│   │   └── listener/         # PostModerationConsumer (RabbitMQ)
│   ├── src/main/resources/
│   │   └── db/migration/     # Flyway (4 migration)
│   └── pom.xml
│
├── notification-service/        # Bildirim servisi (port 8088)
│   ├── src/main/java/.../
│   │   ├── listener/         # 6 Event listener (RabbitMQ)
│   │   └── service/          # Email service, template
│   └── pom.xml
│
├── gamification-service/        # Oyunlaştırma servisi (port 8089)
│   ├── src/main/java/.../
│   │   ├── controller/       # Liderboard, gamification
│   │   ├── service/          # Puan, rozet, seri yönetimi
│   │   ├── listener/         # GamificationEventListener (RabbitMQ)
│   │   └── entity/           # UserReputation, PointHistory, UserBadge
│   ├── src/main/resources/
│   │   └── db/migration/     # Flyway (10 migration!)
│   └── pom.xml
│
├── llm-service/                 # AI servisi (port 8090)
│   ├── src/main/java/.../
│   │   ├── controller/       # 3 endpoint (student-asst, instructor-copilot, agent)
│   │   ├── service/          │
│   │   │   ├── StudentAssistantService
│   │   │   ├── InstructorCopilotService
│   │   │   ├── ClubRecommendationService (RAG)
│   │   │   ├── UnifiedAgentService (akışlı)
│   │   │   ├── AiModerationService (moderasyon)
│   │   │   └── SemanticCacheService (Redis VectorStore)
│   │   ├── ingestion/        # ClubIngestionService (startup)
│   │   ├── client/           # Feign clients (course, assignment, post)
│   │   └── util/             # Türkçe stopword, intent tespiti
│   ├── src/main/resources/
│   │   └── application.yml   # Ollama konfigürasyonu
│   └── pom.xml
│
├── docker-compose.yml           # Konteynerleştirilmiş altyapı
├── pom.xml                      # Ana Maven POM (multi-module, 13 modül)
├── .github/                     # GitHub Actions (CI/CD template)
├── PROJE_RAPORU.md             # Detaylı akademik rapor (Türkçe)
├── TEKNIK_RAPOR.md             # Teknik detaylar (Türkçe)
└── README.md                    # Bu dosya
```

---

## 📚 Mikroservisler Detaylı Açıklama

### 1. Auth Service (Kimlik Doğrulama)

**Sorumluluk**: Tüm kimlik doğrulama ve yetkilendirme işlemleri

**Temel Endpoint'ler**:
```
POST   /api/auth/register                              # Hızlı kayıt
POST   /api/auth/request/student-account              # Belgeli öğrenci başvurusu
POST   /api/auth/request/academician-account          # Akademisyen başvurusu
POST   /api/auth/login                                 # Giriş (accessToken + refreshToken döner)
POST   /api/auth/refresh                               # Token yenileme
POST   /api/auth/logout                                # Çıkış
POST   /api/auth/change-password                       # Şifre değiştirme
POST   /api/auth/forgot-password                       # Şifre sıfırlama e-postası
POST   /api/auth/reset-password                        # Tek kullanımlık link ile sıfırlama
GET    /api/auth/admin/requests/students              # Bekleyen öğrenci başvuruları (Admin)
POST   /api/auth/admin/approve-student/{id}           # Öğrenci onayı (Admin)
GET    /api/auth/admin/requests/academicians          # Akademisyen başvuruları (Admin)
POST   /api/auth/admin/approve-academician/{id}       # Akademisyen onayı (Admin)
```

**Veritabanı Tabloları** (`auth_db` şeması):
- `users` - Temel kullanıcı bilgileri
- `refresh_tokens` - Yenileme token'larının kaydı
- `password_reset_tokens` - Şifre sıfırlama token'ları
- `student_requests` - Bekleyen öğrenci başvuruları
- `academician_requests` - Bekleyen akademisyen başvuruları

**Roller**:
- `ROLE_STUDENT` - Öğrenci
- `ROLE_ACADEMICIAN` - Akademisyen
- `ROLE_ADMIN` - Sistem yöneticisi
- `ROLE_CLUB_OFFICIAL` - Kulüp görevlisi
- `ROLE_PENDING_CLUB_OFFICIAL` - Kurulu görevlisi başvurusu

---

### 2. User Service (Profil Yönetimi)

**Sorumluluk**: Kullanıcı profil bilgilerinin yönetimi ve API Composition

**Temel Endpoint'ler**:
```
GET    /api/users/profile/{userId}                    # Profil getir
PUT    /api/users/profile/{userId}                    # Profil güncelle (JSON/multipart)
POST   /api/users/me/profile-picture                  # Profil fotoğrafı yükle
GET    /api/users/profile/{userId}/aggregated         # Birleşik profil (puan + gönderiler)
GET    /api/users/by-student-number/{no}              # Öğrenci numarasıyla sorgula
GET    /api/users/search/academicians?query=...       # Akademisyen arama
DELETE /api/users/students/{userId}                   # Öğrenci arşivle (Admin)
DELETE /api/users/academicians/{userId}               # Akademisyen arşivle (Admin)
```

**Özniteliği**: **API Composition Design Pattern**
- `GET /api/users/profile/{id}/aggregated` endpoint'i şunu yapar:
  - GamificationClient → Puan, rozet, sıralama
  - PostClient → Son gönderiler
  - Yanıtlar birleştirip frontend'e gönder

**MinIO Entegrasyonu**: Profil fotoğrafları `profile-pictures` bucket'ında saklanır.

---

### 3. Course Service (Kurs Yönetimi)

**Sorumluluk**: Kurs oluşturma, kayıt, duyuru ve materyal yönetimi

**Temel Endpoint'ler**:
```
GET    /api/courses                                    # Tüm kurslar
POST   /api/courses                                    # Kurs oluştur (multipart)
GET    /api/courses/{id}                               # Kurs detayı
DELETE /api/courses/{id}                               # Kurs sil
POST   /api/courses/{id}/enroll-student                # Öğrenci kaydı (doğrudan)
POST   /api/courses/{id}/apply                         # Kursa başvur (FCFS)
GET    /api/courses/{id}/applications/pending         # Bekleyen başvurular
PUT    /api/courses/applications/{id}/approve         # Başvuru onayla
PUT    /api/courses/applications/{id}/reject          # Başvuru reddet
POST   /api/courses/{id}/announcements                # Duyuru oluştur
GET    /api/courses/{id}/enrolled-students            # Kayıtlı öğrenciler (detaylı)
GET    /api/courses/{id}/enrolled-students/ids        # Öğrenci ID listesi
```

**Kayıt Modları**:
1. **Doğrudan Kayıt**: Akademisyen öğrenci ID'lerini sağlar
2. **Başvuru Tabanlı (FCFS)**: Öğrenciler başvurur, akademisyen onayla/reddet

**RabbitMQ Entegrasyonu**:
- Duyuru yayınlandığında → Kayıtlı öğrencilere e-posta bildirim

**MinIO Entegrasyonu**: Kurs kapak görselleri ve materyalleri `course-covers` ve `course-materials` bucket'larında

---

### 4. Assignment Service (Ödev Yönetimi)

**Sorumluluk**: Ödev oluşturma, teslim yönetimi, notlandırma

**Temel Endpoint'ler**:
```
POST   /api/assignments                                # Ödev oluştur (multipart)
GET    /api/assignments/course/{courseId}             # Kursa ait ödevler
POST   /api/assignments/{id}/submit                    # Ödev teslimi (multipart)
PUT    /api/assignments/submissions/{id}/grade        # Notlandırma
GET    /api/assignments/course/{id}/submissions       # Kurs tüm teslimleri
GET    /api/assignments/{id}/submissions              # Ödevin tüm teslimleri
GET    /api/assignments/my-assignments                # Öğrencinin ödevleri
GET    /api/assignments/files/download                # Dosya indir
```

**Dosya Yönetimi**:
- Ödev dosyaları → `assignment-files` bucket
- Teslim dosyaları → `submission-files` bucket

**Olay Dinleme**:
- Kurs silindiğinde → Kurs'a ait tüm ödevler ve teslimleri sil

---

### 5. Event Service (Etkinlik Yönetimi)

**Sorumluluk**: Etkinlik yönetimi, danışman onayı, **QR kod bilet sistemi**

**Temel Endpoint'ler**:
```
GET    /api/events                                     # Aktif etkinlikler (public)
GET    /api/events/{id}                                # Etkinlik detayı
POST   /api/events                                     # Etkinlik oluştur (kulüp görevlisi)
POST   /api/events/{id}/register                       # Etkinliğe kayıt (QR kod üret)
GET    /api/events/my-registrations                    # Öğrencinin kayıtları
GET    /api/events/advisor/pending                     # Danışman onayına bekleyen etkinlikler
PUT    /api/events/{id}/advisor-approve                # Danışman onayı
```

**QR Kod Bilet Sistemi**:
```java
// POST /api/events/{id}/register çağrıldığında:
1. Öğrenci kayıt UUID'si oluştur
2. Google ZXing ile UUID'yi QR kod PNG'ye dönüştür
3. Base64 encode yapıp yanıta ekle
4. Frontend QR kodu telefonda gösterebilir
5. Etkinlik kapısında tarandığında kayıt doğrulanır
```

**Etkinlik Durumu**:
- `PENDING_ADVISOR_APPROVAL` - Danışman onayı bekliyor
- `APPROVED` - Onaylandı, halkın erişimine açık
- `CANCELLED` - İptal edildi
- `PAST` - Geçmiş etkinlik

---

### 6. Club Service (Kulüp Yönetimi)

**Sorumluluk**: Kulüp profilleri, üyelik, yönetim kurulu yapısı

**Temel Endpoint'ler**:
```
GET    /api/clubs                                      # Tüm kulüpler (public)
GET    /api/clubs/{id}                                 # Kulüp detayı
POST   /api/clubs/{id}/join                            # Kulübe katıl
DELETE /api/clubs/{id}/leave                           # Kulüpten ayrıl
POST   /api/clubs/{id}/logo                            # Logo yükle
GET    /api/clubs/my-memberships                       # Üyeliklerim
POST   /api/clubs/{id}/membership-request              # Üyelik talebi (onay tabanlı)
GET    /api/clubs/{id}/members/ids                     # Üye ID listesi (internal, Event için)
GET    /api/clubs/{id}/advisor-id                      # Danışman ID'si (internal, advisor approval)
GET    /api/clubs/search?name=...                      # İsme göre arama
POST   /api/admin/clubs                                # Kulüp oluştur (Admin)
DELETE /api/admin/clubs/{id}                           # Kulüp sil/arşivle (Admin)
```

**Üyelik Modları**:
1. **Doğrudan Katılım**: Öğrenci join eder, hemen üye olur
2. **Onay Tabanlı**: Başvuru yapar, yönetim onayı gerekir

**Yönetim Kurulu Rolleri**:
- Başkan (President)
- Başkan Yardımcısı (Vice President)
- Yönetim Kurulu Üyesi (Board Member)
- Sıradan Üye (Regular Member)

**Görev Değişikliği Süreci**:
- Başkanlık değişikliğinde kulübün danışman akademisyeni onay verir
- `role_change_requests` tablosunda takip edilir

---

### 7. Post Service (Sosyal Akış)

**Sorumluluk**: Blog benzeri içerik paylaşımı, sosyal etkileşim

**Temel Endpoint'ler**:
```
POST   /api/posts                                      # Gönderi oluştur
PUT    /api/posts/{id}                                 # Gönderi güncelle
DELETE /api/posts/{id}                                 # Gönderi sil
GET    /api/posts                                      # Yayınlanmış gönderiler (sayfalı)
GET    /api/posts/{id}                                 # Gönderi detayı
GET    /api/posts/saved                                # Kaydedilen gönderiler
PUT    /api/posts/{id}/likes                           # Beğen (idempotent)
DELETE /api/posts/{id}/likes                           # Beğeniyi kaldır
POST   /api/posts/{id}/comments                        # Yorum ekle
GET    /api/posts/{id}/comments                        # Yorumları listele
DELETE /api/posts/{id}/comments/{cid}                  # Yorum sil
POST   /api/posts/{id}/bookmarks                       # Kaydet
```

**İdempotent Beğeni Design Pattern**:
```
PUT /api/posts/{id}/likes   → Beğenmiş mi? Evet: sil, Hayır: ekle
DELETE /api/posts/{id}/likes → Beğenmiş mi? Evet: sil, Hayır: hata verme
POST /api/posts/{id}/likes    → Toggle (beğen/beğeni kaldır)
```

**Asenkron İçerik Moderasyonu**:
- Yorum gönderilir → RabbitMQ'ya `post.comment.moderate` olayı yayınla
- LLM Service → Yorumu analyze et → TEMIZ mi ZORBA mı?
- Post Service → Sonuç döndürülür, yorum görünürlüğü güncellenir

**Sayfalama**:
- Spring Data `Pageable` ile sayfalanmış listeleme
- Varsayılan: 10 kayıt, yayın tarihine göre azalan sıralama

---

### 8. Notification Service (Bildirimler)

**Sorumluluk**: Olay güdümlü e-posta bildirimleri

**Dinlediği Olaylar**:
```
user.account.status              → Hesap onay/red bildirim
user.password.reset              → Şifre sıfırlama linki
event.created                    → Kulüp üyelerine etkinlik duyurusu
event.registered                 → Öğrenciye etkinlik bilet e-postası (QR kod ile)
course.announcement.created      → Kurs duyurusu bildirimi
course.assignment.created        → Ödev bildirimi
```

**Özellik**: Durumsuz (stateless) servis
- Veritabanı tutmaz
- Yalnızca RabbitMQ mesajlarını dinler
- Spring JavaMailSender ile e-posta gönderir

**Mailpit Entegrasyonu** (Geliştirme Ortamı):
- SMTP: `localhost:1025`
- Web UI: `http://localhost:8025`
- Gönderilen e-postalar burada görüntülenir

---

### 9. Gamification Service (Oyunlaştırma)

**Sorumluluk**: Puan sistemi, rozet, liderboard

**Temel Endpoint'ler**:
```
GET    /api/gamification/internal/users/{id}/summary  # Kullanıcı puan özeti
GET    /api/gamification/leaderboard?limit=20         # Liderboard (top 20)
```

**Puan Sistemi**:
| Eylem | Puan | Not |
|-------|------|-----|
| POST_PUBLISHED | 10 | Gönderi yayını |
| PROFILE_COMPLETED | 100 | Profil tamamlama (tek seferlik) |
| DAILY_LOGIN | Değişken | Her gün giriş (7 gün seri = +50 bonus) |
| VALID_REPORT | 15 | Doğru ihbar |
| ANSWER_ACCEPTED | 50 | Cevap kabul |

**Günlük Limit**: Her eylem tipinden günde maksimum 3 kez puan alınır

**Rozet Sistemi** (3 rozet):
```
First Step (First Blood)        → İlk gönderi paylaşımı
Profile Complete               → Tüm zorunlu profil alanları doldurulması
Fortnight Warrior              → 14 gün üst üste DAILY_LOGIN
```

**Rozet Görselleri**: SVG inline olarak sunulur (CDN bağımlılığı yok)

**Eşzamanlılık Güvenliği**:
- `@Version` anotasyonu ile Optimistic Locking
- Eşzamanlı güncelleme çakışması → `ObjectOptimisticLockingFailureException`
- Catch → 3 kez yeniden dene (exponential backoff)
- Başarısız olursa DLQ'ya (Dead Letter Queue) yönlendir

**Liderboard**:
- Top 20 (parametrize edilebilir, max 100)
- User Service'ten isim bilgisi ile zenginleştirilme
- Cache ile optimize edilmiş

**Flyway Göçü** (10 migration):
```
V1__init_gamification_schema.sql
V2__migrate_to_gamification_db_schema.sql
V3__add_daily_limit_lookup_index.sql
V4__add_leaderboard_sort_index.sql
V5__create_user_badges_table.sql
V6__rename_first_blood_badge.sql
V7__backfill_user_badges.sql
V8__backfill_first_step_badge.sql
V9__add_and_backfill_fortnight_warrior_badge.sql
V10__backfill_profile_complete_badge.sql
```

---

### 10. LLM Service (Yapay Zekâ)

**Sorumluluk**: LLM-tabanlı asistanlar, RAG, içerik moderasyonu

**Temel Endpoint'ler**:
```
POST   /api/ai/student-assistant                      # Öğrenci sorusu
POST   /api/ai/instructor-copilot                     # Akademisyen yardımı
POST   /api/ai/agent                                  # Birleşik ajan (akışlı)
```

#### **Bileşen 1: Student Assistant**

```
Öğrenci → "Bana yapay zekaya ilgi duyan kulüp önerir misin?"
         ↓
Service → Türkçe nyet tespiti
         ↓
        "kulüp" kelimesi var mı? → Evet
         ↓
ClubRecommendationService → RAG
         ↓
SimpleVectorStore.similaritySearch(query, topK=5)
         ↓
Türkçe stopword temizliği (~100 kelime) + regex
         ↓
LLM → "Bilgisayar Topluluğu, Teknoloji Kulübü..."
```

#### **Bileşen 2: Instructor Copilot**

```
Akademisyen → "/api/ai/instructor-copilot"
             → {"question": "Bu hafta hangi konuyu işlemeliyim?"}
              ↓
Service → CourseServiceClient
         (akademisyenin kurslarını getir)
         ↓
         → AssignmentServiceClient
         (bekleyen ödevleri getir)
         ↓
         → Sistem promptu:
            "Sen Türkçe konuşan bir akademisyen asistanısın.
             Kurs: [kurs adı], [ders notu özetleri]
             Bekleyen ödevler: [liste]
             Hangi konuyu işleymeliyim?"
         ↓
LLM → Türkçe yanıt
```

#### **Bileşen 3: Unified Agent (Akışlı)**

```
POST /api/ai/agent
Content-Type: application/json; charset=utf-8
{
  "message": "Kulüpleri nasıl bulurum?",
  "userId": "...",
  "streaming": true
}

Response: text/event-stream
─────────────────────────────
Kontrol akışı:

1. SemanticCacheService.searchCache(message)
   ↓
2. Benzerlik > 0.90 ?
   ├─ YET: Cached yanıt kelimelere bölün
   │       Flux.fromIterable().delayElements(50ms) → Fake typing
   │
   └─ HAYIR: LLM'e gönder
            ↓
            ChatClient.prompt().stream().content()
            ↓
            Her token Frontend'e gönder (SSE)
            ↓
            StringBuffer'a biriktir
            ↓
            onComplete() → Cache'e yaz (Redis)
```

**Semantic Cache Property**:
- Vector Store: Redis (RediSearch + HNSW)
- Benzerlik Eşiği: 0.90 kosinüs benzerliği
- Depolanacak Metadata: `{type: "semantic_cache", cachedResponse: "..."}`
- Faydası: Aynı sorunun tekrar sorulmasında LLM çağrısı yapılmaz

#### **Bileşen 4: RAG Kulüp Önerisi**

```
İşlem Başlığı:
  1. Uygulama başladığında @PostConstruct ile ClubIngestionService çalışır
  2. Club Service'ten tüm kulüpleri çek (JdbcTemplate)
  3. Her kulüp için Türkçe metin oluştur:

     ┌──────────────────────────────────┐
     │ Kulüp Adı: Bilgisayar Topluluğu  │
     │ Kategori: Teknoloji              │
     │ Açıklama: Yapay zeka, yazılım... │
     │ Toplantı Günü: Çarşamba 18:00    │
     └──────────────────────────────────┘

  4. nomic-embed-text ile 768-d vektöre dönüştür
  5. SimpleVectorStore.add(Document) ile ekle
  6. data/vector-store.json dosyasına kaydet

┌─────────────────────────────────┐
│ Sorgu zamanında:                │
├─────────────────────────────────┤
│ 1. Öğrenci: "Yapay zekaya..."   │
│ 2. Vector Store → topK=5        │
│    benzerlik araması            │
│ 3. Türkçe stopword temizliği    │
│ 4. Regex ile kulüp adları ekle  │
│ 5. Dedüplikasyon (LinkedHashSet)│
│ 6. LLM prompt:                  │
│    "Bu kulüpleri öner: [liste]" │
│ 7. Doğal dil Türkçe yanıt       │
└─────────────────────────────────┘
```

#### **Bileşen 5: Otomatik İçerik Moderasyonu**

```
Post Service'ten:
  POST /api/posts/{id}/comments
                ↓
  Yorum gönderilir → RabbitMQ
                 ↓
  moderation.exchange [post.comment.moderate]
                ↓
  LLM Service → AiModerationService.classify(text)
                ↓
                { temperature: 0 (deterministik),
                  system: "Türkçe içerik sınıflandırması",
                  prompt: "Yorum uygun mu? TEMIZ | ZORBA",
                  max_tokens: 10 }
                ↓
  LLM → "TEMIZ" (veya "ZORBA")
                ↓
  ModerationDecisionParser (parsers output)
                ↓
  RabbitMQ → Post Service'e geri
                ↓
  Yorum hidden/approved olarak işaretlenir
```

---

## 📡 Servisler Arası İletişim

### Senkron İletişim (OpenFeign)

```java
// Example: User Service → Gamification Service
@FeignClient(name = "gamification-service")
public interface GamificationClient {
    @GetMapping("/api/gamification/internal/users/{id}/summary")
    GamificationSummaryDto getSummary(@PathVariable("id") UUID userId);
}

// Kullanım:
@Service
public class ProfileAggregationService {
    @Autowired
    private GamificationClient gamificationClient;

    public AggregatedProfileDto aggregate(UUID userId) {
        GamificationSummaryDto gamif = gamificationClient.getSummary(userId);
        // ... diğer Feign çağrıları
        return combined;
    }
}
```

**Feign Konfigürasyonu**:
- Zaman aşımı: 500ms (connectTimeout + readTimeout)
- Yük dengeleme: Eureka müteşekkil (round-robin)
- Devre kesici: Resilience4j

### Asenkron İletişim (RabbitMQ)

```yaml
# Exchange & Kuyruk Yapısı

user-exchange (Direct):
  ├─ user.account.status → notification-event-created-queue
  └─ user.password.reset → password-reset-queue

course.exchange (Topic):
  ├─ course.announcement.created → notification-course-announcement-queue
  └─ course.assignment.created → notification-course-assignment-queue

club-exchange (Direct):
  ├─ event.created → notification-event-created-queue
  └─ event.registered → notification-registration-queue

gamification.exchange (Topic):
  └─ gamification.*.* → gamification.points.queue
```

**Publisher Örneği** (Post Service):
```java
@Service
@RequiredArgsConstructor
public class PostService {
    private final RabbitTemplate rabbitTemplate;

    public void publishPost(Post post) {
        rabbitTemplate.convertAndSend(
            "gamification.exchange",
            "gamification.user.post_published",
            new GamificationEvent(post.getAuthorId(), ActionType.POST_PUBLISHED)
        );
    }
}
```

**Listener Örneği** (Gamification Service):
```java
@Service
@RequiredArgsConstructor
public class GamificationEventListener {

    @RabbitListener(queues = "gamification.points.queue")
    public void handleGamificationEvent(GamificationEvent event) {
        userReputation.addPoints(event.getUserId(), event.getActionType().getPoints());
    }
}
```

---

## 💾 Veritabanı Tasarımı

### Şema Yapısı (Ser Başına Şema Deseni)

```sql
-- PostgreSQL Veritabanı: educonnect
-- 8 Mantıksal Şema:

CREATE SCHEMA auth_db;        -- Auth Service
CREATE SCHEMA user_db;        -- User Service
CREATE SCHEMA course_db;      -- Course Service
CREATE SCHEMA assignment_db;  -- Assignment Service
CREATE SCHEMA event_service;  -- Event Service
CREATE SCHEMA club_db;        -- Club Service
CREATE SCHEMA post_db;        -- Post Service
CREATE SCHEMA gamification_db;-- Gamification Service
```

### Tablolar ve İlişkiler

#### auth_db.users (Ana Tablo)

```sql
CREATE TABLE auth_db.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    roles VARCHAR(100) NOT NULL, -- "ROLE_STUDENT,ROLE_CLUB_OFFICIAL"
    status VARCHAR(50) DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, DELETED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### course_db.courses (Kurs)

```sql
CREATE TABLE course_db.courses (
    id UUID PRIMARY KEY,
    instructor_id UUID NOT NULL,
    code VARCHAR(20) UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    capacity INT,
    semester VARCHAR(20), -- "Spring2024", "Fall2024"
    cover_image_url VARCHAR(255),
    enrollment_mode VARCHAR(50), -- "DIRECT", "APPLICATION"
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Kayıt İlişkisi (Çoktan Çoğa)
CREATE TABLE course_db.student_course_enrollments (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    course_id UUID NOT NULL REFERENCES courses(id),
    enrolled_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) -- "ACTIVE", "COMPLETED", "WITHDRAWN"
);
```

#### post_db.posts & post_db.comments (Sosyal)

```sql
CREATE TABLE post_db.posts (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL,
    title VARCHAR(255),
    content TEXT,
    category VARCHAR(50),
    status VARCHAR(50) -- "DRAFT", "PUBLISHED", "ARCHIVED"
);

CREATE TABLE post_db.comments (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES posts(id),
    author_id UUID NOT NULL,
    parent_comment_id UUID, -- İç içe yorum desteği
    content TEXT,
    status VARCHAR(50) -- "PENDING", "APPROVED", "HIDDEN"
);

CREATE TABLE post_db.post_likes (
    post_id UUID NOT NULL REFERENCES posts(id),
    user_id UUID NOT NULL,
    PRIMARY KEY (post_id, user_id)
);
```

#### gamification_db.user_reputation (Puan)

```sql
CREATE TABLE gamification_db.user_reputation (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    total_points INT DEFAULT 0,
    version INT DEFAULT 0, -- Optimistic locking
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE gamification_db.point_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_reputation(user_id),
    action_type VARCHAR(50), -- "POST_PUBLISHED", "DAILY_LOGIN" vs.
    points INT,
    action_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at_date DATE -- Günlük limit kontrolü için
);

-- İndeks: Her kullanıcıdan günlük limit kontrolü
CREATE INDEX idx_point_history_user_action_date
ON gamification_db.point_history(user_id, action_type, created_at_date);

CREATE TABLE gamification_db.user_badges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_reputation(user_id),
    badge_name VARCHAR(50),
    earned_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### club_db.clubs (Kulüp)

```sql
CREATE TABLE club_db.clubs (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    advisor_id UUID, -- Danışman akademisyen
    logo_url VARCHAR(255),
    founded_year INT,
    status VARCHAR(50) -- "ACTIVE", "ARCHIVED"
);

CREATE TABLE club_db.club_memberships (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    club_id UUID NOT NULL REFERENCES clubs(id),
    role VARCHAR(50), -- "PRESIDENT", "VICE_PRESIDENT", "BOARD_MEMBER", "MEMBER"
    joined_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Şema Yönetimi

| Servis | Yönetim Aracı | Strateji |
|--------|---------------|----------|
| auth-services | Flyway | SQL-first, sürüm kontrollü |
| post-service | Flyway | SQL-first, sürüm kontrollü |
| gamification-service | Flyway | SQL-first, 10 migration |
| Diğerleri | Hibernate | `ddl-auto: update` (geliştirme) |

---

## 🔐 Güvenlik Mimarisi

### Çok Katmanlı Güvenlik Tasarımı

```
┌─────────────────────────────────────────────────────────────┐
│ KATMAN 1: API Gateway (JWT Doğrulama)                       │
│  • AuthenticationFilter → Authorization başlığı kontrolü    │
│  • Token imzası doğrula (HMAC-SHA256)                       │
│  • Süresi dolmuş token'lar 401 Unauthorized                 │
│  • X-Authenticated-User-* başlıklarını ekle                  │
└──────┬──────────────────────────────────────────────────────┘
       ▼
┌─────────────────────────────────────────────────────────────┐
│ KATMAN 2: Servis Düzeyinde Yetkilendirme                    │
│  • @PreAuthorize("hasRole('ADMIN')")                        │
│  • @PreAuthorize("hasAnyRole('STUDENT','CLUB_OFFICIAL')")  │
│  • Spring Security konfigürasyonu                           │
└──────┬──────────────────────────────────────────────────────┘
       ▼
┌─────────────────────────────────────────────────────────────┐
│ KATMAN 3: Kaynak Sahipliği Doğrulaması                      │
│  • X-Authenticated-User-Id ≠ Kaynağın Sahibi → 403         │
│  • Örn: Başkası'nın gönderisini düzenlemeye çalışma       │
└──────┬──────────────────────────────────────────────────────┘
       ▼
┌─────────────────────────────────────────────────────────────┐
│ KATMAN 4: Veri Şifrelemesi & Gizlilik                       │
│  • Şifreler: BCrypt ile karma                              │
│  • Refresh token: Veritabanında saklanmış (logout sil)     │
│  • HTTPS (üretim): TLS 1.3                                 │
└─────────────────────────────────────────────────────────────┘
```

### JWT Token Yapısı

```json
// Header
{
  "alg": "HS256",
  "typ": "JWT"
}

// Payload
{
  "sub": "example@university.edu",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "roles": "ROLE_STUDENT,ROLE_CLUB_OFFICIAL",
  "iat": 1717689000,
  "exp": 1717692600  // 1 saat sonra süresi dol
}

// Signature
HMACSHA256(
  base64UrlEncode(header) + "." +
  base64UrlEncode(payload),
  SECRET_KEY
)
```

### Token Yönetimi Akışı

```
1. Kayıt/Giriş
   POST /api/auth/login
   ↓
   JWT oluştur (1 saat validity)
   Refresh token oluştur (7 gün validity)
   refresh_tokens tablosuna kaydet
   ↓
   {
     "accessToken": "eyJhbGc...",
     "refreshToken": "550e8400...",
     "tokenType": "Bearer",
     "expiresIn": 3600
   }

2. Korumalı İstek
   GET /api/users/profile/me
   Authorization: Bearer eyJhbGc...
   ↓
   API Gateway: Token doğrula
   ↓
   X-Authenticated-User-Id: 550e8400...
   X-Authenticated-User-Roles: ROLE_STUDENT

3. Token Yenileme (1 saatteki süresi dolmadan)
   POST /api/auth/refresh
   Body: { "refreshToken": "550e8400..." }
   ↓
   Refresh token DB'de var mı? Kontrolü
   Yeni accessToken oluştur
   ↓
   { "accessToken": "newEyJhbGc..." }

4. Çıkış
   POST /api/auth/logout
   Body: { "refreshToken": "550e8400..." }
   ↓
   refresh_tokens tablosundan sil
   ↓
   { "message": "Logged out successfully" }
```

### Rol Tabanlı Erişim Kontrolü (RBAC)

| Rol | Endpoint'ler | Açıklama |
|-----|--------------|----------|
| ROLE_STUDENT | Kurs kayıt, gönderi paylaş, ödev teslimi | Temel öğrenci |
| ROLE_ACADEMICIAN | Kurs oluştur, ödev ver, duyuru yap | Akademisyen |
| ROLE_ADMIN | Tüm admin endpoint'leri | Sistem yöneticisi |
| ROLE_CLUB_OFFICIAL | Etkinlik oluştur, kulüp yönet | Kulüp görevlisi |
| ROLE_PENDING_CLUB_OFFICIAL | Çoğu endpoint'e erişim yok | Talep sahibi |

---

## 🤖 Yapay Zekâ Entegrasyonu

### Kurulum ve Modeller

```bash
# Modelleri indir
docker exec ollama ollama pull llama3.2:1b      # Sohbet modeli (1B params)
docker exec ollama ollama pull nomic-embed-text # Gömme modeli (768 boyut)

# Doğrulama
curl http://localhost:11434/api/tags

# Cevap örneği:
{
  "models": [
    {
      "name": "llama3.2:1b",
      "size": 2000000000,
      ...
    },
    {
      "name": "nomic-embed-text",
      ...
    }
  ]
}
```

### İçinde Spring AI Yapılandırması

```yaml
# application.yml (llm-service)
spring:
  ai:
    ollama:
      base-url: http://ollama:11434

      # Sohbet Modeli
      chat:
        options:
          model: llama3.2:1b
          temperature: 0.0      # Deterministik (moderasyon)
          top_p: 0.1           # Düşük varyans
          num_predict: 128     # Max 128 token yanıt
          num_ctx: 1024        # 1024 token bağlam penceresi

      # Gömme Modeli (RAG)
      embedding:
        options:
          model: nomic-embed-text
          # 768 boyutlu vektörler üretilir
```

### RAG (Retrieval-Augmented Generation) Akışı

```
┌────────────────────────────────────────┐
│ 1. İNGESTİON (Uygulama Başlangıcında)  │
└────────────────────────────────────────┘

ClubIngestionService @PostConstruct
    ↓
club_db.clubs tablosundan tüm kulüpleri oku
    ↓
Her kulüp için Markdown metin oluştur:
    ┌─────────────────────────────────────────────┐
    │ Kulüp Adı: Yapay Zekâ Topluluğu             │
    │ Kategori: Teknoloji                          │
    │ Açıklama: LLM, NLP, Görsel AI araştırması   │
    │ Toplantı Günü: Salı 17:00                   │
    └─────────────────────────────────────────────┘
    ↓
nomic-embed-text model ile vektör oluştur
    ↓
SimpleVectorStore.add(new Document(content))
    ↓
data/vector-store.json dosyasına kaydet (~200KB)

┌────────────────────────────────────────┐
│ 2. SORGULAMA (POST /api/ai/student-...)│
└────────────────────────────────────────┘

Öğrenci → "Yapay zekaya ilgi duyan kulüp önerir misin?"
    ↓
Niyet Tespiti:
    - Türkçe stopword + aleykeykelime REMOVED
    - Temizlenmiş: "yapay zeka ilgi kulüp öner"
    ↓
"kulüp" keyword bulundu → ClubRecommendationService
    ↓
SimpleVectorStore.similaritySearch(query, topK=5)
    ↓
Kosinüs benzerliğine göre sıralanan 5 doküman:
    1. "Yapay Zekâ Topluluğu" (0.95)
    2. "Veri Bilimi Kulübü" (0.87)
    3. "Robotik ve Otomasyon" (0.82)
    ...
    ↓
META_NAME_PATTERN regex ile kulüp adları ekstrakları
    ↓
Türkçe stopword listesi ile temizle
    ↓
LinkedHashSet ile dedüplikasyon
    ↓
LLM Prompt:
    SYSTEM: "Türkçe eğitim danışmanısın"
    USER: "Öğrenci şu kulüblere ilgi gösterebilir: [liste]"
    ↓
LLM (temperature=0, max 128 token) → Yanıt
    ↓
Response: "Şu kulüpleri öneriyorum..."
```

### Semantik Önbellek

```
┌──────────────────────────────────────────────┐
│ Semantic Cache HIT (benzerlik > 0.90)        │
└──────────────────────────────────────────────┘

Sorgu: "Yapay zekaya ilgi duyan kulüp?"
    ↓
Redis VectorStore aranır:
    similaritySearch(query, topK=1, threshold=0.90)
    ↓
Benzerlik > 0.90 bulundu!
    ↓
Cached response döndürülür:
    metadata.cachedResponse = "Yapay Zekâ Topluluğu..."
    ↓
Yanıt kelimelere bölün: ["Yapay", "Zekâ", "Topluluğu", ...]
    ↓
Flux<String> ile 50ms aralıkla stream et
    ↓
Frontend: Fake typing efekti (LLM çağrı yapılmaksızın)

┌──────────────────────────────────────────────┐
│ Semantic Cache MISS (benzerlik ≤ 0.90)       │
└──────────────────────────────────────────────┘

    ↓
LLM'e gönder (gerçek çağrı)
    ↓
SpringAI: ChatClient.prompt().stream().content()
    ↓
Ollama HTTP stream yanıtı
    ↓
Her token Frontend'e SSE ile gönder
    ↓
StringBuffer'a biriktir (tam yanıt)
    ↓
onComplete() → Redis'e yaz:
    {
      "content": "Yapay Zekâ Topluluğu, ...",
      "metadata": {
        "type": "semantic_cache",
        "cachedResponse": "..."
      },
      "vector": [0.123, -0.456, ...] // 768d
    }
    ↓
50ms delay ile stream edilen yanıt frontend'e ulaşır
```

---

## 👨‍💻 Geliştirilme Rehberi

### Yeni Servis Ekleme

1. **Scaffold Oluşturma**
   ```bash
   mkdir new-service
   touch new-service/pom.xml
   ```

2. **pom.xml Yapısı**
   ```xml
   <parent>
       <groupId>com.educonnect</groupId>
       <artifactId>educonnect-parent</artifactId>
       <version>1.0.0-SNAPSHOT</version>
   </parent>
   <artifactId>new-service</artifactId>

   <dependencies>
       <!-- Spring Boot Web / WebFlux -->
       <dependency>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-starter-web</artifactId>
       </dependency>
       <!-- Spring Cloud -->
       <dependency>
           <groupId>org.springframework.cloud</groupId>
           <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
       </dependency>
       <!-- JPA (eğer veritabanı kullanacaksanız) -->
       <dependency>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-starter-data-jpa</artifactId>
       </dependency>
   </dependencies>
   ```

3. **Application Main Class**
   ```java
   package com.educonnect.newservice;

   import org.springframework.boot.SpringApplication;
   import org.springframework.boot.autoconfigure.SpringBootApplication;
   import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

   @SpringBootApplication
   @EnableDiscoveryClient
   public class NewServiceApplication {
       public static void main(String[] args) {
           SpringApplication.run(NewServiceApplication.class, args);
       }
   }
   ```

4. **application.yml**
   ```yaml
   spring:
     application:
       name: new-service
     config:
       import: "configserver:http://localhost:8888"
     jpa:
       hibernate:
         ddl-auto: update

   eureka:
     client:
       service-url:
         defaultZone: http://localhost:8761/eureka/

   server:
     port: 8091
   ```

5. **Parent pom.xml'e Ekle**
   ```xml
   <modules>
       ...
       <module>new-service</module>
   </modules>
   ```

### REST Controller Yazma

```java
package com.educonnect.newservice.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.UUID;

@RestController
@RequestMapping("/api/resource")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping
    public ResponseEntity<List<ResourceDTO>> getAll() {
        return ResponseEntity.ok(resourceService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResourceDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(resourceService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResourceDTO> create(@RequestBody CreateResourceRequest req) {
        return ResponseEntity.status(201).body(resourceService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResourceDTO> update(
        @PathVariable UUID id,
        @RequestBody UpdateResourceRequest req
    ) {
        return ResponseEntity.ok(resourceService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        resourceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

### Service Yazma (JPA)

```java
package com.educonnect.newservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository repository;
    private final ResourceMapper mapper;

    public List<ResourceDTO> getAll() {
        return repository.findAll()
            .stream()
            .map(mapper::toDTO)
            .collect(Collectors.toList());
    }

    public ResourceDTO getById(UUID id) {
        return repository.findById(id)
            .map(mapper::toDTO)
            .orElseThrow(() -> new ResourceNotFoundException("ID: " + id));
    }

    public ResourceDTO create(CreateResourceRequest req) {
        Resource resource = new Resource();
        resource.setName(req.getName());
        resource.setDescription(req.getDescription());
        // ... diğer alanlar

        resource = repository.save(resource);
        return mapper.toDTO(resource);
    }
}
```

### RabbitMQ Event Yayınlama

```java
@Service
@RequiredArgsConstructor
public class EventPublisherService {

    private final RabbitTemplate rabbitTemplate;

    public void publishResourceCreated(Resource resource) {
        rabbitTemplate.convertAndSend(
            "resource-exchange",                    // Exchange
            "resource.created",                     // Routing key
            new ResourceCreatedEvent(
                resource.getId(),
                resource.getName(),
                System.currentTimeMillis()
            )
        );
    }
}
```

### RabbitMQ Event Dinleme

```java
@Service
public class ResourceEventListener {

    @RabbitListener(queues = "resource-created-queue")
    public void handle ResourceCreatedEvent(ResourceCreatedEvent event) {
        System.out.println("Resource created: " + event.getResourceId());
        // İş mantığını çalıştır
    }
}
```

### Feign Client Yazma

```java
@FeignClient(name = "externa1-service", url = "http://localhost:8080")
public interface ExternalServiceClient {

    @GetMapping("/api/data/{id}")
    ExternalDataDTO getData(@PathVariable("id") UUID id);

    @PostMapping("/api/data")
    @Headers("Content-Type: application/json")
    ExternalDataDTO createData(@RequestBody CreateExternalDataRequest req);
}
```

---

## ⚡ Performans Optimizasyonu

### 1. Redis Caching Stratejisi

```java
// Application-level cache
@Service
public class CourseService {

    @Cacheable(value = "courses", key = "#id")
    public CourseDTO getCourse(UUID id) {
        // Veritabanı sorgusundan kaçın
        return courseRepository.findById(id)
            .map(courseMapper::toDTO)
            .orElseThrow();
    }

    @CacheEvict(value = "courses", key = "#id")
    public void updateCourse(UUID id, UpdateCourseRequest req) {
        // Cache'ten sil, sonra güncelle
        courseRepository.save(updatedCourse);
    }
}
```

### 2. Veritabanı Sorgularını Optimize Etme

```java
// ❌ N+1 Problem
List<Course> courses = courseRepository.findAll();
for (Course course : courses) {
    System.out.println(course.getInstructor().getName()); // Her döngüde SELECT
}

// ✅ EAGER Loading ile çözüm
@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.instructor")
    List<Course> findAllWithInstructor();
}

// ✅ Projections ile çözüm
@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    @Query("SELECT new com.educonnect.dto.CourseDTO(c.id, c.name, c.instructor.name) FROM Course c WHERE c.id = :id")
    CourseDTO findDTOById(@Param("id") UUID id);
}
```

### 3. Asenkron İşleme

```java
@Service
public class NotificationService {

    @Async
    public void sendEmailAsync(String email, String subject, String content) {
        // Uzun işlem arka planda çalışır
        mailSender.send(email, subject, content);
        // İstek thread'i bloklanmaz
    }
}
```

### 4. Pagination (Sonsuz Scroll Yerine)

```java
@GetMapping("/api/posts")
public ResponseEntity<Page<PostDTO>> getPosts(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "10") int size,
    @RequestParam(defaultValue = "createdAt") String sortBy
) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).descending());
    Page<PostDTO> posts = postService.findAll(pageable);
    return ResponseEntity.ok(posts);
}
```

### 5. API Composition Optimization

```java
// ❌ Sequential (Yavaş)
public AggregatedData getAggregated(UUID userId) {
    UserData user = userService.getUser(userId);        // 100ms
    GameficationData gam = gamficationService.getData(userId); // 150ms
    PostData posts = postService.getPosts(userId);     // 200ms
    // Toplam: 450ms
    return aggregate(user, gam, posts);
}

// ✅ Parallel ile Feign
@Async
private CompletableFuture<UserData> getUserAsync(UUID id) {
    return CompletableFuture.completedFuture(
        userClient.getUser(id)
    );
}

public AggregatedData getAggregated(UUID userId) {
    CompletableFuture<UserData> userFuture = getUserAsync(userId);           // parallel
    CompletableFuture<GameficationData> gamFuture = getGamificationAsync(userId);
    CompletableFuture<PostData> postFuture = getPostsAsync(userId);

    CompletableFuture.allOf(userFuture, gamFuture, postFuture).join();
    // Toplam: ~200ms (en yavaş çağrının süresi)

    return aggregate(
        userFuture.join(),
        gamFuture.join(),
        postFuture.join()
    );
}
```

---

## 🐛 Sorun Giderme

### Docker Konteynerler Başlamaz

```bash
# Log'ları kontrol et
docker-compose logs postgres
docker-compose logs redis
docker-compose logs rabbitmq

# Konteyner bilgisini göster
docker ps -a

# Konteyner durumu sıfırla
docker-compose down -v  # Volume'leri sil
docker-compose up -d    # Yeniden başlat
```

### PostgreSQL Bağlantı Hatası

```
com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Exception during pool initialization
```

```bash
# PostgreSQL konteynerine bağlan
docker exec -it educonnect-postgres psql -U eduadmin -d educonnect

# Veritabanı var mı kontrol et
\l

# Şema oluştur
CREATE SCHEMA auth_db;
CREATE SCHEMA user_db;
# ... vesaire

# Exit
\q
```

### JWT Token Validation Error

```
io.jsonwebtoken.ExpiredJwtException: JWT expired at
```

**Çözüm**: Refresh token kullan
```bash
POST http://localhost:8080/api/auth/refresh
Body: {
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

### RabbitMQ Bağlantı Hatası

```
com.rabbitmq.client.Connection - Could not open socket to 127.0.0.1:5672
```

```bash
# RabbitMQ konteyner çalışıyor mu?
docker ps | grep rabbitmq

# RabbitMQ Management UI erişilebilir mi?
curl http://localhost:15672/api/overview -u guest:guest

# Konteyner logları
docker logs educonnect-rabbitmq
```

### MinIO Bucket Bulamaz

```bash
# MinIO konteyneri içine gir
docker exec -it educonnect-minio /bin/sh

# mc (CLI) ile bucket oluştur
mc alias set minio http://localhost:9000 minioadmin minioadmin
mc mb minio/profile-pictures
mc mb minio/course-covers
```

### Ollama Model Yükleme Hatası

```bash
# Modelleri listele
docker exec ollama ollama list

# Model indir (tekrar deneyin)
docker exec ollama ollama pull llama3.2:1b

# Ollama servisi log'ları
docker logs ollama

# Ollama API'ye ping at
curl -X POST http://localhost:11434/api/generate \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama3.2:1b",
    "prompt": "Hello"
  }'
```

### Microservis Port Çakışması

```bash
# Kullanımda olan port'ları göster
lsof -i :8080
lsof -i :8081
lsof -i :5432

# Process kill et (macOS)
kill -9 <PID>

# windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

---

## 📖 API Dokümantasyonu

### Authentication Endpoints

#### 1. Register (Kayıt)

```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "student@university.edu",
  "password": "SecurePassword123!",
  "firstName": "John",
  "lastName": "Doe"
}

Response 201:
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "student@university.edu",
  "roles": ["ROLE_STUDENT"],
  "status": "ACTIVE"
}
```

#### 2. Login (Giriş)

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "student@university.edu",
  "password": "SecurePassword123!"
}

Response 200:
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

#### 3. Student Account Request (Belgeli Başvuru)

```http
POST /api/auth/request/student-account
Content-Type: multipart/form-data

Form Data:
- email: "newstudent@university.edu"
- password: "Password123!"
- firstName: "Jane"
- lastName: "Smith"
- studentDocument: <PDF file>

Response 201:
{
  "requestId": "550e8400...",
  "status": "PENDING",
  "message": "Your application has been submitted"
}
```

### Course Endpoints

#### Get All Courses

```http
GET /api/courses?page=0&size=10&sort=name,asc
Authorization: Bearer <TOKEN>

Response 200:
{
  "content": [
    {
      "id": "550e8400...",
      "code": "CS101",
      "name": "Introduction to Computer Science",
      "instructor": "Dr Ahmed",
      "capacity": 50,
      "enrolledCount": 45
    }
  ],
  "totalElements": 25,
  "totalPages": 3,
  "currentPage": 0
}
```

#### Create Course (Akademiysan)

```http
POST /api/courses
Authorization: Bearer <TOKEN>
Content-Type: multipart/form-data

Form Data:
- code: CS202
- name: Advanced Algorithms
- description: Deep dive into algo
- capacity: 30
- semester: Spring2024
- coverImage: <PNG/JPG file>

Response 201:
{
  "id": "550e8400...",
  "code": "CS202",
  "name": "Advanced Algorithms",
  "instructorId": "550e8400..."
}
```

#### Enroll Student in Course

```http
POST /api/courses/{courseId}/enroll-student
Authorization: Bearer <TOKEN>
Content-Type: application/json

{
  "studentIds": [
    "550e8400-e29b-41d4-a716-446655440001",
    "550e8400-e29b-41d4-a716-446655440002"
  ]
}

Response 200:
{
  "enrolledCount": 2,
  "message": "Students enrolled successfully"
}
```

### Post (Sosyal) Endpoints

#### Create Post

```http
POST /api/posts
Authorization: Bearer <TOKEN>
Content-Type: application/json

{
  "title": "My First Post",
  "content": "# Hello World\n This is my first blog post",
  "category": "Technology",
  "status": "PUBLISHED"
}

Response 201:
{
  "id": "550e8400...",
  "title": "My First Post",
  "authorId": "550e8400...",
  "createdAt": "2024-06-01T10:30:00Z",
  "likeCount": 0,
  "commentCount": 0
}
```

#### Like Post (Idempotent)

```http
PUT /api/posts/{postId}/likes
Authorization: Bearer <TOKEN>

Response 200:
{
  "liked": true,
  "likeCount": 42
}

# Tekrar çağrılarak:
PUT /api/posts/{postId}/likes
Authorization: Bearer <TOKEN>

Response 200:
{
  "liked": false,    # Beğeni kaldırıldı
  "likeCount": 41
}
```

### Gamification Endpoints

#### Get Leaderboard

```http
GET /api/gamification/leaderboard?limit=20
Authorization: Bearer <TOKEN>

Response 200:
{
  "leaderboard": [
    {
      "rank": 1,
      "userId": "550e8400...",
      "userName": "John Doe",
      "totalPoints": 5420,
      "badges": ["First Step", "Profile Complete"],
      "profilePicture": "https://minio.../profile/..."
    },
    {
      "rank": 2,
      "userId": "550e8400...",
      "userName": "Jane Smith",
      "totalPoints": 4850,
      "badges": ["First Step", "Fortnight Warrior"]
    }
  ]
}
```

### AI Endpoints

#### Student Assistant

```http
POST /api/ai/student-assistant
Authorization: Bearer <TOKEN>
Content-Type: application/json

{
  "message": "Bana yapay zekaya ilgi duyan bir kulüp önerir misin?"
}

Response 200:
{
  "response": "Yapay Zekâ Topluluğu şu programları sunmaktadır:\n
             - Makine Öğrenmesi Atölyesi\n
             - Doğal Dil İşleme Semineri\n...",
  "intent": "CLUB_RECOMMENDATION",
  "processingTimeMs": 1240
}
```

#### Unified Agent (Streaming)

```http
POST /api/ai/agent
Authorization: Bearer <TOKEN>
Content-Type: application/json
Accept: text/event-stream

{
  "message": "Kurs başvurusu nasıl yapabilirim?",
  "streaming": true
}

Response 200 (text/event-stream):
data: "Kurs\n"
data: " başvuru\n"
data: "su \n"
data: "şu\n"
data: " aşama\n"
data: "lar\n"
data: "dan \n"
data: "geçmek\n"
data: "tedil\n"
data: ":...\n"
```

---

## 📊 Metrikler & Monitoring

### Actuator Endpoint'leri

```bash
# Health check
GET http://localhost:8081/actuator/health

# Metrics
GET http://localhost:8081/actuator/metrics

# Specific metric
GET http://localhost:8081/actuator/metrics/http.server.requests

# Environment info
GET http://localhost:8081/actuator/env
```

---

## 🤝 Katkıda Bulunma

### Git Workflow

```bash
# 1. Feature branch oluştur
git checkout -b feature/new-capability

# 2. Değişiklikleri yaparak commit et
git add .
git commit -m "feat: add new capability description"

# 3. Branch'i push et
git push origin feature/new-capability

# 4. GitHub'dan Pull Request oluştur
# → Code Review talebi

# 5. Onaylandıktan sonra merge et
```

### Commit Mesaj Formatı

```
<type>(<scope>): <subject>

<body>

<footer>
```

Örnek:
```
feat(gamification): add fortnight warrior badge

- Added daily login streak tracking
- Implemented 14-day badge logic
- Added Flyway migration V9

Fixes #123
```

**Types**: feat, fix, docs, style, refactor, perf, test, chore

---

## 📄 Lisans & Ek Kaynaklar

- **Lisans**: MIT
- **PROJE_RAPORU.md**: Detaylı akademik rapor (1000+ satır)
- **TEKNIK_RAPOR.md**: Teknik mimarisi detayları

---

## 📞 İletişim & Destek

Sorularınız için:
- GitHub Issues: Hata raporları ve özellik talepleri
- Email: [contact@educonnect.dev]
- Discord: [Community sunucusu linki]

---

<div align="center">

**⭐ Bu projeyi beğendiyseniz star vermeyi unutmayın!**

Made with ❤️ by [Ahmet Berke Çiftçi]

**Happy Coding! 🚀**

</div>

