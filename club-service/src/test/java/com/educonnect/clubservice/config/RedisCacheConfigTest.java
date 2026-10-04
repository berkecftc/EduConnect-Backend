package com.educonnect.clubservice.config;

import com.educonnect.clubservice.dto.response.MyClubMembershipDTO;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.common.web.cache.CacheValueSerializers;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheConfigTest {

    private final GenericJacksonJsonRedisSerializer serializer = CacheValueSerializers.typed();

    @Test
    void cachedMembershipsComeBackAsMembershipObjects() {
        MyClubMembershipDTO membership = new MyClubMembershipDTO();
        membership.setClubId(UUID.randomUUID());
        membership.setClubName("Satranç Kulübü");
        membership.setClubRole(ClubPosition.GENERAL_SECRETARY);
        membership.setActive(true);
        membership.setTermStartDate(Instant.parse("2026-09-01T07:00:00Z"));

        Object restored = serializer.deserialize(serializer.serialize(new ArrayList<>(List.of(membership))));

        MyClubMembershipDTO cached = (MyClubMembershipDTO) ((List<?>) restored).get(0);
        assertThat(cached.getClubId()).isEqualTo(membership.getClubId());
        assertThat(cached.getClubRole()).isEqualTo(ClubPosition.GENERAL_SECRETARY);
        assertThat(cached.getTermStartDate()).isEqualTo(membership.getTermStartDate());
        assertThat(cached.isActive()).isTrue();
    }
}
