package com.pidabrow.starter.sample.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pidabrow.starter.data.entity.Tenant;
import com.pidabrow.starter.sample.MicroserviceStarterApplication;
import com.pidabrow.starter.sample.api.dto.CreateUserRequest;
import com.pidabrow.starter.sample.api.dto.RegisterUserRequest;
import com.pidabrow.starter.sample.api.dto.UserPreferencesDto;
import com.pidabrow.starter.testing.AbstractIntegrationTest;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that error responses comply with RFC 7807 (Problem Details).
 * Covers ADR-011 points 1, 2, 3, 5.
 */
@SpringBootTest(classes = MicroserviceStarterApplication.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProblemDetailErrorHandlingTest extends AbstractIntegrationTest {

    private static final MediaType APPLICATION_PROBLEM_JSON =
            MediaType.valueOf("application/problem+json");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            entityManager.createNativeQuery("DELETE FROM notification_requests").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
            entityManager.flush();
            return null;
        });
        tx.execute(status -> {
            Tenant tenant = Tenant.create("Test Tenant");
            tenantId = tenant.getId();
            entityManager.persist(tenant);
            entityManager.flush();
            entityManager.clear();
            return null;
        });
    }

    @Nested
    @DisplayName("Validation errors (MethodArgumentNotValidException)")
    class ValidationErrors {

        @Test
        @DisplayName("Should return RFC 7807 with fieldErrors for invalid request body")
        void should_return_rfc7807_with_field_errors() throws Exception {
            CreateUserRequest invalid = new CreateUserRequest(
                    "", "", "", "", null
            );

            mockMvc.perform(post("/api/v1/users")
                            .header("X-Tenant-Id", tenantId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title", is("Validation Error")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.detail", is("Validation failed")))
                    .andExpect(jsonPath("$.fieldErrors").isMap())
                    .andExpect(jsonPath("$.fieldErrors", hasKey("email")))
                    .andExpect(jsonPath("$.fieldErrors", hasKey("firstName")));
        }
    }

    @Nested
    @DisplayName("Resource not found (NoSuchElementException)")
    class NotFound {

        @Test
        @DisplayName("Should return RFC 7807 for non-existent resource")
        void should_return_rfc7807_for_not_found() throws Exception {
            mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID())
                            .header("X-Tenant-Id", tenantId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title", is("Resource Not Found")))
                    .andExpect(jsonPath("$.status", is(404)));
        }
    }

    @Nested
    @DisplayName("User already exists (UserAlreadyExistsException → 409)")
    class Conflict {

        @Test
        @DisplayName("Should return RFC 7807 with 409 for duplicate registration")
        void should_return_rfc7807_for_duplicate_registration() throws Exception {
            RegisterUserRequest request = new RegisterUserRequest(
                    "duplicate@example.com",
                    "SecurePassword123!",
                    "Alice",
                    "Wonderland"
            );

            // First registration succeeds
            mockMvc.perform(post("/api/v1/users/register")
                            .header("X-Tenant-Id", tenantId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // Second registration returns RFC 7807 conflict
            mockMvc.perform(post("/api/v1/users/register")
                            .header("X-Tenant-Id", tenantId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title", is("User Already Exists")))
                    .andExpect(jsonPath("$.status", is(409)))
                    .andExpect(jsonPath("$.detail").isString());
        }
    }
}
