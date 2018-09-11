package org.hspconsortium.sandboxmanagerapi.services.impl;

import com.amazonaws.services.cloudwatch.model.ResourceNotFoundException;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.hspconsortium.sandboxmanagerapi.model.*;
import org.hspconsortium.sandboxmanagerapi.repositories.SandboxRepository;
import org.hspconsortium.sandboxmanagerapi.services.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.transaction.Transactional;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class SandboxServiceImpl implements SandboxService {

    @Value("${hspc.platform.defaultPublicSandboxRoles}")
    private String[] defaultPublicSandboxRoles;

    @Value("${hspc.platform.defaultPrivateSandboxRoles}")
    private String[] defaultPrivateSandboxRoles;

    @Value("${hspc.platform.defaultSandboxCreatorRoles}")
    private String[] defaultSandboxCreatorRoles;

    @Value("${hspc.platform.defaultSandboxVisibility}")
    private String defaultSandboxVisibility;

    private static Logger LOGGER = LoggerFactory.getLogger(SandboxServiceImpl.class.getName());
    private final SandboxRepository repository;

    @Value("${hspc.platform.api.version1.baseUrl:}")
    private String apiBaseURL_1;

    @Value("${hspc.platform.api.version2.baseUrl:}")
    private String apiBaseURL_2;

    @Value("${hspc.platform.api.version3.baseUrl:}")
    private String apiBaseURL_3;

    @Value("${hspc.platform.api.version4.baseUrl:}")
    private String apiBaseURL_4;

    @Value("${hspc.platform.api.version5.baseUrl:}")
    private String apiBaseURL_5;

    @Value("${hspc.platform.api.version6.baseUrl:}")
    private String apiBaseURL_6;

    @Value("${hspc.platform.api.version7.baseUrl:}")
    private String apiBaseURL_7;

    @Value("${hspc.platform.api.oauthUserInfoEndpointURL}")
    private String oauthUserInfoEndpointURL;

    @Value("${expiration-message}")
    private String expirationMessage;

    @Value("${expiration-date}")
    private String expirationDate;

    @Value("${default-public-apps}")
    private String[] defaultPublicApps;

    private UserService userService;
    private UserRoleService userRoleService;
    private UserPersonaService userPersonaService;
    private UserLaunchService userLaunchService;
    private AppService appService;
    private LaunchScenarioService launchScenarioService;
    private SandboxImportService sandboxImportService;
    private SandboxActivityLogService sandboxActivityLogService;
    private RuleService ruleService;
    private UserAccessHistoryService userAccessHistoryService;
    private CloseableHttpClient httpClient;

    @Inject
    public SandboxServiceImpl(final SandboxRepository repository) {
        this.repository = repository;
    }

    @Inject
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @Inject
    public void setUserRoleService(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @Inject
    public void setUserPersonaService(UserPersonaService userPersonaService) {
        this.userPersonaService = userPersonaService;
    }

    @Inject
    public void setUserLaunchService(UserLaunchService userLaunchService) {
        this.userLaunchService = userLaunchService;
    }

    @Inject
    public void setAppService(AppService appService) {
        this.appService = appService;
    }

    @Inject
    public void setLaunchScenarioService(LaunchScenarioService launchScenarioService) {
        this.launchScenarioService = launchScenarioService;
    }

    @Inject
    public void setSandboxImportService(SandboxImportService sandboxImportService) {
        this.sandboxImportService = sandboxImportService;
    }

    @Inject
    public void setSandboxActivityLogService(SandboxActivityLogService sandboxActivityLogService) {
        this.sandboxActivityLogService = sandboxActivityLogService;
    }

    @Inject
    public void setRuleService(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @Inject
    public void setUserAccessHistoryService(UserAccessHistoryService userAccessHistoryService) {
        this.userAccessHistoryService = userAccessHistoryService;
    }

    @Inject
    public void setHttpClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void delete(final int id) {
        repository.delete(id);
    }

    @Override
    @Transactional
    public void delete(final Sandbox sandbox, final String bearerToken, final User admin) {

        deleteAllSandboxItems(sandbox, bearerToken);

        List<SandboxImport> imports = sandbox.getImports();
        for (SandboxImport sandboxImport : imports) {
            sandboxImportService.delete(sandboxImport);
        }
        sandbox.setImports(null);
        save(sandbox);

        //remove user memberships
        removeAllMembers(sandbox);

        if (admin != null) {
            sandboxActivityLogService.sandboxDelete(sandbox, admin);
        } else {
            sandboxActivityLogService.sandboxDelete(sandbox, sandbox.getCreatedBy());
        }
        delete(sandbox.getId());

        try {
            callDeleteSandboxAPI(sandbox, bearerToken);
        } catch (Exception ex) {
            throw new SandboxDeleteFailedException(String.format("Failed to delete sandbox: %s %n %s", sandbox.getSandboxId(), ex.getMessage()), ex);
        }
    }

    @Override
    @Transactional
    public void delete(final Sandbox sandbox, final String bearerToken) {
        delete(sandbox, bearerToken, null);
    }

    private void deleteAllSandboxItems(final Sandbox sandbox, final String bearerToken) {

        deleteSandboxItemsExceptApps(sandbox, bearerToken);

        //delete all registered app, authClients, images
        List<App> apps = appService.findBySandboxId(sandbox.getSandboxId());
        for (App app : apps) {
            appService.delete(app);
        }
    }

    private void deleteSandboxItemsExceptApps(final Sandbox sandbox, final String bearerToken) {

        //delete launch scenarios, context params
        List<LaunchScenario> launchScenarios = launchScenarioService.findBySandboxId(sandbox.getSandboxId());
        for (LaunchScenario launchScenario : launchScenarios) {
            launchScenarioService.delete(launchScenario);
        }

        List<UserPersona> userPersonas = userPersonaService.findBySandboxId(sandbox.getSandboxId());
        for (UserPersona userPersona : userPersonas) {
            userPersonaService.delete(userPersona);
        }


        //remove sample patients from all apps
        List<App> apps = appService.findBySandboxId(sandbox.getSandboxId());
        for (App app : apps) {
            app.setSamplePatients(null);
            appService.save(app);
        }

        userAccessHistoryService.deleteUserAccessInstancesForSandbox(sandbox);
    }

    // TODO: create no longer used
    @Override
    @Transactional
    public Sandbox create(final Sandbox sandbox, final User user, final String bearerToken) throws UnsupportedEncodingException {

        Boolean canCreate = ruleService.checkIfUserCanCreateSandbox(user);
        if (!canCreate) {
            return null;
        }
        UserPersona userPersona = userPersonaService.findByPersonaUserId(user.getSbmUserId());

        if (userPersona == null && callCreateOrUpdateSandboxAPI(sandbox, bearerToken)) {
            sandbox.setCreatedBy(user);
            sandbox.setCreatedTimestamp(new Timestamp(new Date().getTime()));
            sandbox.setVisibility(Visibility.valueOf(defaultSandboxVisibility));
            // Set expiration date and message for R4 sandboxes
            if (sandbox.getApiEndpointIndex().equals("7")) {
                sandbox.setExpirationMessage(expirationMessage);
                sandbox.setExpirationDate(formatDate());
            }

            sandbox.setPayerUserId(user.getId());
            Sandbox savedSandbox = save(sandbox);
            addMember(savedSandbox, user, Role.ADMIN);
            for (String roleName : defaultSandboxCreatorRoles) {
                addMemberRole(sandbox, user, Role.valueOf(roleName));
            }
            sandboxActivityLogService.sandboxCreate(sandbox, user);
            return savedSandbox;
        }
        return null;
    }

    @Override
    @Transactional
    public Sandbox clone(final Sandbox newSandbox, final String clonedSandboxId, final User user, final String bearerToken) throws UnsupportedEncodingException {
        Boolean canCreate = ruleService.checkIfUserCanCreateSandbox(user);
        if (!canCreate) {
            return null;
        }
        UserPersona initialUserPersona = userPersonaService.findByPersonaUserId(user.getSbmUserId());
        Sandbox clonedSandbox = findBySandboxId(clonedSandboxId);
        Sandbox newSandboxExists = findBySandboxId(newSandbox.getSandboxId());
        if (newSandboxExists != null) {
            throw new ResourceNotFoundException("Sandbox with id " + newSandbox.getSandboxId() + " already exists.");
        }
        if (clonedSandbox == null) {
            throw new ResourceNotFoundException("Cloned sandbox does not exist.");
        }
        if (initialUserPersona == null) {// && callCloneSandboxApi(newSandbox, existingSandbox, bearerToken)) {
            newSandbox.setCreatedBy(user);
            newSandbox.setCreatedTimestamp(new Timestamp(new Date().getTime()));
            newSandbox.setVisibility(Visibility.valueOf(defaultSandboxVisibility));
            // Set expiration date and message for R4 sandboxes
            if (newSandbox.getApiEndpointIndex().equals("7")) {
                newSandbox.setExpirationMessage(expirationMessage);
                newSandbox.setExpirationDate(formatDate());
            }

            newSandbox.setPayerUserId(clonedSandbox.getPayerUserId());
            Sandbox savedSandbox = save(newSandbox);
            addMember(savedSandbox, user, Role.ADMIN);
            for (String roleName : defaultSandboxCreatorRoles) {
                addMemberRole(savedSandbox, user, Role.valueOf(roleName));
            }
            sandboxActivityLogService.sandboxCreate(newSandbox, user);
            if (newSandbox.getDataSet().equals(DataSet.DEFAULT)) {
                cloneUserPersonas(savedSandbox, clonedSandbox, user);
            }
            if (newSandbox.getApps().equals(DataSet.DEFAULT)) {
                if (newSandbox.getDataSet().equals(DataSet.DEFAULT)) {
                    cloneApps(savedSandbox, clonedSandbox, user);
                    cloneLaunchScenarios(savedSandbox, clonedSandbox, user);
                } else {
                    //Clone only the apps if the dataset is empty
                    cloneApps(savedSandbox, clonedSandbox, user);
                }
            }
            callCloneSandboxApi(newSandbox, clonedSandbox, bearerToken);
            return savedSandbox;
        }
        return null;
    }

    @Override
    @Transactional
    public Sandbox update(final Sandbox sandbox, final User user, final String bearerToken) throws UnsupportedEncodingException  {
        Sandbox existingSandbox = findBySandboxId(sandbox.getSandboxId());
        existingSandbox.setName(sandbox.getName());
        existingSandbox.setDescription(sandbox.getDescription());
        if (existingSandbox.isAllowOpenAccess() != sandbox.isAllowOpenAccess()) {
            sandboxActivityLogService.sandboxOpenEndpoint(existingSandbox, user, sandbox.isAllowOpenAccess());
            existingSandbox.setAllowOpenAccess(sandbox.isAllowOpenAccess());
            callCreateOrUpdateSandboxAPI(existingSandbox, bearerToken);
        }
        return save(existingSandbox);
    }

    @Override
    @Transactional
    public void removeMember(final Sandbox sandbox, final User user, final String bearerToken) {
        if (user != null) {
            userService.removeSandbox(sandbox, user);

            //delete launch scenarios, context params
            List<LaunchScenario> launchScenarios = launchScenarioService.findBySandboxIdAndCreatedBy(sandbox.getSandboxId(), user.getSbmUserId());
            launchScenarios.stream().filter(launchScenario -> launchScenario.getVisibility() == Visibility.PRIVATE).forEach(launchScenarioService::delete);

            //delete user launches for public launch scenarios in this sandbox
            List<UserLaunch> userLaunches = userLaunchService.findByUserId(user.getSbmUserId());
            userLaunches.stream().filter(userLaunch -> userLaunch.getLaunchScenario().getSandbox().getSandboxId().equalsIgnoreCase(sandbox.getSandboxId())).forEach(userLaunchService::delete);

            //delete all registered app, authClients, images
            List<App> apps = appService.findBySandboxIdAndCreatedBy(sandbox.getSandboxId(), user.getSbmUserId());
            apps.stream().filter(app -> app.getVisibility() == Visibility.PRIVATE).forEach(appService::delete);

            List<UserPersona> userPersonas = userPersonaService.findBySandboxIdAndCreatedBy(sandbox.getSandboxId(), user.getSbmUserId());
            userPersonas.stream().filter(userPersona -> userPersona.getVisibility() == Visibility.PRIVATE).forEach(userPersonaService::delete);

            List<UserRole> allUserRoles = sandbox.getUserRoles();
            List<UserRole> currentUserRoles = new ArrayList<>();
            Iterator<UserRole> iterator = allUserRoles.iterator();
            while (iterator.hasNext()) {
                UserRole userRole = iterator.next();
                if (userRole.getUser().getId().equals(user.getId())) {
                    currentUserRoles.add(userRole);
                    iterator.remove();
                }
            }
            if (!currentUserRoles.isEmpty()) {
                sandbox.setUserRoles(allUserRoles);
                save(sandbox);
                currentUserRoles.forEach(userRoleService::delete);
            }
            sandboxActivityLogService.sandboxUserRemoved(sandbox, sandbox.getCreatedBy(), user);
        }
    }

    @Override
    @Transactional
    public void addMember(final Sandbox sandbox, final User user) {
        String[] defaultRoles = sandbox.getVisibility() == Visibility.PUBLIC ? defaultPublicSandboxRoles : defaultPrivateSandboxRoles;
        for (String roleName : defaultRoles) {
            addMemberRole(sandbox, user, Role.valueOf(roleName));
        }
    }

    @Override
    @Transactional
    public void addMember(final Sandbox sandbox, final User user, final Role role) {
        List<UserRole> userRoles = sandbox.getUserRoles();
        userRoles.add(new UserRole(user, role));
        sandboxActivityLogService.sandboxUserRoleChange(sandbox, user, role, true);
        sandbox.setUserRoles(userRoles);
        userService.addSandbox(sandbox, user);
        sandboxActivityLogService.sandboxUserAdded(sandbox, user);
        save(sandbox);
    }

    @Override
    @Transactional
    public void addMemberRole(final Sandbox sandbox, final User user, final Role role) {
        if (hasMemberRole(sandbox, user, role)) {
            return;
        }
        if (!isSandboxMember(sandbox, user)) {
            addMember(sandbox, user, role);
        } else {
            List<UserRole> userRoles = sandbox.getUserRoles();
            userRoles.add(new UserRole(user, role));
            sandboxActivityLogService.sandboxUserRoleChange(sandbox, user, role, true);
            sandbox.setUserRoles(userRoles);
            save(sandbox);
        }
    }

    @Override
    @Transactional
    public void removeMemberRole(final Sandbox sandbox, final User user, final Role role) {
        if (isSandboxMember(sandbox, user)) {
            List<UserRole> allUserRoles = sandbox.getUserRoles();
            UserRole removeUserRole = null;
            Iterator<UserRole> iterator = allUserRoles.iterator();
            while (iterator.hasNext()) {
                UserRole userRole = iterator.next();
                if (userRole.getUser().getId().equals(user.getId()) &&
                        userRole.getRole().equals(role)) {
                    removeUserRole = userRole;
                    iterator.remove();
                }
            }
            if (removeUserRole != null) {
                sandbox.setUserRoles(allUserRoles);
                save(sandbox);
                userRoleService.delete(removeUserRole);
            }
        }
    }

    @Override
    @Transactional
    public void changePayerForSandbox(final Sandbox sandbox, final User payer) {
        sandbox.setPayerUserId(payer.getId());
        save(sandbox);
    }

    @Override
    public boolean hasMemberRole(final Sandbox sandbox, final User user, final Role role) {
        List<UserRole> userRoles = sandbox.getUserRoles();
        for(UserRole userRole : userRoles) {
            if (userRole.getUser().getSbmUserId().equalsIgnoreCase(user.getSbmUserId()) && userRole.getRole() == role) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addSandboxImport(final Sandbox sandbox, final SandboxImport sandboxImport) {
        List<SandboxImport> imports = sandbox.getImports();
        imports.add(sandboxImport);
        sandbox.setImports(imports);
        save(sandbox);
    }

    @Override
    public void reset(final Sandbox sandbox, final String bearerToken) {
        deleteSandboxItemsExceptApps(sandbox, bearerToken);
    }

    @Override
    public boolean isSandboxMember(final Sandbox sandbox, final User user) {
        for(UserRole userRole : sandbox.getUserRoles()) {
            if (userRole.getUser().getSbmUserId().equalsIgnoreCase(user.getSbmUserId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional
    public void sandboxLogin(final String sandboxId, final String userId) {
        Sandbox sandbox = findBySandboxId(sandboxId);
        User user = userService.findBySbmUserId(userId);
        if (isSandboxMember(sandbox, user)) {
            sandboxActivityLogService.sandboxLogin(sandbox, user);
        }
    }

    @Override
    @Transactional
    public Sandbox save(final Sandbox sandbox) {
        return repository.save(sandbox);
    }

    @Override
    public List<Sandbox> getAllowedSandboxes(final User user) {
        List<Sandbox> sandboxes = new ArrayList<>();
        if (user != null) {
            sandboxes = user.getSandboxes();
        }

        for (Sandbox sandbox : findByVisibility(Visibility.PUBLIC)){
            if (!sandboxes.contains(sandbox)){
                sandboxes.add(sandbox);
            }
        }

        return sandboxes;
    }

    @Override
    public Sandbox findBySandboxId(final String sandboxId) {
        return repository.findBySandboxId(sandboxId);
    }

    @Override
    public List<Sandbox> findByVisibility(final Visibility visibility) {
        return repository.findByVisibility(visibility);
    }

    @Override
    public String fullCount() {
        return repository.fullCount();
    }

    @Override
    public String schemaCount(String apiEndpointIndex) {
        return repository.schemaCount(apiEndpointIndex);
    }

    @Override
    public String intervalCount(Timestamp intervalTime) {
        return repository.intervalCount(intervalTime);
    }

    @Override
    public List<Sandbox> findByPayerId(Integer payerId) {
        return repository.findByPayerUserId(payerId);
    }

    @Override
    public String getSandboxApiURL(final Sandbox sandbox) {
        return getApiSchemaURL(sandbox.getApiEndpointIndex()) + "/" + sandbox.getSandboxId();
    }

    private void removeAllMembers(final Sandbox sandbox) {

        List<UserRole> userRoles = sandbox.getUserRoles();
        sandbox.setUserRoles(Collections.<UserRole>emptyList());
        save(sandbox);

        for(UserRole userRole : userRoles) {
            userService.removeSandbox(sandbox, userRole.getUser());
            userRoleService.delete(userRole);
        }
    }

    private String getApiSchemaURL(final String apiEndpointIndex) {
        String url;
        switch (apiEndpointIndex){
            case "1":
                url = apiBaseURL_1;
                break;
            case "2":
                url = apiBaseURL_2;
                break;
            case "3":
                url = apiBaseURL_3;
                break;
            case "4":
                url = apiBaseURL_4;
                break;
            case "5":
                url = apiBaseURL_5;
                break;
            case "6":
                url = apiBaseURL_6;
                break;
            default:
                url = apiBaseURL_7;
        }
        return url;
    }

    private boolean callCreateOrUpdateSandboxAPI(final Sandbox sandbox, final String bearerToken ) throws UnsupportedEncodingException {
        String url = getSandboxApiURL(sandbox) + "/sandbox";
        if (!sandbox.getDataSet().equals(DataSet.NA)){
            url = getSandboxApiURL(sandbox) + "/sandbox?dataSet=" + sandbox.getDataSet();
        }

        // TODO: change to using 'simpleRestTemplate'
        HttpPut putRequest = new HttpPut(url);
        putRequest.addHeader("Content-Type", "application/json");
        StringEntity entity;

        String jsonString = "{\"teamId\": \"" + sandbox.getSandboxId() + "\",\"allowOpenAccess\": \"" + sandbox.isAllowOpenAccess() + "\"}";
        entity = new StringEntity(jsonString);
        putRequest.setEntity(entity);
        putRequest.setHeader("Authorization", "BEARER " + bearerToken);

        try (CloseableHttpResponse closeableHttpResponse = httpClient.execute(putRequest)) {
            if (closeableHttpResponse.getStatusLine().getStatusCode() != 200) {
                HttpEntity rEntity = closeableHttpResponse.getEntity();
                String responseString = EntityUtils.toString(rEntity, StandardCharsets.UTF_8);
                String errorMsg = String.format("There was a problem creating the sandbox.\n" +
                                "Response Status : %s .\nResponse Detail :%s. \nUrl: :%s",
                        closeableHttpResponse.getStatusLine(),
                        responseString,
                        url);
                LOGGER.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            return true;
        } catch (IOException e) {
            LOGGER.error("Error posting to " + url, e);
            throw new RuntimeException(e);
        } finally {
//            try {
//                httpClient.close();
//            }catch (IOException e) {
//                LOGGER.error("Error closing HttpClient");
//            }
        }
    }

    private boolean callCloneSandboxApi(final Sandbox newSandbox, final Sandbox clonedSandbox, final String bearerToken) throws UnsupportedEncodingException {
        String url = getSandboxApiURL(newSandbox) + "/sandbox/clone";

        // TODO: change to using 'simpleRestTemplate'
        HttpPut putRequest = new HttpPut(url);
        putRequest.addHeader("Content-Type", "application/json");
        StringEntity entity;

        String jsonString = "{\"newSandbox\": {" +
                "\"teamId\": \"" + newSandbox.getSandboxId() +
                "\",\"allowOpenAccess\": \"" + newSandbox.isAllowOpenAccess() + "\"" +
                "}," +
                "\"clonedSandbox\": {" +
                "\"teamId\": \"" + clonedSandbox.getSandboxId() +
                "\",\"allowOpenAccess\": \"" + clonedSandbox.isAllowOpenAccess() + "\"" +
                "}" +
                "}";
        entity = new StringEntity(jsonString);
        putRequest.setEntity(entity);
        putRequest.setHeader("Authorization", "BEARER " + bearerToken);

        try (CloseableHttpResponse closeableHttpResponse = httpClient.execute(putRequest)) {
            if (closeableHttpResponse.getStatusLine().getStatusCode() != 200) {
                HttpEntity rEntity = closeableHttpResponse.getEntity();
                String responseString = EntityUtils.toString(rEntity, StandardCharsets.UTF_8);
                String errorMsg = String.format("There was a problem cloning the sandbox.\n" +
                                "Response Status : %s .\nResponse Detail :%s. \nUrl: :%s",
                        closeableHttpResponse.getStatusLine(),
                        responseString,
                        url);
                LOGGER.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            return true;
        } catch (IOException e) {
            LOGGER.error("Error posting to " + url, e);
            throw new RuntimeException(e);
        } finally {
//            try {
//                httpClient.close();
//            } catch (IOException e) {
//                LOGGER.error("Error closing HttpClient");
//            }
        }
    }

    private boolean callDeleteSandboxAPI(final Sandbox sandbox, final String bearerToken ) {
        String url = getSandboxApiURL(sandbox) + "/sandbox";

        // TODO: change to using 'simpleRestTemplate'
        HttpDelete deleteRequest = new HttpDelete(url);
        deleteRequest.addHeader("Authorization", "BEARER " + bearerToken);

        try (CloseableHttpResponse closeableHttpResponse = httpClient.execute(deleteRequest)) {
            if (closeableHttpResponse.getStatusLine().getStatusCode() != 200) {
                HttpEntity rEntity = closeableHttpResponse.getEntity();
                String responseString = EntityUtils.toString(rEntity, StandardCharsets.UTF_8);
                String errorMsg = String.format("There was a problem deleting the sandbox.\n" +
                                "Response Status : %s .\nResponse Detail :%s. \nUrl: :%s",
                        closeableHttpResponse.getStatusLine(),
                        responseString,
                        url);
                LOGGER.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            return true;
        } catch (IOException e) {
            LOGGER.error("Error deleting to " + url, e);
            throw new RuntimeException(e);
        } finally {
//            try {
//                httpClient.close();
//            }catch (IOException e) {
//                LOGGER.error("Error closing HttpClient");
//            }
        }
    }

    private java.sql.Date formatDate() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        try {
            Date parsed = format.parse(expirationDate);
            return new java.sql.Date(parsed.getTime());
        } catch (ParseException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            return null;
        }
    }

    private void cloneUserPersonas(Sandbox newSandbox, Sandbox existingSandbox, User user) {
        List<UserPersona> userPersonas = userPersonaService.findBySandboxId(existingSandbox.getSandboxId());
        for (UserPersona userPersona: userPersonas) {
            UserPersona newUserPersona = new UserPersona();
            newUserPersona.setPassword(userPersona.getPassword());
            newUserPersona.setSandbox(newSandbox);
            String[] personaSplit = userPersona.getPersonaUserId().split("@");
            newUserPersona.setPersonaUserId(personaSplit[0] + "@" + newSandbox.getSandboxId());
            newUserPersona.setCreatedBy(user);
            newUserPersona.setCreatedTimestamp(new Timestamp(new Date().getTime()));
            newUserPersona.setVisibility(userPersona.getVisibility());
            newUserPersona.setFhirId(userPersona.getFhirId());
            newUserPersona.setFhirName(userPersona.getFhirName());
            newUserPersona.setPersonaName(userPersona.getPersonaName());
            newUserPersona.setResource(userPersona.getResource());
            newUserPersona.setResourceUrl(userPersona.getResourceUrl());
            userPersonaService.save(newUserPersona);
        }
    }

    private void cloneApps(Sandbox newSandbox, Sandbox existingSandbox, User user) {
        List<App> clonedSmartApps = appService.findBySandboxId(existingSandbox.getSandboxId());
        for (App app: clonedSmartApps) {
            App newApp = new App();
            newApp.setBriefDescription(app.getBriefDescription());
            newApp.setClientId(app.getClientId());
            newApp.setCreatedTimestamp(new Timestamp(new Date().getTime()));
            newApp.setAuthor(app.getAuthor());
            newApp.setInfo(app.getInfo());
            newApp.setManifestUrl(app.getManifestUrl());
            newApp.setSamplePatients(app.getSamplePatients());
            newApp.setCreatedBy(user);
            newApp.setLogoUri(app.getLogoUri());
            newApp.setLogo(app.getLogo());
            newApp.setLaunchUri(app.getLaunchUri());
            newApp.setClientName(app.getClientName());
            newApp.setClientUri(app.getClientUri());
            newApp.setFhirVersions(app.getFhirVersions());

            newApp.setSandbox(newSandbox);
            newApp.setVisibility(Visibility.PRIVATE);
            newApp.setCopyType(CopyType.REPLICA);

            appService.save(newApp);
        }

    }

    private void cloneLaunchScenarios(Sandbox newSandbox, Sandbox existingSandbox, User user) {
        List<LaunchScenario> launchScenarios = launchScenarioService.findBySandboxId(existingSandbox.getSandboxId());
        for (LaunchScenario launchScenario: launchScenarios) {
            LaunchScenario newLaunchScenario = new LaunchScenario();
            newLaunchScenario.setSandbox(newSandbox);
            newLaunchScenario.setPatient(launchScenario.getPatient());
            newLaunchScenario.setPatientName(launchScenario.getPatientName());
            newLaunchScenario.setNeedPatientBanner(launchScenario.getNeedPatientBanner());
            newLaunchScenario.setLocation(launchScenario.getLocation());
            newLaunchScenario.setEncounter(launchScenario.getEncounter());
            newLaunchScenario.setIntent(launchScenario.getIntent());
            newLaunchScenario.setResource(launchScenario.getResource());
            newLaunchScenario.setSmartStyleUrl(launchScenario.getSmartStyleUrl());
            newLaunchScenario.setLastLaunchSeconds(launchScenario.getLastLaunchSeconds());
            newLaunchScenario.setApp(launchScenario.getApp());
            List<ContextParams> contextParamsList = launchScenario.getContextParams();
            List<ContextParams> newContextParamsList = new ArrayList<>();
            for (ContextParams contextParams: contextParamsList) {
                ContextParams newContextParams = new ContextParams();
                newContextParams.setName(contextParams.getName());
                newContextParams.setValue(contextParams.getValue());
                newContextParamsList.add(newContextParams);
            }
            newLaunchScenario.setContextParams(newContextParamsList);
            newLaunchScenario.setCreatedBy(user);
            newLaunchScenario.setCreatedTimestamp(new Timestamp(new Date().getTime()));
            newLaunchScenario.setDescription(launchScenario.getDescription());
            newLaunchScenario.setTitle(launchScenario.getTitle());
            String personaId = launchScenario.getUserPersona().getPersonaUserId().split("@")[0] + "@" + newLaunchScenario.getSandbox().getSandboxId();
            newLaunchScenario.setUserPersona(userPersonaService.findByPersonaUserIdAndSandboxId(personaId, newLaunchScenario.getSandbox().getSandboxId()));
            newLaunchScenario.setVisibility(launchScenario.getVisibility());
            launchScenarioService.save(newLaunchScenario);
        }
    }

}


