package org.hspconsortium.sandboxmanagerapi.controllers;

import org.hspconsortium.sandboxmanagerapi.model.*;
import org.hspconsortium.sandboxmanagerapi.services.OAuthService;
import org.hspconsortium.sandboxmanagerapi.services.SandboxService;
import org.hspconsortium.sandboxmanagerapi.services.UserAccessHistoryService;
import org.hspconsortium.sandboxmanagerapi.services.UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(value = UserAccessHistoryController.class, secure = false)
@ContextConfiguration(classes = UserAccessHistoryController.class)
public class UserAccessHistoryControllerTest {

    @Autowired
    private MockMvc mvc;

    private HttpMessageConverter mappingJackson2HttpMessageConverter;

    @MockBean
    private OAuthService oAuthService;

    @MockBean
    private SandboxService sandboxService;

    @MockBean
    private UserAccessHistoryService userAccessHistoryService;

    @MockBean
    private UserService userService;

    @Autowired
    void setConverters(HttpMessageConverter<?>[] converters) {

        this.mappingJackson2HttpMessageConverter = Arrays.stream(converters)
                .filter(hmc -> hmc instanceof MappingJackson2HttpMessageConverter)
                .findAny()
                .orElse(null);

        assertNotNull("the JSON message converter must not be null",
                this.mappingJackson2HttpMessageConverter);
    }

    private Sandbox sandbox;
    private User user;
    private UserAccessHistory userAccessHistory;
    private List<UserAccessHistory> userAccessHistoryList;

    @Before
    public void setup() {
        when(oAuthService.getOAuthUserId(any())).thenReturn("me");
        user = new User();
        sandbox = new Sandbox();
        user.setSbmUserId("me");
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        List<UserRole> userRoles = new ArrayList<>();
        userRoles.add(userRole);
        sandbox.setUserRoles(userRoles);
//        sandbox.setVisibility(Visibility.PUBLIC);
        sandbox.setSandboxId("sandbox");
        userAccessHistory = new UserAccessHistory();
        userAccessHistoryList = new ArrayList<>();
        userAccessHistoryList.add(userAccessHistory);
    }

    @Test
    public void getLastSandboxAccessWithSandboxIdTest() throws Exception {
        String json = json(userAccessHistoryList);
        when(sandboxService.findBySandboxId(sandbox.getSandboxId())).thenReturn(sandbox);
        when(userAccessHistoryService.getLatestUserAccessHistoryInstancesWithSandbox(sandbox)).thenReturn(userAccessHistoryList);
        mvc
                .perform(get("/sandbox-access?sandboxId=" + sandbox.getSandboxId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(content().json(json));
    }

    @Test
    public void getLastSandboxAccessWithSbmUserIdTest() throws Exception {
        String json = json(userAccessHistoryList);
        when(userService.findBySbmUserId(any())).thenReturn(user);
        when(userAccessHistoryService.getLatestUserAccessHistoryInstancesWithSbmUser(user)).thenReturn(userAccessHistoryList);
        mvc
                .perform(get("/sandbox-access?sbmUserId=" + user.getSbmUserId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(content().json(json));
    }

    @Test
    public void getLastSandboxAccessTest() throws Exception {
        Timestamp timestamp = new Timestamp(new Date().getTime());
        when(userService.findBySbmUserId(any())).thenReturn(user);
        when(sandboxService.findBySandboxId(sandbox.getSandboxId())).thenReturn(sandbox);
        when(userAccessHistoryService.getLatestUserAccessHistoryInstance(sandbox, user)).thenReturn(timestamp);
        mvc
                .perform(get("/sandbox-access?sbmUserId=" + user.getSbmUserId() + "&sandboxId=" + sandbox.getSandboxId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(content().json(Long.toString(timestamp.getTime())));
    }

    @SuppressWarnings("unchecked")
    private String json(Object o) throws IOException {
        MockHttpOutputMessage mockHttpOutputMessage = new MockHttpOutputMessage();
        mappingJackson2HttpMessageConverter.write(
                o, MediaType.APPLICATION_JSON, mockHttpOutputMessage);
        return mockHttpOutputMessage.getBodyAsString();
    }
}