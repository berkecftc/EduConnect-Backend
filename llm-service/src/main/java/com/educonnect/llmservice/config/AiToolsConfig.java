package com.educonnect.llmservice.config;

import com.educonnect.llmservice.client.CourseServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class AiToolsConfig {

    private final CourseServiceClient courseServiceClient;

    public AiToolsConfig(CourseServiceClient courseServiceClient) {
        this.courseServiceClient = courseServiceClient;
    }

    // 1. LLM'in dolduracağı girdi (Request) parametreleri
    public record AnnouncementToolRequest(String instructorId, String courseId, String title, String content) {}

    // 2. LLM'e döneceğimiz sonuç (Response)
    public record AnnouncementToolResponse(boolean success, String message) {}

    // 3. Asıl Fonksiyon (LLM bu Bean'i çağıracak)
    @Bean
    @Description("""
        Bu araç (tool), bir akademisyenin verdiği derse (courseId) yeni bir duyuru (announcement) eklemek için kullanılır.
        instructorId sana kullanıcı mesajında bağlam (context) olarak verilecektir.
        Duyurunun başlığını (title) ve profesyonel/nazik içeriğini (content) akademisyenin talebine göre sen üretmelisin.
        Eğer kullanıcı ders ID'sini (courseId) belirtmemişse, bu aracı çağırmadan önce kullanıcıya 'Hangi ders için duyuru yapmamı istersiniz?' diye sor.
        """)
    public Function<AnnouncementToolRequest, AnnouncementToolResponse> createAnnouncementTool() {
        return request -> {
            try {
                // LLM'in ürettiği verilerle CourseServiceClient'ı (OpenFeign) çağırıyoruz
                CourseServiceClient.AnnouncementRequest payload =
                        new CourseServiceClient.AnnouncementRequest(request.title(), request.content());

                courseServiceClient.createAnnouncement(request.instructorId(), request.courseId(), payload);

                return new AnnouncementToolResponse(true, "Duyuru course-service üzerinden başarıyla yayınlandı.");
            } catch (Exception e) {
                // Devre kesici (Circuit Breaker) veya ağ hatası olursa LLM'e bilgi veriyoruz
                return new AnnouncementToolResponse(false, "Sistem hatası nedeniyle duyuru yayınlanamadı: " + e.getMessage());
            }
        };
    }
}