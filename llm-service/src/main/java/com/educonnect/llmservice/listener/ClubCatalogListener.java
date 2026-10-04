package com.educonnect.llmservice.listener;

import com.educonnect.llmservice.config.RabbitMQConfig;
import com.educonnect.llmservice.dto.event.ClubChangedMessage;
import com.educonnect.llmservice.service.ClubCatalogIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ClubCatalogListener {

    private static final Logger log = LoggerFactory.getLogger(ClubCatalogListener.class);

    private final ClubCatalogIndex catalogIndex;

    public ClubCatalogListener(ClubCatalogIndex catalogIndex) {
        this.catalogIndex = catalogIndex;
    }

    @RabbitListener(queues = RabbitMQConfig.CLUB_CATALOG_QUEUE)
    public void onClubChanged(ClubChangedMessage message) {
        log.debug("Club changed, refreshing the assistant catalog: clubId={}", message == null ? null : message.clubId());
        catalogIndex.requestRefresh();
    }
}
