package com.tatzains.doodle_hometask;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tatzains.doodle_hometask.dto.request.CreateSlotRequest;
import com.tatzains.doodle_hometask.dto.request.CreateUserRequest;
import com.tatzains.doodle_hometask.dto.response.SlotResponse;
import com.tatzains.doodle_hometask.dto.response.UserResponse;
import com.tatzains.doodle_hometask.web.CurrentUserResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared base for controller-level integration tests: a real Postgres via Testcontainers
 * (so the GiST constraint and Flyway migrations actually run), MockMvc for HTTP, and
 * request/response helpers reused across test classes.
 *
 * <p>Uses the Testcontainers "singleton container" pattern rather than {@code @Container},
 * so the container and cached ApplicationContext are reused across all subclasses.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected UserResponse createUser(String name, String email) throws Exception {
        String response = mockMvc.perform(postJson("/users", new CreateUserRequest(name, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, UserResponse.class);
    }

    protected SlotResponse createSlot(UUID ownerId, Instant start, Instant end) throws Exception {
        String response = mockMvc.perform(asUser(ownerId, postJson("/slots", new CreateSlotRequest(start, end))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, SlotResponse.class);
    }

    protected MockHttpServletRequestBuilder asUser(UUID userId, MockHttpServletRequestBuilder request) {
        return request.header(CurrentUserResolver.USER_ID_HEADER, userId.toString());
    }

    protected MockHttpServletRequestBuilder postJson(String url, Object body) throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    protected MockHttpServletRequestBuilder patchJson(String url, Object body) throws Exception {
        return patch(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    /**
     * Extracts the {@code content} array out of a {@code PagedModel} JSON response body
     * (the shape every paginated listing endpoint returns) into a plain list.
     */
    protected <T> List<T> readPagedContent(String responseJson, Class<T> itemType) {
        JsonNode content = objectMapper.readTree(responseJson).get("content");
        return objectMapper.readValue(
                objectMapper.writeValueAsString(content),
                objectMapper.getTypeFactory().constructCollectionType(List.class, itemType));
    }

    /**
     * A future instant aligned to the 15-minute granularity step required by
     * {@code @ValidTimeRange}, comfortably past the minimum booking buffer.
     */
    protected static Instant alignedFutureStart(Duration aheadOfNow) {
        long granularitySeconds = Duration.ofMinutes(15).getSeconds();
        long candidateEpochSeconds = Instant.now().plus(aheadOfNow).getEpochSecond();
        long alignedEpochSeconds = ((candidateEpochSeconds / granularitySeconds) + 1) * granularitySeconds;
        return Instant.ofEpochSecond(alignedEpochSeconds);
    }
}
