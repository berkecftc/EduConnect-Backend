# LLM Service — Teknik Analiz

> **Proje:** EduConnect Backend  
> **Modül:** `llm-service`  
> **Tarih:** 2026-05-21  
> **Teknoloji:** Java 21 · Spring Boot 3 · Spring AI · Ollama · RabbitMQ · Redis · PostgreSQL

---

## 1. Genel Bakış

`llm-service`, EduConnect mikroservis mimarisinin yapay zeka katmanıdır. Üç temel görevi vardır:

| Görev | Açıklama |
|---|---|
| **Instructor Copilot** | Akademisyenlerin doğal dille duyuru oluşturmasını sağlar |
| **Student Assistant** | Öğrencilerin ödev sorgulama ve kulüp öneri isteklerini karşılar |
| **Post Moderasyonu** | Forum gönderilerini otomatik olarak sınıflandırır (küfür/zorbalık tespiti) |

Servis, Eureka'ya kayıtlı ve Config Server üzerinden yapılandırılmıştır.

---

## 2. Teknoloji Yığını

```
Spring Boot 3 (Java 21)
├── Spring AI — Ollama (llama3.2:1b) → Sohbet + Embedding (nomic-embed-text)
├── Spring AI — Redis Vector Store → Semantik önbellekleme
├── Spring AI — SimpleVectorStore (JSON dosyası) → Kulüp RAG
├── Spring AI — PDF Document Reader
├── Spring Cloud OpenFeign → Servislerarası HTTP çağrıları
├── Spring AMQP (RabbitMQ) → Asenkron post moderasyonu
├── Spring WebFlux (Reactor) → Akışlı (streaming) yanıtlar
└── Spring JDBC + PostgreSQL → Kulüp verisi çekme
```

### Bağımlılıklar (pom.xml özeti)

| Bağımlılık | Amaç |
|---|---|
| `spring-ai-ollama-spring-boot-starter` | Yerel LLM entegrasyonu |
| `spring-ai-redis-store-spring-boot-starter` | Semantik cache |
| `spring-ai-pdf-document-reader` | PDF doküman okuma |
| `spring-boot-starter-amqp` | RabbitMQ mesajlaşma |
| `spring-cloud-starter-openfeign` | Feign HTTP istemcileri |
| `spring-boot-starter-webflux` | Reaktif programlama |
| `netty-resolver-dns-native-macos` | macOS ARM Netty DNS düzeltmesi |
| `spring-boot-starter-jdbc` + `postgresql` | Kulüp DB erişimi |

---

## 3. Dosya Yapısı

```
llm-service/src/main/java/com/educonnect/llmservice/
├── LlmServiceApplication.java          # Ana uygulama
├── client/
│   ├── AssignmentServiceClient.java    # Feign: ödev servisi
│   ├── CourseServiceClient.java        # Feign: ders servisi
│   └── PostServiceClient.java          # Feign: gönderi servisi
├── config/
│   ├── AiConfig.java                   # ChatClient bean
│   ├── AiToolsConfig.java              # LLM Function Tools
│   ├── HttpConfig.java                 # HTTP timeout ayarları
│   ├── RabbitMQConfig.java             # Mesaj kuyruğu tanımları
│   └── RagConfig.java                  # SimpleVectorStore kurulumu
├── controller/
│   └── CopilotController.java          # REST endpoint'leri
├── dto/
│   ├── ClubResponse.java
│   ├── InstructorCourseSummary.java
│   ├── event/PostModerationEvent.java
│   └── moderation/
│       ├── ModerationDecision.java     # Enum: ZORBA | TEMIZ
│       └── ModerationDecisionRequest.java
├── listener/
│   └── PostModerationListener.java     # RabbitMQ tüketici
├── service/
│   ├── AiAssistantService.java         # Öğrenci ödev sorgulama
│   ├── AiModerationService.java        # İçerik moderasyonu
│   ├── ClubIngestionService.java       # DB → VectorStore ETL
│   ├── ClubRecommendationService.java  # RAG tabanlı kulüp önerisi
│   ├── CopilotService.java             # Akademisyen asistanı
│   ├── SemanticCacheService.java       # Redis semantik önbellek
│   └── UnifiedAgentService.java        # Akışlı genel ajan
└── util/
    └── ModerationDecisionParser.java   # LLM çıktısı ayrıştırıcı
```

---

## 4. Konfigürasyon

### `application.yaml`

