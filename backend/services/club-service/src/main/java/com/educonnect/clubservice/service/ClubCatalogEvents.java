package com.educonnect.clubservice.service;

import com.educonnect.clubservice.config.ClubRabbitMQConfig;
import com.educonnect.clubservice.dto.message.ClubUpdateMessage;
import com.educonnect.clubservice.model.Club;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;

@Component
public class ClubCatalogEvents {

    public static final String ROUTING_KEY_CATALOG_CHANGED = "club.catalog.changed";

    private final OutboxPublisher outboxPublisher;

    public ClubCatalogEvents(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    public void changed(Club club) {
        outboxPublisher.publish(ClubRabbitMQConfig.CLUB_EXCHANGE_NAME, ROUTING_KEY_CATALOG_CHANGED,
                new ClubUpdateMessage(club.getId(), club.getName(), club.getLogoUrl()));
    }
}
