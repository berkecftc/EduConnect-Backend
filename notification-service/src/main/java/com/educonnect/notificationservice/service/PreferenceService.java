package com.educonnect.notificationservice.service;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.web.ApiException;
import com.educonnect.notificationservice.dto.response.PreferenceResponse;
import com.educonnect.notificationservice.model.NotificationPreference;
import com.educonnect.notificationservice.repository.NotificationPreferenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PreferenceService {

    private final NotificationPreferenceRepository repository;
    private final UnsubscribeTokens tokens;

    public PreferenceService(NotificationPreferenceRepository repository, UnsubscribeTokens tokens) {
        this.repository = repository;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Boolean> emailEnabled(Collection<UUID> userIds, NotificationCategory category) {
        Map<UUID, Boolean> enabled = new HashMap<>();
        userIds.forEach(id -> enabled.put(id, category.mandatory() || category.emailByDefault()));
        if (!category.mandatory()) {
            repository.findByUserIdInAndCategory(userIds, category)
                    .forEach(preference -> enabled.put(preference.getUserId(), preference.isEmailEnabled()));
        }
        return enabled;
    }

    @Transactional(readOnly = true)
    public List<PreferenceResponse> list(UUID userId) {
        Map<NotificationCategory, NotificationPreference> stored = repository.findByUserId(userId).stream()
                .collect(Collectors.toMap(NotificationPreference::getCategory, Function.identity()));
        return Arrays.stream(NotificationCategory.values())
                .map(category -> new PreferenceResponse(category, category.label(), category.mandatory(),
                        category.mandatory() || (stored.containsKey(category)
                                ? stored.get(category).isEmailEnabled()
                                : category.emailByDefault())))
                .toList();
    }

    @Transactional
    public PreferenceResponse update(UUID userId, NotificationCategory category, boolean emailEnabled) {
        if (category.mandatory()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_MANDATORY",
                    category.label() + " bildirimleri hizmet bildirimidir ve kapatılamaz.");
        }
        NotificationPreference preference = repository.findById(new NotificationPreference.Key(userId, category))
                .orElseGet(() -> new NotificationPreference(userId, category, emailEnabled));
        preference.update(emailEnabled);
        repository.save(preference);
        return new PreferenceResponse(category, category.label(), false, emailEnabled);
    }

    @Transactional
    public PreferenceResponse unsubscribe(String token) {
        UnsubscribeTokens.Subscription subscription = tokens.verify(token)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_UNSUBSCRIBE_TOKEN",
                        "Abonelikten çıkma bağlantısı geçersiz."));
        return update(subscription.userId(), subscription.category(), false);
    }
}
