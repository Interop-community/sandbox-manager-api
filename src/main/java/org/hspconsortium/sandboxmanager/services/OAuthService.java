package org.hspconsortium.sandboxmanager.services;

import javax.servlet.http.HttpServletRequest;

/**
 */
public interface OAuthService {

    String getBearerToken(HttpServletRequest request);

    String getOAuthUserId(HttpServletRequest request);

    String postOAuthClient(String clientJSON);

    String putOAuthClient(Integer id, String clientJSON);

    String getOAuthClient(Integer id);

    void deleteOAuthClient(Integer id);
}