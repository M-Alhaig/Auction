package com.auction.userservice.client;

import com.auction.userservice.dto.oauth2.OAuth2UserInfo;
import com.auction.userservice.exceptions.OAuth2AuthenticationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client for Google OAuth2 token exchange and user info retrieval.
 *
 * <p>Flow:
 * <ol>
 *   <li>Exchange authorization code for tokens at Google's token endpoint</li>
 *   <li>Parse ID token (JWT) to extract user info (sub, email, name, picture)</li>
 * </ol>
 *
 * <p>Google's ID token contains all needed user info, so no additional API call is required.
 */
@Slf4j
@Component
public class GoogleOAuth2Client {

  private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String clientId;
  private final String clientSecret;

  public GoogleOAuth2Client(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      @Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId,
      @Value("${spring.security.oauth2.client.registration.google.client-secret}") String clientSecret) {
    this.restClient = restClientBuilder.build();
    this.objectMapper = objectMapper;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  /**
   * Exchange authorization code for user info.
   *
   * @param code        the authorization code from Google callback
   * @param redirectUri the redirect URI used in the OAuth flow
   * @return normalized user info from Google
   * @throws OAuth2AuthenticationException if code exchange or parsing fails
   */
  public OAuth2UserInfo exchangeCodeForUserInfo(String code, String redirectUri) {
    log.debug("Exchanging Google authorization code for tokens");

    try {
      MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
      formData.add("code", code);
      formData.add("client_id", clientId);
      formData.add("client_secret", clientSecret);
      formData.add("redirect_uri", redirectUri);
      formData.add("grant_type", "authorization_code");

      String response = restClient.post()
          .uri(TOKEN_URL)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(formData)
          .retrieve()
          .body(String.class);

      JsonNode tokenResponse = objectMapper.readTree(response);

      if (tokenResponse.has("error")) {
        String error = tokenResponse.get("error").asText();
        String description = tokenResponse.has("error_description")
            ? tokenResponse.get("error_description").asText()
            : "Unknown error";
        log.warn("Google token exchange failed: {} - {}", error, description);
        throw new OAuth2AuthenticationException("Google authentication failed: " + description);
      }

      String idToken = tokenResponse.get("id_token").asText();
      return parseIdToken(idToken);

    } catch (RestClientException e) {
      log.error("Failed to exchange Google authorization code", e);
      throw new OAuth2AuthenticationException("Failed to communicate with Google", e);
    } catch (OAuth2AuthenticationException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to parse Google token response", e);
      throw new OAuth2AuthenticationException("Failed to parse Google response", e);
    }
  }

  /**
   * Parse Google's ID token to extract user info.
   *
   * <p>The ID token is a JWT signed by Google. We parse without signature verification
   * since we received it directly over HTTPS from Google's token endpoint.
   *
   * <p>For production hardening, consider verifying using Google's JWKS:
   * https://www.googleapis.com/oauth2/v3/certs
   */
  private OAuth2UserInfo parseIdToken(String idToken) {
    try {
      // JWT format: header.payload.signature
      String[] parts = idToken.split("\\.");
      if (parts.length != 3) {
        throw new OAuth2AuthenticationException("Invalid ID token format");
      }

      // Decode payload (Base64URL encoded)
      String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
      JsonNode claims = objectMapper.readTree(payload);

      String providerId = claims.get("sub").asText();
      String email = claims.has("email") ? claims.get("email").asText() : null;
      String name = claims.has("name") ? claims.get("name").asText() : null;
      String picture = claims.has("picture") ? claims.get("picture").asText() : null;
      boolean emailVerified = claims.has("email_verified") && claims.get("email_verified").asBoolean();

      if (email == null) {
        throw new OAuth2AuthenticationException("Google did not provide email address");
      }

      log.debug("Parsed Google user info: providerId={}, email={}", providerId, email);
      return new OAuth2UserInfo(providerId, email, name, picture, emailVerified);

    } catch (OAuth2AuthenticationException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to parse Google ID token", e);
      throw new OAuth2AuthenticationException("Failed to parse Google ID token", e);
    }
  }
}
