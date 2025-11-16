package com.kieser.springboot;

import com.kieser.record.GitRepoData;
import com.kieser.record.GitUserData;
import com.kieser.record.UserData;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestController
public class UserController {

	@Configuration
	public static class AppConfig {

		@Bean
		public LRUCache<String, UserData> lruCache(@Value("${lru.cache.size:1000}") int cacheSize) {
			return new LRUCache<>(cacheSize);
		}
	}

	private final Logger logger = LoggerFactory.getLogger(UserController.class);
	private final String apiAccessToken;
	private final String GITHUB_BASE_URL = "https://api.github.com/users/";
	private final LRUCache<String, UserData> lruCache;
	private final WebClient webClient;

	// initialize the controller and read in properties from environment variables
	public UserController(
			@Value("${api.access.token:}") String apiAccessToken,
			@Lazy LRUCache<String, UserData> lruCache,
			WebClient.Builder webClientBuilder) {
		logger.info("{}access token configured", apiAccessToken.isEmpty() ? "no " : "");

		this.apiAccessToken = apiAccessToken;
		this.lruCache = lruCache;
		this.webClient = webClientBuilder.build();
	}

	/**
	 * Retrieves information for a specific user by username.
	 *
	 * <p>This endpoint accepts a {@code username} as a path variable and returns
	 * user data and a list of the user's repos fetched from external github.com
	 * endpoints. The username must contain only the following characters:
	 * letters (a–z, A–Z), digits (0–9), dash (-), and must not exceed 39 chars.
	 * Once requested, the service caches that user data for fast subsequent
	 * retrieval. The cache size is configurable (lru.cache.size) and defaults
	 * to 1000. If a github api access token is configured at runtime
	 * (api.access.token), the service will use that token for all calls to
	 * github.
	 *
	 * <p>If the username contains invalid characters, a {@code 400 Bad Request}
	 * response is returned.
	 *
	 * @param username the username of the user to fetch; must match the pattern
	 *                 {@code [a-zA-Z0-9-]+}
	 * @return a {@link ResponseEntity} containing user data or an error response
	 */
	@GetMapping("/users/{username}")
	public ResponseEntity<?> username(@PathVariable String username) {
		// validate username
		if (!this.isValidGithubUsername(username)) {
			logger.debug("bad username received");
			return ResponseEntity
					.badRequest()
					.body("Invalid username: only letters, numbers, and - allowed. Must not exceed 39 chars");
		}
		logger.info("username received - {}", username);

		// check if user data is already in cache
		if (lruCache.containsKey(username)) {
			logger.info("found {} in cache; skipping call to github", username);
			return ResponseEntity.ok(lruCache.get(username));
		}

		// if not, reach out to GH servers for data
		final GitUserData gitUserData;
		final GitRepoData[] gitRepoData;
		try {
			gitUserData = this.requestData(GITHUB_BASE_URL + username, apiAccessToken, GitUserData.class);
			gitRepoData = this.requestData(GITHUB_BASE_URL + username + "/repos", apiAccessToken, GitRepoData[].class);
		} catch (WebClientResponseException e) {
			logger.error("Failed to get response from github - {}", e.getMessage());
			return ResponseEntity.internalServerError().body("Failed to get response from github - " + e.getStatusText());
		} catch (Exception e) {
			logger.error("unknown error - {}", e.getMessage());
			return ResponseEntity.internalServerError().body("unknown error");
		}

		// combine data response from GH into expected format
		final UserData userData = new UserData(
				username,
				gitUserData.name(),
				gitUserData.avatar_url(),
				gitUserData.location(),
				gitUserData.email(),
				gitUserData.url(),
				gitUserData.created_at(),
				gitRepoData);

		// put the username in the cache
		lruCache.put(username, userData);

		return ResponseEntity.ok(userData);
	}

	// get data from an endpoint given an optional access token
	private <T> T requestData(String url, @Nullable String accessToken, Class<T> responseType) {
			WebClient.RequestHeadersSpec<?> request = webClient.get().uri(url);
			if (StringUtils.isNotEmpty(accessToken)) {
				request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
			}
			return request.retrieve().bodyToMono(responseType).block();
	}

	// only allow alphanumeric and dashes of up to 39 chars
	private boolean isValidGithubUsername(String input) {
		return !input.isEmpty() && input.length() <= 39 && input.matches("[a-zA-Z0-9-]+");
	}

}
