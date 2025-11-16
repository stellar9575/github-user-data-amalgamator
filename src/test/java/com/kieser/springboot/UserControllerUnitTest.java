package com.kieser.springboot;

import com.kieser.record.GitRepoData;
import com.kieser.record.GitUserData;
import com.kieser.record.UserData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class UserControllerSpringTest {

    @MockBean
    private LRUCache<String, UserData> mockCache;

    @MockBean
    private WebClient.Builder mockWebClientBuilder;

    @MockBean
    private WebClient.ResponseSpec mockResponseSpec;

    @org.junit.jupiter.api.BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        var mockWebClient = mock(WebClient.class);
        var mockRequestSpec = mock(WebClient.RequestHeadersUriSpec.class);
        var mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);

        // WebClient builder returns mocked WebClient
        when(mockWebClientBuilder.build()).thenReturn(mockWebClient);

        // Mock the GET -> URI -> Header -> Retrieve -> bodyToMono chain
        when(mockWebClient.get()).thenReturn(mockRequestSpec);
        when(mockRequestSpec.uri(any(String.class))).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.header(any(), any())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
    }

    @Test
    void username_invalidUsername_returnsBadRequest() {
        var userController = new UserController("", mockCache, mockWebClientBuilder);

        ResponseEntity<?> response = userController.username("invalid@name!");
        assertEquals(400, response.getStatusCode().value());
        Assertions.assertNotNull(response.getBody());
        assertTrue(response.getBody().toString().contains("Invalid username"));
    }

    @Test
    void username_cacheHit_returnsCachedData() {
        var userController = new UserController("", mockCache, mockWebClientBuilder);

        UserData cachedUser = new UserData("octocat", "The Octocat", "", "", "", "", "", new GitRepoData[]{});
        when(mockCache.containsKey("octocat")).thenReturn(true);
        when(mockCache.get("octocat")).thenReturn(cachedUser);

        ResponseEntity<?> response = userController.username("octocat");

        verify(mockCache, times(1)).get("octocat");
        assertEquals(200, response.getStatusCode().value());
        assertEquals(cachedUser, response.getBody());
    }

    @Test
    void username_cacheMiss_fetchesFromGitHub() {
        var userController = new UserController("", mockCache, mockWebClientBuilder);

        when(mockCache.containsKey("octocat")).thenReturn(false);

        // Mock GitUserData response
        GitUserData gitUserData = new GitUserData("The Octocat", "avatar", "loc", "email", "url", "created");
        when(mockResponseSpec.bodyToMono(GitUserData.class)).thenReturn(Mono.just(gitUserData));

        // Mock GitRepoData array response
        GitRepoData[] repos = new GitRepoData[]{};
        when(mockResponseSpec.bodyToMono(GitRepoData[].class)).thenReturn(Mono.just(repos));

        ResponseEntity<?> response = userController.username("octocat");

        verify(mockCache, times(1)).put(eq("octocat"), any(UserData.class));
        assertEquals(200, response.getStatusCode().value());

        UserData userData = (UserData) response.getBody();
        Assertions.assertNotNull(userData);
        // test fields
        assertEquals("octocat", userData.user_name());
        assertEquals("The Octocat", userData.display_name());
        assertEquals("avatar", userData.avatar());
        assertEquals("loc", userData.geo_location());
        assertEquals("email", userData.email());
        assertEquals("url", userData.url());
        assertEquals("created", userData.created_at());
    }

    @Test
    void username_githubNotFound_returnsInternalServerError() {
        var userController = new UserController("", mockCache, mockWebClientBuilder);

        when(mockResponseSpec.bodyToMono(GitUserData.class))
                .thenThrow(WebClientResponseException.NotFound.create(404, "", null, null, null));

        ResponseEntity<?> response = userController.username("username-does-not-exist");

        assertEquals(500, response.getStatusCode().value());
        Assertions.assertNotNull(response.getBody());
        assertTrue(response.getBody().toString().contains("Failed to get response from github"));
    }





}
