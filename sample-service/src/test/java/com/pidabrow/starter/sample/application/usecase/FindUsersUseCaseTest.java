package com.pidabrow.starter.sample.application.usecase;

import com.pidabrow.starter.common.tenant.TenantContext;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.sample.application.port.out.FindUserPort;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FindUsersUseCase}.
 *
 * Tests business logic: listing users, finding by ID, tenant context enforcement.
 * Ports are mocked — no Spring context or database needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FindUsersUseCase unit tests")
class FindUsersUseCaseTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private FindUserPort findUserPort;

    private FindUsersUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new FindUsersUseCase(findUserPort);
        TenantContextHolder.setContext(TenantContext.of(TENANT_ID));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should return all users for current tenant")
    void should_return_all_users_for_current_tenant() {
        User user1 = new User(UUID.randomUUID(), TENANT_ID, "a@example.com", "+111", "A", "User", new UserPreferences(true, false), null);
        User user2 = new User(UUID.randomUUID(), TENANT_ID, "b@example.com", "+222", "B", "User", new UserPreferences(false, true), null);
        when(findUserPort.findAll()).thenReturn(List.of(user1, user2));

        List<User> result = useCase.findAll();

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(user1, user2);
    }

    @Test
    @DisplayName("Should return empty list when no users exist for tenant")
    void should_return_empty_list_when_no_users_exist() {
        when(findUserPort.findAll()).thenReturn(List.of());

        List<User> result = useCase.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return user when found by ID")
    void should_return_user_when_found_by_id() {
        User user = new User(USER_ID, TENANT_ID, "test@example.com", "+123", "John", "Doe", new UserPreferences(true, true), null);
        when(findUserPort.findById(USER_ID)).thenReturn(Optional.of(user));

        User result = useCase.findById(USER_ID);

        assertThat(result).isEqualTo(user);
    }

    @Test
    @DisplayName("Should throw NoSuchElementException when user not found by ID")
    void should_throw_no_such_element_when_user_not_found_by_id() {
        when(findUserPort.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.findById(USER_ID))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when tenant context is not set for findAll")
    void should_throw_illegal_state_when_tenant_context_not_set_for_find_all() {
        TenantContextHolder.clearContext();

        assertThatThrownBy(() -> useCase.findAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context must be set");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when tenant context is not set for findById")
    void should_throw_illegal_state_when_tenant_context_not_set_for_find_by_id() {
        TenantContextHolder.clearContext();

        assertThatThrownBy(() -> useCase.findById(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context must be set");
    }
}

