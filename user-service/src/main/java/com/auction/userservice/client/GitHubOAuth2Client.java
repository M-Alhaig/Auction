package com.auction.userservice.client;

import com.auction.userservice.dto.oauth2.OAuth2UserInfo;
import com.auction.userservice.exceptions.OAuth2AuthenticationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client for GitHub OAuth2 token exchange and user info retrieval.
 *
 * <p>Flow:
 * <ol>
 *   <li>Exchange authorization code for access token</li>
 *   <li>Fetch user info from GitHub's /user API</li>
 *   <li>If email is private, fetch from /user/emails endpoint</li>
 * </ol>
 *
 * <p>Unlike Google, GitHub doesn't provide an ID token, so we must call the user API.
 */
@Slf4j
@Component
public class GitHubOAuth2Client {

  private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
  private static final String USER_URL = "https://api.github.com/user";
  private static final String EMAILS_URL = "https://api.github.com/user/emails";

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String clientId;
  private final String clientSecret;

  public GitHubOAuth2Client(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      @Value("${spring.security.oauth2.client.registration.github.client-id}") String clientId,
      @Value("${spring.security.oauth2.client.registration.github.client-secret}") String clientSecret) {
    this.restClient = restClientBuilder.build();
    this.objectMapper = objectMapper;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  /**
   * Exchange authorization code for user info.
   *
   * @param code        the authorization code from GitHub callback
   * @param redirectUri the redirect URI used in the OAuth flow
   * @return normalized user info from GitHub
   * @throws OAuth2AuthenticationException if code exchange or user info fetch fails
   */
  public OAuth2UserInfo exchangeCodeForUserInfo(String code, String redirectUri) {
    log.debug("Exchanging GitHub authorization code for tokens");

    try {
      String accessToken = exchangeCodeForToken(code, redirectUri);
      return fetchUserInfo(accessToken);
    } catch (OAuth2AuthenticationException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to authenticate with GitHub", e);
      throw new OAuth2AuthenticationException("Failed to authenticate with GitHub", e);
    }
  }

  private String exchangeCodeForToken(String code, String redirectUri) {
    try {
      MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
      formData.add("code", code);
      formData.add("client_id", clientId);
      formData.add("client_secret", clientSecret);
      formData.add("redirect_uri", redirectUri);

      String response = restClient.post()
          .uri(TOKEN_URL)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .accept(MediaType.APPLICATION_JSON)
          .body(formData)
          .retrieve()
          .body(String.class);

      JsonNode tokenResponse = objectMapper.readTree(response);

      if (tokenResponse.has("error")) {
        String error = tokenResponse.get("error").asText();
        String description = tokenResponse.has("error_description")
            ? tokenResponse.get("error_description").asText()
            : "Unknown error";
        log.warn("GitHub token exchange failed: {} - {}", error, description);
        throw new OAuth2AuthenticationException("GitHub authentication failed: " + description);
      }

      return tokenResponse.get("access_token").asText();

    } catch (RestClientException e) {
      log.error("Failed to exchange GitHub authorization code", e);
      throw new OAuth2AuthenticationException("Failed to communicate with GitHub", e);
    } catch (OAuth2AuthenticationException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to parse GitHub token response", e);
      throw new OAuth2AuthenticationException("Failed to parse GitHub response", e);
    }
  }

  private OAuth2UserInfo fetchUserInfo(String accessToken) {
    try {
      String userResponse = restClient.get()
          .uri(USER_URL)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
          .retrieve()
          .body(String.class);

      JsonNode userJson = objectMapper.readTree(userResponse);

      String providerId = String.valueOf(userJson.get("id").asLong());
      String email = userJson.has("email") && !userJson.get("email").isNull()
          ? userJson.get("email").asText()
          : null;
      String name = userJson.has("name") && !userJson.get("name").isNull()
          ? userJson.get("name").asText()
          : userJson.get("login").asText();
      String avatarUrl = userJson.has("avatar_url") ? userJson.get("avatar_url").asText() : null;

      // If email is private, fetch from emails endpoint
      if (email == null) {
        email = fetchPrimaryEmail(accessToken);
      }

      if (email == null) {
        throw new OAuth2AuthenticationException(
            "GitHub did not provide email. Please make your email public or grant email permission.");
      }

      log.debug("Fetched GitHub user info: providerId={}, email={}", providerId, email);
      // GitHub emails from /user/emails are verified
      return new OAuth2UserInfo(providerId, email, name, avatarUrl, true);

    } catch (RestClientException e) {
      log.error("Failed to fetch GitHub user info", e);
      throw new OAuth2AuthenticationException("Failed to fetch GitHub user info", e);
    } catch (OAuth2AuthenticationException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to parse GitHub user info", e);
      throw new OAuth2AuthenticationException("Failed to parse GitHub user info", e);
    }
  }

  private String fetchPrimaryEmail(String accessToken) {
    try {
      String emailsResponse = restClient.get()
          .uri(EMAILS_URL)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
          .retrieve()
          .body(String.class);

      JsonNode emailsJson = objectMapper.readTree(emailsResponse);

      // Find primary verified email
      for (JsonNode emailNode : emailsJson) {
        if (emailNode.get("primary").asBoolean() && emailNode.get("verified").asBoolean()) {
          return emailNode.get("email").asText();
        }
      }

      // Fallback: any verified email
      for (JsonNode emailNode : emailsJson) {
        if (emailNode.get("verified").asBoolean()) {
          return emailNode.get("email").asText();
        }
      }

      return null;
    } catch (Exception e) {
      log.warn("Failed to fetch GitHub emails", e);
      return null;
    }
  }
}
