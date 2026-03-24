# Gamification Service

EduConnect icin asenkron puanlama ve itibar servisi.

## Veritabani Semasi

- Kanonik sema: `gamification_db`
- Eski kurulumlarda kalan `gamification_schema` tablolari, `V2__migrate_to_gamification_db_schema.sql` ile `gamification_db` altina tasinir.

## Kapsam (Faz-1)

- RabbitMQ uzerinden event tuketimi (`gamification.exchange`)
- Idempotent puan isleme (`point_history` unique key)
- Optimistic locking ile yarismali guncelleme guvenligi (`user_reputation.version`)
- Gunluk streak reset scheduler (`Europe/Istanbul`)

## Event Sozlesmesi

Exchange: `gamification.exchange`

Routing key'ler:

- `gamification.post.published`
- `gamification.answer.accepted`
- `gamification.report.resolved`
- `gamification.user.login`

Payload sinifi: `GamificationEvent`

- `userId: UUID`
- `actionType: ActionType`
- `referenceId: String`
- `occurredAt: OffsetDateTime`

## Calistirma

```bash
cd /Users/berkeciftci/Desktop/EduConnect-Backend
mvn -pl gamification-service spring-boot:run
```

## Test

```bash
cd /Users/berkeciftci/Desktop/EduConnect-Backend
mvn -pl gamification-service -DskipTests=false test
```

## Faz-2 Backlog

- `PROFILE_COMPLETED` puan kurali
- Liderlik tablosu endpoint'i + Redis cache

