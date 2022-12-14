/*
 * #%L
 *
 * %%
 * Copyright (C) 2014 - 2015 Healthcare Services Platform Consortium
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package org.logicahealth.sandboxmanagerapi.services.impl;

import org.logicahealth.sandboxmanagerapi.services.OAuthClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.Collections;

@Service
public class OAuthClientServiceImpl implements OAuthClientService {

    @Value("${hspc.platform.api.oauthClientEndpointURL}")
    String oauthClientEndpointURL;

    private OAuth2RestOperations restTemplate;

    @Inject
    public void setRestTemplate(OAuth2RestOperations restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String postOAuthClient(String clientJSON) {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(clientJSON, requestHeaders);

        ResponseEntity<String> responseEntity = restTemplate.exchange(oauthClientEndpointURL, HttpMethod.POST, requestEntity, String.class);
        return responseEntity.getBody();
    }

    @Override
    public String putOAuthClientWithClientId(String clientId, String clientJSON) {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(clientJSON, requestHeaders);

        ResponseEntity<String> responseEntity = restTemplate.exchange(oauthClientEndpointURL + "?clientId=" + clientId, HttpMethod.PUT, requestEntity, String.class);
        return responseEntity.getBody();
    }

    @Override
    public String getOAuthClientWithClientId(String clientId) {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> requestEntity = new HttpEntity<>(requestHeaders);

        ResponseEntity<String> responseEntity = restTemplate.exchange(oauthClientEndpointURL + "?clientId=" + clientId, HttpMethod.GET, requestEntity, String.class);
        return responseEntity.getBody();
    }

    @Override
    public void deleteOAuthClientWithClientId(String clientId) {
        restTemplate.delete(oauthClientEndpointURL + "?clientId=" + clientId);
    }
}
