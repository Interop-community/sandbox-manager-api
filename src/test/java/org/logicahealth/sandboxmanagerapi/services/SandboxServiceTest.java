package org.logicahealth.sandboxmanagerapi.services;

import com.amazonaws.services.cloudwatch.model.ResourceNotFoundException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.junit.Before;
import org.junit.Test;
import org.logicahealth.sandboxmanagerapi.model.*;
import org.logicahealth.sandboxmanagerapi.repositories.SandboxRepository;
import org.logicahealth.sandboxmanagerapi.repositories.UserSandboxRepository;
import org.logicahealth.sandboxmanagerapi.services.impl.SandboxServiceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class SandboxServiceTest {

    private SandboxRepository repository = mock(SandboxRepository.class);
    private UserService userService = mock(UserService.class);
    private UserRoleService userRoleService = mock(UserRoleService.class);
    private UserPersonaService userPersonaService = mock(UserPersonaService.class);
    private UserLaunchService userLaunchService = mock(UserLaunchService.class);
    private SandboxInviteService sandboxInviteService = mock(SandboxInviteService.class);
    private AppService appService = mock(AppService.class);
    private LaunchScenarioService launchScenarioService = mock(LaunchScenarioService.class);
    private SandboxImportService sandboxImportService = mock(SandboxImportService.class);
    private SandboxActivityLogService sandboxActivityLogService = mock(SandboxActivityLogService.class);
    private RuleService ruleService = mock(RuleService.class);
    private UserAccessHistoryService userAccessHistoryService = mock(UserAccessHistoryService.class);
    private CdsServiceEndpointService cdsServiceEndpointService = mock(CdsServiceEndpointService.class);
    private FhirProfileDetailService fhirProfileDetailService = mock(FhirProfileDetailService.class);
    private CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
    private CloseableHttpClient sandboxDeleteHttpClient = mock(CloseableHttpClient.class);
    private CloseableHttpResponse response = spy(CloseableHttpResponse.class);
    private SandboxBackgroundTasksService sandboxBackgroundTasksService = mock(SandboxBackgroundTasksService.class);
    private UserSandboxRepository userSandboxRepository = mock(UserSandboxRepository.class);

    private SandboxServiceImpl sandboxService = new SandboxServiceImpl(repository);

    private Sandbox sandbox = spy(Sandbox.class);
    private Sandbox newSandbox = spy(Sandbox.class);

    private User user;
    private String bearerToken = "token";
    private SandboxImport sandboxImport;
    private StatusLine statusLine;
    private LaunchScenario launchScenario;
    private UserPersona userPersona;
    private SandboxInvite sandboxInvite;
    private App app;
    private UserLaunch userLaunch;
    private UserRole userRole;
    private List<LaunchScenario> launchScenarios;
    private List<UserPersona> userPersonas;
    private List<SandboxInvite> sandboxInvites;
    private List<App> apps;
    private List<UserLaunch> userLaunches;
    private List<UserRole> userRoles;
    private List<Sandbox> sandboxes;
    private Role role = Role.ADMIN;
    private String token = "token";

    @Before
    public void setup() {
        sandboxService.setUserAccessHistoryService(userAccessHistoryService);
        sandboxService.setUserLaunchService(userLaunchService);
        sandboxService.setUserPersonaService(userPersonaService);
        sandboxService.setUserRoleService(userRoleService);
        sandboxService.setSandboxInviteService(sandboxInviteService);
        sandboxService.setUserService(userService);
        sandboxService.setAppService(appService);
        sandboxService.setLaunchScenarioService(launchScenarioService);
        sandboxService.setSandboxImportService(sandboxImportService);
        sandboxService.setSandboxActivityLogService(sandboxActivityLogService);
        sandboxService.setRuleService(ruleService);
        sandboxService.setHttpClient(httpClient);
        sandboxService.setSandboxDeleteHttpClient(sandboxDeleteHttpClient);
        sandboxService.setCdsServiceEndpointService(cdsServiceEndpointService);
        sandboxService.setFhirProfileDetailService(fhirProfileDetailService);
        sandboxService.setSandboxBackgroundTasksService(sandboxBackgroundTasksService);
        sandboxService.setUserSandboxRepository(userSandboxRepository);

        sandbox.setId(1);
        sandbox.setSandboxId("sandboxId");
        sandbox.setApiEndpointIndex("9");
        sandbox.setVisibility(Visibility.PUBLIC);
        newSandbox.setSandboxId("new-sandbox");
        newSandbox.setApiEndpointIndex("10");
        sandboxes = new ArrayList<>();
        sandboxes.add(sandbox);
        user = new User();
        user.setId(1);
        user.setSbmUserId("userId");
        user.setSandboxes(sandboxes);
        sandbox.setCreatedBy(user);
        sandbox.setCreationStatus(SandboxCreationStatus.CREATED);
        List<SandboxImport> sandboxImportList = new ArrayList<>();
        sandboxImport = new SandboxImport();
        sandboxImportList.add(sandboxImport);
        sandbox.setImports(sandboxImportList);
        launchScenarios = new ArrayList<>();
        launchScenario = new LaunchScenario();
        launchScenario.setId(1);
        launchScenario.setVisibility(Visibility.PRIVATE);
        launchScenarios.add(launchScenario);
        launchScenario.setSandbox(sandbox);
        apps = new ArrayList<>();
        app = new App();
        app.setId(1);
        app.setVisibility(Visibility.PRIVATE);
        app.setClientId("clientId");
        app.setLaunchUri("launchUri");
        launchScenario.setApp(app);
        apps.add(app);
        userPersonas = new ArrayList<>();
        userPersona = new UserPersona();
        userPersona.setId(1);
        userPersona.setVisibility(Visibility.PRIVATE);
        userPersona.setPersonaUserId("user@sandbox");
        launchScenario.setUserPersona(userPersona);
        userPersonas.add(userPersona);
        sandboxInvites = new ArrayList<>();
        sandboxInvite = new SandboxInvite();
        sandboxInvite.setSandbox(sandbox);
        sandboxInvites.add(sandboxInvite);
        userLaunch = new UserLaunch();
        userLaunch.setId(1);
        userLaunch.setLaunchScenario(launchScenario);
        userLaunches = new ArrayList<>();
        userLaunches.add(userLaunch);
        userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(Role.ADMIN);
        userRole.setId(1);
        userRoles = new ArrayList<>();
        userRoles.add(userRole);
        sandbox.setUserRoles(userRoles);

        statusLine = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "FINE!");

        String[] defaultPublicSandboxRoles = new String[]{"USER"};

        ReflectionTestUtils.setField(sandboxService, "defaultSandboxVisibility", "PRIVATE");
        ReflectionTestUtils.setField(sandboxService, "expirationDate", "2018-09-01");
        ReflectionTestUtils.setField(sandboxService, "defaultSandboxCreatorRoles", new String[0]);
        ReflectionTestUtils.setField(sandboxService, "defaultPublicSandboxRoles", defaultPublicSandboxRoles);

        when(ruleService.checkIfUserCanCreateSandbox(user, token)).thenReturn(true);
        when(repository.findBySandboxId(sandbox.getSandboxId())).thenReturn(sandbox);
        when(repository.save(newSandbox)).thenReturn(newSandbox);
        when(userService.findBySbmUserId(user.getSbmUserId())).thenReturn(user);

        FhirVersion currentFhirVersion = new FhirVersion();
        currentFhirVersion.setDstu2("8");
        currentFhirVersion.setStu3("9");
        currentFhirVersion.setR4("10");
        currentFhirVersion.setApiBaseURL_dstu2("apiBaseURL_currDstu2");
        currentFhirVersion.setApiBaseURL_stu3("apiBaseURL_currStu3");
        currentFhirVersion.setApiBaseURL_r4("apiBaseURL_currR4");
        FhirVersion prevFhirVersion = new FhirVersion();
        prevFhirVersion.setDstu2("5");
        prevFhirVersion.setStu3("6");
        prevFhirVersion.setR4("7");
        prevFhirVersion.setApiBaseURL_dstu2("apiBaseURL_prevDstu2");
        prevFhirVersion.setApiBaseURL_stu3("apiBaseURL_prevStu3");
        prevFhirVersion.setApiBaseURL_r4("apiBaseURL_prevR4");
        ApiEndpointIndex apiEndpointIndex = new ApiEndpointIndex(prevFhirVersion, currentFhirVersion);
        ReflectionTestUtils.setField(sandboxService, "apiEndpointIndexObj", apiEndpointIndex);
    }

    @Test
    public void deleteTest() {
        sandboxService.delete(sandbox.getId());
        verify(repository).deleteById(sandbox.getId());
    }

    @Test
    public void deleteTestAll() throws IOException {
        when(sandboxDeleteHttpClient.execute(any())).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        sandboxService.delete(sandbox, bearerToken, user, false);
        verify(sandboxImportService).delete(sandboxImport);
        verify(sandboxActivityLogService).sandboxDelete(sandbox, user);
        verify(sandboxInviteService).findInvitesBySandboxId(sandbox.getSandboxId());
    }

    @Test
    public void deleteTestAllAdminIsNull() throws IOException {
        when(sandboxDeleteHttpClient.execute(any())).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        sandboxService.delete(sandbox, bearerToken, null, false);
        verify(sandboxActivityLogService).sandboxDelete(sandbox, sandbox.getCreatedBy());
    }

    @Test
    public void deleteTestAllVerifyItemsDeleted() throws IOException {
        when(sandboxDeleteHttpClient.execute(any())).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(launchScenarioService.findBySandboxId(sandbox.getSandboxId())).thenReturn(launchScenarios);
        when(userPersonaService.findBySandboxId(sandbox.getSandboxId())).thenReturn(userPersonas);
        when(sandboxInviteService.findInvitesBySandboxId(sandbox.getSandboxId())).thenReturn(sandboxInvites);
        when(appService.findBySandboxIdIncludingCustomApps(sandbox.getSandboxId())).thenReturn(apps);
        sandboxService.delete(sandbox, bearerToken, user, false);
        verify(launchScenarioService).delete(launchScenario);
        verify(userPersonaService).delete(userPersona);
        verify(sandboxInviteService).delete(sandboxInvite);
        verify(appService).delete(app);
        verify(userAccessHistoryService).deleteUserAccessInstancesForSandbox(sandbox);
    }

    @Test(expected = RuntimeException.class)
    public void deleteTestErrorInApiCall() throws IOException {
        when(launchScenarioService.findBySandboxId(sandbox.getSandboxId())).thenReturn(launchScenarios);
        when(userPersonaService.findBySandboxId(sandbox.getSandboxId())).thenReturn(userPersonas);
        when(httpClient.execute(any())).thenReturn(response);
        statusLine = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "NOT FINE!");
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(mock(HttpEntity.class));
        sandboxService.delete(sandbox, bearerToken, user, false);
    }

    @Test(expected = RuntimeException.class)
    public void deleteTestThrownExeptionInApiCall() throws IOException {
        when(launchScenarioService.findBySandboxId(sandbox.getSandboxId())).thenReturn(launchScenarios);
        when(userPersonaService.findBySandboxId(sandbox.getSandboxId())).thenReturn(userPersonas);
        when(httpClient.execute(any())).thenThrow(IOException.class);
        sandboxService.delete(sandbox, bearerToken, user, false);
    }

    @Test
    public void cloneTest() throws IOException {
        when(ruleService.checkIfUserCanCreateSandbox(user, token)).thenReturn(true);
        when(userPersonaService.findByPersonaUserId(user.getSbmUserId())).thenReturn(null);
        when(httpClient.execute(any())).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        sandboxService.clone(newSandbox, sandbox.getSandboxId(), user, bearerToken);
        verify(newSandbox).setCreatedBy(user);
        verify(newSandbox).setCreatedTimestamp(any());
        verify(newSandbox).setVisibility(any());
        verify(newSandbox).setPayerUserId(any());
        verify(sandboxActivityLogService).sandboxCreate(newSandbox, user);
    }

    @Test(expected = ResourceNotFoundException.class)
    public void cloneTestExistingSandboxDoesntExist() throws IOException {
        when(ruleService.checkIfUserCanCreateSandbox(user, token)).thenReturn(true);
        when(repository.findBySandboxId(sandbox.getSandboxId())).thenReturn(null);
        sandboxService.clone(newSandbox, sandbox.getSandboxId(), user, bearerToken);
    }

    @Test(expected = IllegalArgumentException.class)
    public void cloneTestNewSandboxAlreadyExists() throws IOException {
        when(ruleService.checkIfUserCanCreateSandbox(user, token)).thenReturn(true);
        when(repository.findBySandboxId(newSandbox.getSandboxId())).thenReturn(newSandbox);
        sandboxService.clone(newSandbox, sandbox.getSandboxId(), user, bearerToken);
    }

    @Test
    public void cloneTestCloneUserPersonas() throws IOException {
        newSandbox.setDataSet(DataSet.DEFAULT);
        when(ruleService.checkIfUserCanCreateSandbox(user, token)).thenReturn(true);
        when(userPersonaService.findByPersonaUserId(user.getSbmUserId())).thenReturn(null);
        when(httpClient.execute(any())).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(userPersonaService.findBySandboxId(sandbox.getSandboxId())).thenReturn(userPersonas);
        sandboxService.clone(newSandbox, sandbox.getSandboxId(), user, bearerToken);
        verify(userPersonaService).save(any());
    }

    @Test
    public void cloneTestCloneAppsAndLaunchScenarios() throws IOException {
        newSandbox.setApps(DataSet.DEFAULT);
        newSandbox.setDataSet(DataSet.DEFAULT);
        when(ruleService.checkIfUserCanCreateSandbox(user, token)).thenReturn(true);
        when(userPersonaService.findByPersonaUserId(user.getSbmUserId())).thenReturn(null);
        when(httpClient.execute(any())).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(appService.findBySandboxId(sandbox.getSandboxId())).thenReturn(apps);
        when(appService.findByLaunchUriAndClientIdAndSandboxId(any(), any(), any())).thenReturn(app);
        List<ContextParams> contextParams = new ArrayList<>();
        contextParams.add(new ContextParams());
        launchScenario.setContextParams(contextParams);
        when(launchScenarioService.findBySandboxId(sandbox.getSandboxId())).thenReturn(launchScenarios);
        sandboxService.clone(newSandbox, sandbox.getSandboxId(), user, bearerToken);
        verify(launchScenarioService).save(any());
        verify(appService).save(any());
    }

    @Test
    public void cloneTestCloneAppsOnly() throws IOException {
        newSandbox.setApps(DataSet.DEFAULT);
        when(ruleService.checkIfUserCanCreateSandbox(user, token)).thenReturn(true);
        when(userPersonaService.findByPersonaUserId(user.getSbmUserId())).thenReturn(null);
        when(httpClient.execute(any())).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(appService.findBySandboxId(sandbox.getSandboxId())).thenReturn(apps);
        when(launchScenarioService.findBySandboxId(sandbox.getSandboxId())).thenReturn(launchScenarios);
        sandboxService.clone(newSandbox, sandbox.getSandboxId(), user, bearerToken);
        verify(launchScenarioService, times(0)).save(any());
        verify(appService).save(any());
    }

    @Test
    public void cloneTestInitialPersonaNotNull() throws IOException {
        when(ruleService.checkIfUserCanCreateSandbox(user, token)).thenReturn(true);
        when(userPersonaService.findByPersonaUserId(user.getSbmUserId())).thenReturn(userPersona);
        sandboxService.clone(newSandbox, sandbox.getSandboxId(), user, bearerToken);
        verify(repository, times(0)).save(any(Sandbox.class));
    }

    @Test
    public void cloneTestCantClone() throws IOException {
        when(ruleService.checkIfUserCanCreateSandbox(user, token)).thenReturn(false);
        sandboxService.clone(sandbox, sandbox.getSandboxId(), user, bearerToken);
        verify(repository, times(0)).save(any(Sandbox.class));
    }

    @Test
    public void updateTest() throws IOException {
        when(repository.findBySandboxId(sandbox.getSandboxId())).thenReturn(sandbox);
        when(httpClient.execute(any())).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        sandboxService.update(sandbox, user, bearerToken);
        verify(sandbox).setName(null);
        verify(sandbox).setDescription(null);
        verify(sandboxActivityLogService, times(0)).sandboxOpenEndpoint(any(), any(), any());
    }

    @Test
    public void updateTestIsOpenAccess() throws IOException {
        Sandbox otherSandbox = new Sandbox();
        otherSandbox.setAllowOpenAccess(true);
        otherSandbox.setId(2);
        otherSandbox.setSandboxId("otherSandboxId");
        otherSandbox.setApiEndpointIndex("9");
        when(repository.findBySandboxId(sandbox.getSandboxId())).thenReturn(otherSandbox);
        when(httpClient.execute(any())).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        sandboxService.update(sandbox, user, bearerToken);
        verify(sandboxActivityLogService).sandboxOpenEndpoint(any(), any(), any());
    }


    @Test
    public void updateDataSetNATest() throws IOException {
        Sandbox otherSandbox = new Sandbox();
        otherSandbox.setAllowOpenAccess(true);
        otherSandbox.setId(2);
        otherSandbox.setSandboxId("otherSandboxId");
        otherSandbox.setApiEndpointIndex("9");
        otherSandbox.setDataSet(DataSet.NONE);
        when(repository.findBySandboxId(sandbox.getSandboxId())).thenReturn(otherSandbox);
        when(httpClient.execute(any())).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        sandboxService.update(sandbox, user, bearerToken);
        verify(sandboxActivityLogService).sandboxOpenEndpoint(any(), any(), any());
    }

    @Test(expected = RuntimeException.class)
    public void updateTestNot200CodeReturned() throws IOException {
        Sandbox otherSandbox = new Sandbox();
        otherSandbox.setAllowOpenAccess(true);
        otherSandbox.setId(2);
        otherSandbox.setSandboxId("otherSandboxId");
        otherSandbox.setApiEndpointIndex("9");
        when(repository.findBySandboxId(sandbox.getSandboxId())).thenReturn(otherSandbox);
        when(httpClient.execute(any())).thenReturn(response);
        statusLine = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "NOT FINE!");
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(mock(HttpEntity.class));
        sandboxService.update(sandbox, user, bearerToken);
    }

    @Test(expected = RuntimeException.class)
    public void updateTestExceptionThrownWithExecute() throws IOException {
        Sandbox otherSandbox = new Sandbox();
        otherSandbox.setAllowOpenAccess(true);
        otherSandbox.setId(2);
        otherSandbox.setSandboxId("otherSandboxId");
        otherSandbox.setApiEndpointIndex("9");
        when(repository.findBySandboxId(sandbox.getSandboxId())).thenReturn(otherSandbox);
        when(httpClient.execute(any())).thenThrow(IOException.class);
        sandboxService.update(sandbox, user, bearerToken);
    }

    @Test
    public void removeMemberTest() {
        when(launchScenarioService.findBySandboxIdAndCreatedBy(sandbox.getSandboxId(), user.getSbmUserId())).thenReturn(launchScenarios);
        when(userPersonaService.findBySandboxIdAndCreatedBy(sandbox.getSandboxId(), user.getSbmUserId())).thenReturn(userPersonas);
        when(appService.findBySandboxIdAndCreatedBy(sandbox.getSandboxId(), user.getSbmUserId())).thenReturn(apps);
        when(userLaunchService.findByUserId(user.getSbmUserId())).thenReturn(userLaunches);

        sandboxService.removeMember(sandbox, user, bearerToken);
        verify(userService).removeSandbox(sandbox, user);
        verify(userLaunchService).delete(userLaunch);
        verify(launchScenarioService).delete(launchScenario);
        verify(appService).delete(app);
        verify(userPersonaService).delete(userPersona);
        verify(sandboxActivityLogService).sandboxUserRemoved(sandbox, sandbox.getCreatedBy(), user);
        verify(userRoleService).delete(userRole);
    }

    @Test
    public void addMemberTest() {
        sandboxService.addMember(sandbox, user, role);
        verify(sandboxActivityLogService).sandboxUserRoleChange(sandbox, user, role, true);
        verify(userService).addSandbox(sandbox, user);
        verify(sandboxActivityLogService).sandboxUserAdded(sandbox, user);
    }

    @Test
    public void addMemberTest2() {
        sandboxService.addMember(sandbox, user);
        verify(sandboxActivityLogService).sandboxUserRoleChange(sandbox, user, Role.USER, true);
    }

    @Test
    public void addMemberRoleTest() {
        sandbox.setUserRoles(new ArrayList<>());
        sandboxService.addMemberRole(sandbox, user, role);
        verify(sandboxActivityLogService).sandboxUserRoleChange(sandbox, user, role, true);
    }

    @Test
    public void addMemberRoleTestAlreadyMember() {
        sandboxService.addMemberRole(sandbox, user, role);
        verify(sandboxActivityLogService, times(0)).sandboxUserRoleChange(sandbox, user, role, true);
    }

    @Test
    public void addMemberRoleTestNewRole() {
        List<UserRole> userRoles = new ArrayList<>();
        UserRole userRole = new UserRole();
        userRole.setRole(Role.ADMIN);
        userRole.setUser(user);
        userRoles.add(userRole);
        sandbox.setUserRoles(userRoles);
        sandboxService.addMemberRole(sandbox, user, Role.MANAGE_USERS);
        verify(sandboxActivityLogService).sandboxUserRoleChange(sandbox, user, Role.MANAGE_USERS, true);
    }

    @Test
    public void removeMemberRoleTest() {
        sandboxService.removeMemberRole(sandbox, user, role);
        verify(userRoleService).delete(userRole);
    }

    @Test
    public void changePayerForSandboxTest() {
        sandboxService.changePayerForSandbox(sandbox, user);
        verify(sandbox).setPayerUserId(user.getId());
    }

    @Test
    public void hasMemberRoleTest() {
        Boolean bool = sandboxService.hasMemberRole(sandbox, user, role);
        assertEquals(true, bool);
    }

    @Test
    public void hasMemberRoleTestNoRole() {
        sandbox.setUserRoles(new ArrayList<>());
        Boolean bool = sandboxService.hasMemberRole(sandbox, user, role);
        assertEquals(false, bool);
    }

    @Test
    public void addSandboxImportTest() {
        sandboxService.addSandboxImport(sandbox, sandboxImport);
        verify(sandbox).setImports(sandbox.getImports());
    }

    @Test
    public void isSandboxMemberTest() {
        Boolean bool = sandboxService.isSandboxMember(sandbox, user);
        assertEquals(true, bool);
    }

    @Test
    public void isSandboxMemberTestNotMember() {
        sandbox = new Sandbox();
        sandbox.setUserRoles(new ArrayList<>());
        Boolean bool = sandboxService.isSandboxMember(sandbox, user);
        assertEquals(false, bool);
    }

    @Test
    public void sandboxLoginTest() {
        sandboxService.sandboxLogin(sandbox.getSandboxId(), user.getSbmUserId());
        verify(userService).findBySbmUserId(user.getSbmUserId());
        verify(sandboxActivityLogService).sandboxLogin(sandbox, user);
    }

    @Test
    public void sandboxLoginTestNotMember() {
        User otherUser = new User();
        otherUser.setSbmUserId("other-user");
        when(userService.findBySbmUserId(user.getSbmUserId())).thenReturn(otherUser);
        sandboxService.sandboxLogin(sandbox.getSandboxId(), user.getSbmUserId());
        verify(userService).findBySbmUserId(user.getSbmUserId());
        verify(sandboxActivityLogService, times(0)).sandboxLogin(sandbox, user);
    }

    @Test
    public void saveTest() {
        when(repository.save(sandbox)).thenReturn(sandbox);
        Sandbox returnedSandbox = sandboxService.save(sandbox);
        assertEquals(sandbox, returnedSandbox);
    }

    @Test
    public void getAllowedSandboxesTest() {
        List<Sandbox> publicSandBoxes = new ArrayList<>();
        Sandbox publicSandbox = new Sandbox();
        publicSandbox.setVisibility(Visibility.PUBLIC);
        publicSandBoxes.add(publicSandbox);
        when(repository.findByVisibility(Visibility.PUBLIC)).thenReturn(publicSandBoxes);
        List<Sandbox> returnedSandboxes = sandboxService.getAllowedSandboxes(user);
        assertEquals(2, returnedSandboxes.size());
    }

    @Test
    public void findBySandboxIdTest() {
        Sandbox returnedSandbox = sandboxService.findBySandboxId(sandbox.getSandboxId());
        assertEquals(sandbox, returnedSandbox);
    }

    @Test
    public void findByVisibilityTest() {
        when(repository.findByVisibility(Visibility.PUBLIC)).thenReturn(sandboxes);
        List<Sandbox> returnedSandboxes = sandboxService.findByVisibility(Visibility.PUBLIC);
        assertEquals(sandboxes, returnedSandboxes);
    }

    @Test
    public void fullCountTest() {
        when(repository.fullCount()).thenReturn("1");
        String returnedCount = sandboxService.fullCount();
        assertEquals("1", returnedCount);
    }

    @Test
    public void schemaCountTest() {
        when(repository.schemaCount("9")).thenReturn("1");
        String returnedSchemaCount = sandboxService.schemaCount("9");
        assertEquals("1", returnedSchemaCount);
    }

    @Test
    public void intervalCountTest() {
        Timestamp timestamp = new Timestamp(new java.util.Date().getTime());
        when(repository.intervalCount(timestamp)).thenReturn("1");
        String returnedIntervalCount = sandboxService.intervalCount(timestamp);
        assertEquals("1", returnedIntervalCount);
    }

    @Test
    public void findByPayerIdTest() {
        when(repository.findByPayerUserId(user.getId())).thenReturn(sandboxes);
        List<Sandbox> returnedSandboxes = sandboxService.findByPayerId(user.getId());
        assertEquals(sandboxes, returnedSandboxes);
    }

    @Test
    public void getSandboxApiURLTest() {
        sandbox.setApiEndpointIndex("5");
        String url = sandboxService.getSandboxApiURL(sandbox);
        assertEquals("apiBaseURL_prevDstu2/" + sandbox.getSandboxId(), url);

        sandbox.setApiEndpointIndex("6");
        url = sandboxService.getSandboxApiURL(sandbox);
        assertEquals("apiBaseURL_prevStu3/" + sandbox.getSandboxId(), url);

        sandbox.setApiEndpointIndex("7");
        url = sandboxService.getSandboxApiURL(sandbox);
        assertEquals("apiBaseURL_prevR4/" + sandbox.getSandboxId(), url);

        sandbox.setApiEndpointIndex("8");
        url = sandboxService.getSandboxApiURL(sandbox);
        assertEquals("apiBaseURL_currDstu2/" + sandbox.getSandboxId(), url);

        sandbox.setApiEndpointIndex("9");
        url = sandboxService.getSandboxApiURL(sandbox);
        assertEquals("apiBaseURL_currStu3/" + sandbox.getSandboxId(), url);

        sandbox.setApiEndpointIndex("10");
        url = sandboxService.getSandboxApiURL(sandbox);
        assertEquals("apiBaseURL_currR4/" + sandbox.getSandboxId(), url);
    }

    @Test
    public void createTest() {
        //THIS METHOD IS NO LONGER USED
    }

    @Test
    public void resetTest() {
        sandboxService.reset(sandbox, "");
        verify(launchScenarioService).findBySandboxId(sandbox.getSandboxId());
    }

    @Test
    public void newSandboxesInIntervalCountTest() {
        Date d = new Date();
        Timestamp intervalTime = new Timestamp(d.getTime());
        sandboxService.newSandboxesInIntervalCount(intervalTime, "8");
        verify(repository).newSandboxesInIntervalCount(intervalTime, "8");

    }

    @Test
    public void getSystemSandboxApiURLTest() {
        assertEquals(sandboxService.getSystemSandboxApiURL(), "apiBaseURL_currDstu2/system");
    }

    @Test
    public void findAllTest() {
        sandboxService.findAll();
        verify(repository).findAll();
    }

    @Test
    public void sandboxQueuedCreationStatusTest() {
        var sandboxes = createQueuedSandboxes();
        when(repository.findByCreationStatusOrderByCreatedTimestampAsc(any(SandboxCreationStatus.class))).thenReturn(sandboxes);
        var queuedCreationStatus = sandboxService.getQueuedCreationStatus(sandboxes.get(1)
                                                                                   .getSandboxId());
        assertEquals(1, queuedCreationStatus.getQueuePosition());
        assertEquals(SandboxCreationStatus.QUEUED, queuedCreationStatus.getSandboxCreationStatus());
    }

    @Test
    public void sandboxCreatedCreationStatusTest() {
        when(repository.findByCreationStatusOrderByCreatedTimestampAsc(any(SandboxCreationStatus.class))).thenReturn(Collections.emptyList());
        when(repository.findBySandboxId(anyString())).thenReturn(sandbox);
        var queuedCreationStatus = sandboxService.getQueuedCreationStatus(sandbox.getSandboxId());
        assertEquals(0, queuedCreationStatus.getQueuePosition());
        assertEquals(SandboxCreationStatus.CREATED, queuedCreationStatus.getSandboxCreationStatus());
    }

    private List<Sandbox> createQueuedSandboxes() {
        Sandbox sandbox1 = new Sandbox();
        sandbox1.setSandboxId("sandboxId1");
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        sandbox1.setCreatedTimestamp(Timestamp.from(yesterday));
        sandbox1.setCreationStatus(SandboxCreationStatus.QUEUED);
        Sandbox sandbox2 = new Sandbox();
        sandbox2.setSandboxId("sandboxId2");
        sandbox2.setCreatedTimestamp(Timestamp.from(now));
        sandbox2.setCreationStatus(SandboxCreationStatus.QUEUED);
        List<Sandbox> sandboxes = new ArrayList<>(2);
        sandboxes.add(sandbox1);
        sandboxes.add(sandbox2);
        return sandboxes;
    }

}