```yaml
spring.ai.ollama:
  base-url: http://ollama:11434
  chat.options:
    model: llama3.2:1b
    temperature: 0.0    # Deterministik çıktı (moderasyon için)
    top_p: 0.1
    num_predict: 128    # Kısa yanıt → düşük gecikme
    num_ctx: 1024
  embedding.options:
    model: nomic-embed-text

datasource: postgresql → club_db şeması
vector.store.path: data/vector-store.json
club.ingestion.enabled: true / force: true
```

### `HttpConfig` — Timeout Ayarları

LLM yanıtları uzun sürebildiğinden varsayılan HTTP istemcisi ezilmiştir:

```
Bağlantı timeout: 10 saniye
Okuma timeout: 10 dakika
```

### `AiConfig`

Tek satırlık `ChatClient` bean kaydı. Spring AI'nin builder pattern'ı kullanılır.

---

## 5. REST API

**Base path:** `POST /api/ai`  
**Kimlik doğrulama:** API Gateway'den gelen `X-Authenticated-User-Id` header'ı ile (JWT doğrulaması Gateway tarafında yapılır, bu servis header'a güvenir).

### `POST /api/ai/instructor-copilot`

Akademisyen asistanı. Gateway'den gelen `instructorId` ile `CopilotService.chatWithInstructor()` çağrılır.

### `POST /api/ai/student-assistant`

Öğrenci asistanı. Gelen mesaj Türkçe intent tespiti ile yönlendirilir:

| Tetikleyici kelimeler | Yönlendirme |
|---|---|
| `kulüp`, `topluluk`, `öneri` | `ClubRecommendationService.recommendClub()` |
| `ödev`, `teslim`, `proje`, `görev` | `AiAssistantService.chatWithStudent()` |
| Diğer | Sabit yönlendirme mesajı |

---

## 6. Servisler

### 6.1 `CopilotService` — Akademisyen Copilot

**Amaç:** Akademisyenin doğal dil talebiyle duyuru oluşturmasını sağlar (LLM'siz, kural tabanlı).

**Akış:**
1. `CourseServiceClient.getMyInstructorCourses()` ile akademisyenin dersleri Feign üzerinden çekilir.
2. Mesajdan ders kodu regex ile ayıklanır (`[A-Z]{2,}-\d{2,4}` deseni).
3. Ders kodu eşleşmezse tüm dersler arasında başlık tokenizasyonu ile fuzzy match yapılır.
4. Eşleşen ders için duyuru başlığı ve içeriği `buildAnnouncementTitle/Content()` metodları ile üretilir.
5. `CourseServiceClient.createAnnouncement()` ile duyuru `course-service`'e POST edilir.

**Öne çıkan teknikler:**
- Türkçe karaktere duyarlı normalizasyon (`normalizeText`)
- Kod normalizasyonu: boşluk ve noktalama temizleme (`normalizeCode`)
- Tokenizasyon + stopword benzeri filtreleme (≥3 karakter token)
- İptal/telafi tespiti için anahtar kelime eşleştirme

---

### 6.2 `AiAssistantService` — Öğrenci Ödev Asistanı

**Amaç:** Öğrencinin bekleyen ödevlerini sorgular ve Türkçe yanıt üretir (LLM'siz).

**Akış:**
1. `AssignmentServiceClient.getMyAssignments(studentId)` ile ödevler Feign üzerinden çekilir.
2. `isSubmitted()` → teslim edilmiş ödevler filtrelenir.
3. `isPastDue()` → süresi geçmiş ödevler filtrelenir.
4. Kalan bekleyen ödevler için insan okunabilir Türkçe cümle formatlanır.

**Not:** Bu servis LLM çağrısı yapmaz; veriler doğrudan `assignment-service`'ten Feign ile alınır ve template string ile yanıt oluşturulur.

---

### 6.3 `ClubRecommendationService` — RAG Tabanlı Kulüp Önerisi

**Amaç:** Öğrencinin mesajına göre en uygun kulüpleri vektör benzerliği ile önerir.

**Akış:**
1. Öğrenci mesajı `SimpleVectorStore.similaritySearch()` ile top-5 benzer belgeyi getirir.
2. Türkçe stopword listesi ile token filtreleme yapılır (~100+ kelime tanımlanmış).
3. AI anahtar kelime tespiti (`yapay`, `zeka`, `ml`, `llm` vb.) ayrıca kontrol edilir.
4. `CLUB_NAME_PATTERN` regex ile döküman içinden kulüp adları ayıklanır.
5. Tekrar eden kulüpler `HashSet` ile dedüplike edilir.
6. Sonuç Türkçe doğal dil yanıtı olarak formatlanır.

**RAG Vektör Deposu:**
- Tip: `SimpleVectorStore` (in-memory, JSON'a serialize)
- Dosya: `data/vector-store.json`
- Bean adı: `clubVectorStore` (`@Qualifier` ile inject edilir)

---

### 6.4 `ClubIngestionService` — Vektör Deposu ETL

**Amaç:** PostgreSQL'deki kulüp verisini uygulama başlangıcında vektör deposuna yükler.

**Akış (`@PostConstruct`):**
1. `club.ingestion.enabled` kontrolü — devre dışıysa atla.
2. `vector-store.json` dosyası var ve `force=false` ise yeniden ingestion yapma.
3. `JdbcTemplate` ile `clubs` tablosundan `id`, `name`, `about` çekilir.
4. Her kulüp şu semantik metne dönüştürülür:
   ```
   Kulüp Adı: {name}
   Kategori: {category}
   Açıklama: {description}
   Toplantı Günü: {meetingDay}
   ```
5. `vectorStore.accept(documents)` ile embedding oluşturulur ve depolanır.
6. `SimpleVectorStore.save(file)` ile JSON'a yazılır.

---

### 6.5 `AiModerationService` — İçerik Moderasyonu

**Amaç:** Forum gönderilerini siber zorbalık açısından sınıflandırır.

**LLM Prompt Tasarımı:**
```
System: Türkçe sosyal medya gönderileri için strict siber zorbalık sınıflandırıcısı.
        Tam olarak şu etiketlerden birini döndür: ZORBA veya TEMIZ.
        Hakaret, taciz, aşağılama, tehdit, küfür varsa → ZORBA.
        Özel kelimeler: pislik, dengesiz, salak, aptal, gerizekali → otomatik ZORBA.
        Diğer durum → TEMIZ.

User: TITLE: {başlık}\nCONTENT: {içerik}
```

**`ModerationDecisionParser`:** LLM çıktısından `ZORBA`/`TEMIZ` enum değerini regex ile güvenle ayıklar. `Optional.empty()` ile belirsiz yanıtları reddeder.

---

### 6.6 `SemanticCacheService` — Semantik Önbellekleme

**Amaç:** Benzer soruların tekrar LLM'e gitmemesi için Redis VectorStore tabanlı önbellek.

**Eşleşme eşiği:** `0.90` kosinüs benzerliği

**Okuma:**
```java
vectorStore.similaritySearch(query, topK=1, threshold=0.90, filter="type=='semantic_cache'")
→ metadata["cachedResponse"] döner
```

**Yazma:**
```java
new Document(userMessage, {type: "semantic_cache", cachedResponse: llmResponse})
vectorStore.add(document)
```

---

### 6.7 `UnifiedAgentService` — Akışlı Genel Ajan

**Amaç:** Semantik önbellekli, kelime kelime akışlı genel sohbet endpointi.

**Cache HIT akışı:**
- Önbellekteki yanıt `Flux.fromIterable(words).delayElements(50ms)` ile simüle edilmiş yazma efektiyle stream edilir.

**Cache MISS akışı:**
- `ChatClient.stream().content()` ile Ollama'dan gerçek zamanlı token akışı alınır.
- `StringBuffer` (thread-safe) ile tüm tokenlar biriktirilir.
- `doOnComplete()` ile akış bitince tam yanıt `SemanticCacheService.putCache()` ile önbelleğe alınır.

---

## 7. Asenkron Mesajlaşma (RabbitMQ)

### Topoloji

```
post-service (publisher)
    │
    ▼
Exchange: post.moderation.exchange (TopicExchange)
    │  routing-key: post.moderation.pending
    ▼
Queue: post.moderation.llm.queue (durable)
    │
    ▼
PostModerationListener (consumer, concurrency: 1-4)
    │
    ├─ AiModerationService.classify(title, content)
    │       → llama3.2:1b → ZORBA | TEMIZ
    │
    └─ PostServiceClient.applyModerationDecision(postId, decision)
            → post-service (Feign PUT /api/posts/{id}/moderation)
```

### `RabbitMQConfig` detayları
- Jackson2JsonMessageConverter: JSON serileştirme
- `DefaultClassMapper`: `PostModerationEvent` trusted package mapping
- `defaultRequeueRejected = false`: hatalı mesajlar DLQ'ya gitmez, atılır

---

## 8. Feign İstemcileri

| İstemci | Servis | Endpoint | Amaç |
|---|---|---|---|
| `AssignmentServiceClient` | `assignment-service` | `GET /api/assignments/my-assignments` | Öğrencinin ödevlerini getir |
| `CourseServiceClient` | `course-service` | `GET /api/courses/instructor/me/courses` | Akademisyenin derslerini getir |
| `CourseServiceClient` | `course-service` | `POST /api/courses/{id}/announcements` | Duyuru oluştur |
| `PostServiceClient` | `post-service` | `PUT /api/posts/{id}/moderation` | Moderasyon kararını uygula |

**Kimlik aktarımı:** Tüm Feign çağrıları `X-Authenticated-User-Id` header'ını iletir (Gateway'den gelen güvenilir kimlik).

**Servis keşfi:** Eureka üzerinden; Feign `name` parametresi kayıtlı servis adıdır, Spring Cloud LoadBalancer otomatik IP çözümler.

---

## 9. Ana Uygulama Anotasyonları

```java
@SpringBootApplication
@EnableDiscoveryClient   // Eureka'ya kaydol
@EnableFeignClients      // Feign proxy'lerini tara
@EnableRabbit            // RabbitMQ listener'larını etkinleştir
```

---

## 10. Veri Akışı Özeti

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway                              │
│              (JWT doğrulama + X-Authenticated-User-Id)          │
└─────────────────────────┬───────────────────────────────────────┘
                          │ HTTP
                          ▼
              ┌─────────────────────┐
              │   CopilotController  │   POST /api/ai/*
              └──────┬──────┬───────┘
                     │      │
          ┌──────────▼──┐ ┌─▼──────────────────────┐
          │CopilotService│ │  Student Assistant      │
          │(Akademisyen) │ │  Intent Router (TR NLP) │
          └──────┬───────┘ └──┬─────────────────────┘
                 │             │
       ┌─────────▼──┐    ┌────▼───────────────────┐
       │CourseService│    │AiAssistantService      │ ClubRecommendation
       │Client(Feign)│    │(Assignment Feign)      │ Service (RAG)
       └─────────────┘    └────────────────────────┘
                                                    │
                                         ┌──────────▼──────────┐
                                         │  SimpleVectorStore   │
                                         │  (vector-store.json) │
                                         │  ← ClubIngestionSvc  │
                                         │    (PostgreSQL ETL)  │
                                         └─────────────────────┘

RabbitMQ Akışı:
post-service → [MQ] → PostModerationListener
                           → AiModerationService (llama3.2:1b)
                           → PostServiceClient (Feign) → post-service

Semantik Cache:
UnifiedAgentService → SemanticCacheService → Redis VectorStore
                                           → (cache miss) Ollama LLM
```

---

## 11. Tasarım Kararları

| Karar | Gerekçe |
|---|---|
| LLM olarak `llama3.2:1b` seçimi | Düşük gecikme, düşük kaynak tüketimi; `num_predict=128` ile kısa yanıtlar zorunlu kılınmış |
| `temperature=0.0` + `top_p=0.1` | Moderasyon ve duyuru üretiminde deterministik sonuç gereksinimi |
| Moderasyonda LLM'siz copilot | Düzenli string işlemleri LLM çağrısından daha hızlı; `CopilotService` LLM kullanmıyor |
| Türkçe `YapayZekaOdevVerisi` DTO | LLM'in İngilizce teknik terim karışıklığını önlemek için Anti-Corruption Layer |
| `StringBuffer` (thread-safe) | Reaktif stream'lerde farklı thread'lerden gelen tokenların biriktirilmesi |
| `defaultRequeueRejected=false` | Hatalı moderation mesajlarının sonsuz döngüye girmesini önler |
| Feign inner record DTO | Clean Code; DTO sınıfları istemci dosyası içinde tutularak karmaşa azaltılmış |

---

## 12. Eksik / Geliştirilecek Alanlar

- `UnifiedAgentService` için controller endpoint yok (servis var ama expose edilmemiş).
- Semantic cache Redis store `@Qualifier` olmadan autowire ediliyor; `clubVectorStore` ile aynı bean'i paylaşıp paylaşmadığı dikkat gerektirir.
- `AiToolsConfig` içindeki `getAssignmentsTool` ve `createAnnouncementTool` bean'leri `CopilotController` tarafından aktif olarak kullanılmıyor (eski tool-calling yaklaşımının kalıntısı).
- `ClubIngestionService` sadece `name` ve `about` çekiyor; `category` ve `meetingDay` her zaman `"Belirtilmedi"` olarak dolduruluyor.