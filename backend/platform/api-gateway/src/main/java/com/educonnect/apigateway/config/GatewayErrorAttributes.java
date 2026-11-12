package com.educonnect.apigateway.config;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webflux.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Map;

@Component
public class GatewayErrorAttributes extends DefaultErrorAttributes {

    private static final Map<Integer, String> MESSAGES = Map.of(
            404, "Kayıt bulunamadı.",
            405, "Bu işlem bu adreste desteklenmiyor.",
            413, "Dosya boyutu izin verilen sınırı aşıyor.",
            502, "İlgili servisten geçersiz yanıt alındı.",
            503, "Servis şu an kullanılamıyor. Lütfen daha sonra tekrar deneyin.",
            504, "İlgili servis zamanında yanıt vermedi.");

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(request, options);
        if (isConnectionFailure(getError(request))) {
            attributes.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
            attributes.put("error", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        }
        int status = attributes.get("status") instanceof Integer value ? value : 500;
        String message = MESSAGES.getOrDefault(status,
                status >= 500 ? "Beklenmeyen bir hata oluştu." : "Geçersiz istek.");
        HttpStatus resolved = HttpStatus.resolve(status);
        attributes.put("errorCode", resolved != null ? resolved.name() : "HTTP_" + status);
        attributes.put("message", message);
        attributes.put("detail", message);
        return attributes;
    }

    private static boolean isConnectionFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ConnectException || current instanceof UnknownHostException) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
        }
        return false;
    }
}
