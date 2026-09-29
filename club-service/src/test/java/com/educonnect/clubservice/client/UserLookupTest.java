package com.educonnect.clubservice.client;

import com.educonnect.clubservice.dto.response.UserSummary;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserLookupTest {

    private final UserClient userClient = mock(UserClient.class);
    private final UserLookup userLookup = new UserLookup(userClient);

    @Test
    void usersById_shouldCallBatchOnceWithDistinctIds() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(userClient.getUsersByIds(List.of(first, second))).thenReturn(List.of(user(first), user(second)));

        assertThat(userLookup.usersById(Arrays.asList(first, second, first, null)))
                .containsOnlyKeys(first, second);
        verify(userClient).getUsersByIds(List.of(first, second));
    }

    @Test
    void usersById_whenNoIds_shouldNotCallUserService() {
        assertThat(userLookup.usersById(List.of())).isEmpty();
        verifyNoInteractions(userClient);
    }

    @Test
    void usersById_whenUserServiceFails_shouldReturnEmptyMap() {
        when(userClient.getUsersByIds(anyCollection())).thenThrow(new RuntimeException("down"));

        assertThat(userLookup.usersById(List.of(UUID.randomUUID()))).isEmpty();
    }

    private static UserSummary user(UUID id) {
        UserSummary user = new UserSummary();
        user.setId(id);
        user.setFirstName("Ad");
        user.setLastName("Soyad");
        return user;
    }
}
