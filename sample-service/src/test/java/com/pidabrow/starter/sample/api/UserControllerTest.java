package com.pidabrow.starter.sample.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pidabrow.starter.common.tenant.TenantContext;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.data.entity.Tenant;
import com.pidabrow.starter.sample.MicroserviceStarterApplication;
import com.pidabrow.starter.sample.api.dto.CreateUserRequest;
import com.pidabrow.starter.sample.api.dto.UpdateUserRequest;
import com.pidabrow.starter.sample.api.dto.UserPreferencesDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer test for UserController.
 * Verifies full CRUD REST API endpoints under /api/v1/users.
 */
@SpringBootTest(classes = MicroserviceStarterApplication.class)
@AutoConfigureMockMvc
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_db")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenantAId;
    private UUID tenantBId;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("DELETE FROM notification_requests").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
            entityManager.flush();
            return null;
        });
        transactionTemplate.execute(status -> {
            Tenant tenantA = Tenant.create("Tenant A");
            tenantAId = tenantA.getId();
            entityManager.persist(tenantA);

            Tenant tenantB = Tenant.create("Tenant B");
            tenantBId = tenantB.getId();
            entityManager.persist(tenantB);

            entityManager.flush();
            entityManager.clear();
            return null;
        });
    }

    @Nested
    @DisplayName("POST /api/v1/users")
    class CreateUser {

        @Test
        @DisplayName("Should create user successfully via POST /api/v1/users")
        void should_create_user_successfully_via_post_api_v1_users() throws Exception {
            CreateUserRequest request = new CreateUserRequest(
                    "test@example.com",
                    "+1234567890",
                    "John",
                    "Doe",
                    new UserPreferencesDto(true, true)
            );

            mockMvc.perform(post("/api/v1/users")
                            .header("X-Tenant-Id", tenantAId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.tenantId").value(tenantAId.toString()))
                    .andExpect(jsonPath("$.email").value("test@example.com"))
                    .andExpect(jsonPath("$.phoneNumber").value("+1234567890"))
                    .andExpect(jsonPath("$.firstName").value("John"))
                    .andExpect(jsonPath("$.lastName").value("Doe"))
                    .andExpect(jsonPath("$.preferences.emailEnabled").value(true))
                    .andExpect(jsonPath("$.preferences.smsEnabled").value(true));
        }

        @Test
        @DisplayName("Should return 400 when tenant header is missing")
        void should_return_400_when_tenant_header_is_missing() throws Exception {
            CreateUserRequest request = new CreateUserRequest(
                    "test@example.com",
                    "+1234567890",
                    "John",
                    "Doe",
                    new UserPreferencesDto(true, true)
            );

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when request validation fails")
        void should_return_400_when_request_validation_fails() throws Exception {
            CreateUserRequest invalidRequest = new CreateUserRequest(
                    "", // invalid email
                    "", // invalid phone
                    "", // invalid firstName
                    "", // invalid lastName
                    null // invalid preferences
            );

            mockMvc.perform(post("/api/v1/users")
                            .header("X-Tenant-Id", tenantAId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users")
    class ListUsers {

        @Test
        @DisplayName("Should return empty list when no users exist")
        void should_return_empty_list_when_no_users_exist() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header("X-Tenant-Id", tenantAId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Should return users only for current tenant")
        void should_return_users_only_for_current_tenant() throws Exception {
            // Create user for tenant A
            createUserViaApi(tenantAId, "tenantA@example.com", "Alice", "Smith");
            // Create user for tenant B
            createUserViaApi(tenantBId, "tenantB@example.com", "Bob", "Jones");

            // List users for tenant A - should see only Alice
            mockMvc.perform(get("/api/v1/users")
                            .header("X-Tenant-Id", tenantAId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].email").value("tenantA@example.com"))
                    .andExpect(jsonPath("$[0].firstName").value("Alice"));

            // List users for tenant B - should see only Bob
            mockMvc.perform(get("/api/v1/users")
                            .header("X-Tenant-Id", tenantBId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].email").value("tenantB@example.com"))
                    .andExpect(jsonPath("$[0].firstName").value("Bob"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/{id}")
    class GetUser {

        @Test
        @DisplayName("Should return user when found")
        void should_return_user_when_found() throws Exception {
            String userId = createUserViaApi(tenantAId, "john@example.com", "John", "Doe");

            mockMvc.perform(get("/api/v1/users/{id}", userId)
                            .header("X-Tenant-Id", tenantAId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(userId))
                    .andExpect(jsonPath("$.email").value("john@example.com"))
                    .andExpect(jsonPath("$.firstName").value("John"))
                    .andExpect(jsonPath("$.lastName").value("Doe"));
        }

        @Test
        @DisplayName("Should return 404 when user not found")
        void should_return_404_when_user_not_found() throws Exception {
            mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID())
                            .header("X-Tenant-Id", tenantAId.toString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 404 when accessing user from another tenant")
        void should_return_404_when_accessing_user_from_another_tenant() throws Exception {
            // Create user for tenant A
            String userId = createUserViaApi(tenantAId, "secret@example.com", "Secret", "User");

            // Try to GET with tenant B - should not see tenant A's user
            mockMvc.perform(get("/api/v1/users/{id}", userId)
                            .header("X-Tenant-Id", tenantBId.toString()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/users/{id}")
    class UpdateUser {

        @Test
        @DisplayName("Should update user successfully")
        void should_update_user_successfully() throws Exception {
            String userId = createUserViaApi(tenantAId, "old@example.com", "OldFirst", "OldLast");

            UpdateUserRequest updateRequest = new UpdateUserRequest(
                    "new@example.com",
                    "+9999999999",
                    "NewFirst",
                    "NewLast",
                    new UserPreferencesDto(false, true)
            );

            mockMvc.perform(put("/api/v1/users/{id}", userId)
                            .header("X-Tenant-Id", tenantAId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(userId))
                    .andExpect(jsonPath("$.email").value("new@example.com"))
                    .andExpect(jsonPath("$.phoneNumber").value("+9999999999"))
                    .andExpect(jsonPath("$.firstName").value("NewFirst"))
                    .andExpect(jsonPath("$.lastName").value("NewLast"))
                    .andExpect(jsonPath("$.preferences.emailEnabled").value(false))
                    .andExpect(jsonPath("$.preferences.smsEnabled").value(true));
        }

        @Test
        @DisplayName("Should return 404 when updating user from another tenant")
        void should_return_404_when_updating_user_from_another_tenant() throws Exception {
            // Create user for tenant A
            String userId = createUserViaApi(tenantAId, "tenantA@example.com", "TenantA", "User");

            UpdateUserRequest updateRequest = new UpdateUserRequest(
                    "hacked@example.com", null, null, null, null
            );

            // Try to PUT with tenant B
            mockMvc.perform(put("/api/v1/users/{id}", userId)
                            .header("X-Tenant-Id", tenantBId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 404 when updating non-existent user")
        void should_return_404_when_updating_non_existent_user() throws Exception {
            UpdateUserRequest updateRequest = new UpdateUserRequest(
                    "new@example.com", null, null, null, null
            );

            mockMvc.perform(put("/api/v1/users/{id}", UUID.randomUUID())
                            .header("X-Tenant-Id", tenantAId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/{id}")
    class DeleteUser {

        @Test
        @DisplayName("Should delete user successfully")
        void should_delete_user_successfully() throws Exception {
            String userId = createUserViaApi(tenantAId, "delete@example.com", "Delete", "Me");

            mockMvc.perform(delete("/api/v1/users/{id}", userId)
                            .header("X-Tenant-Id", tenantAId.toString()))
                    .andExpect(status().isNoContent());

            // Verify user is gone
            mockMvc.perform(get("/api/v1/users/{id}", userId)
                            .header("X-Tenant-Id", tenantAId.toString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 404 when deleting user from another tenant")
        void should_return_404_when_deleting_user_from_another_tenant() throws Exception {
            // Create user for tenant A
            String userId = createUserViaApi(tenantAId, "tenantA@example.com", "TenantA", "User");

            // Try to DELETE with tenant B
            mockMvc.perform(delete("/api/v1/users/{id}", userId)
                            .header("X-Tenant-Id", tenantBId.toString()))
                    .andExpect(status().isNotFound());

            // Verify user still exists for tenant A
            mockMvc.perform(get("/api/v1/users/{id}", userId)
                            .header("X-Tenant-Id", tenantAId.toString()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent user")
        void should_return_404_when_deleting_non_existent_user() throws Exception {
            mockMvc.perform(delete("/api/v1/users/{id}", UUID.randomUUID())
                            .header("X-Tenant-Id", tenantAId.toString()))
                    .andExpect(status().isNotFound());
        }
    }

    /**
     * Helper: creates a user via POST and returns the user ID.
     */
    private String createUserViaApi(UUID tenantId, String email, String firstName, String lastName) throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                email, "+1234567890", firstName, lastName,
                new UserPreferencesDto(true, true)
        );

        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
