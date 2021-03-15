package org.logicahealth.sandboxmanagerapi.services;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.logicahealth.sandboxmanagerapi.model.Sandbox;
import org.logicahealth.sandboxmanagerapi.model.User;
import org.logicahealth.sandboxmanagerapi.model.Visibility;
import org.logicahealth.sandboxmanagerapi.repositories.SandboxRepository;
import org.logicahealth.sandboxmanagerapi.services.impl.SandboxBackgroundTasksServiceImpl;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SandboxBackgroundTasksServiceTest {

    private CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
    private SandboxRepository repository  = mock(SandboxRepository.class);
    private UserAccessHistoryService userAccessHistoryService = mock(UserAccessHistoryService.class);
    private SandboxBackgroundTasksService sandboxBackgroundTasksService = new SandboxBackgroundTasksServiceImpl(httpClient, repository, userAccessHistoryService);

    private Sandbox sandbox = spy(Sandbox.class);
    private Sandbox newSandbox = spy(Sandbox.class);
    private User user = spy(User.class);
    private CloseableHttpResponse response = spy(CloseableHttpResponse.class);

    private StatusLine statusLine;

    @Before
    public void setup() {
        sandbox.setId(1);
        sandbox.setSandboxId("sandboxId");
        sandbox.setApiEndpointIndex("9");
        sandbox.setVisibility(Visibility.PUBLIC);
        newSandbox.setSandboxId("new-sandbox");
        newSandbox.setApiEndpointIndex("10");
    }

    @Test(expected = RuntimeException.class)
    public void cloneTestThrowsExceptionInApiCall() throws IOException {
        when(httpClient.execute(any())).thenThrow(IOException.class);
        sandboxBackgroundTasksService.cloneSandboxSchema(newSandbox, sandbox, user,"bearerToken", "sandboxApiURL");
    }

    @Test(expected = RuntimeException.class)
    public void cloneTestErrorInCallToReferenceApi() throws IOException {
        statusLine = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "NOT FINE!");
        when(httpClient.execute(any())).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(mock(HttpEntity.class));
        sandboxBackgroundTasksService.cloneSandboxSchema(newSandbox, sandbox, user,"bearerToken", "sandboxApiURL");
    }
}