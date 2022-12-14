package org.logicahealth.sandboxmanagerapi.services.impl;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.GsonBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.logicahealth.sandboxmanagerapi.model.*;
import org.logicahealth.sandboxmanagerapi.repositories.SandboxRepository;
import org.logicahealth.sandboxmanagerapi.services.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class SandboxExportServiceImpl implements SandboxExportService {

    private final String s3BucketName;
    private final AmazonS3 amazonS3;
    private final SandboxRepository repository;
    private final FhirProfileService fhirProfileService;
    private final CdsHookService cdsHookService;
    private final SandboxInviteService sandboxInviteService;
    private final AppService appService;
    private final UserPersonaService userPersonaService;
    private final CdsServiceEndpointService cdsServiceEndpointService;
    private final LaunchScenarioService launchScenarioService;
    private final FhirProfileDetailService fhirProfileDetailService;
    private final EmailService emailService;
    private final SandboxEncryptionService sandboxEncryptionService;

    private static Logger LOGGER = LoggerFactory.getLogger(SandboxExportServiceImpl.class.getName());

    private static final String FHIR_SERVER_VERSION = "platformVersion";
    private static final String SANDBOX_SERVER_URL = "server";
    private static final String HAPI_VERSION = "hapiVersion";
    private static final String FHIR_VERSION = "fhirVersion";
    private static final String IMAGE_FOLDER = "img/";
    private static final String PATIENT_FHIR_QUERY = "Patient?_id=";
    private static final String SANDBOX_DOWNLOAD_URI = "/sandbox/download";
    private static final String IMAGE_MIME_TYPE = "image/";
    private static final String IMAGE_NAME_PREFIX = "img";
    private static final int DOWNLOAD_LINK_VALID_DAYS = 2;

    public SandboxExportServiceImpl(@Value("${aws.s3BucketName}") String s3BucketName, AmazonS3 amazonS3, SandboxRepository repository, FhirProfileService fhirProfileService, CdsHookService cdsHookService, SandboxInviteService sandboxInviteService, AppService appService, UserPersonaService userPersonaService, CdsServiceEndpointService cdsServiceEndpointService, LaunchScenarioService launchScenarioService, FhirProfileDetailService fhirProfileDetailService, UserService userService, EmailService emailService, SandboxEncryptionService sandboxEncryptionService) {
        this.s3BucketName = s3BucketName;
        this.amazonS3 = amazonS3;
        this.repository = repository;
        this.fhirProfileService = fhirProfileService;
        this.cdsHookService = cdsHookService;
        this.sandboxInviteService = sandboxInviteService;
        this.appService = appService;
        this.userPersonaService = userPersonaService;
        this.cdsServiceEndpointService = cdsServiceEndpointService;
        this.launchScenarioService = launchScenarioService;
        this.fhirProfileDetailService = fhirProfileDetailService;
        this.emailService = emailService;
        this.sandboxEncryptionService = sandboxEncryptionService;
    }

    @Override
    public Runnable createZippedSandboxExport(Sandbox sandbox, String sbmUserId, String bearerToken, String apiUrl, PipedOutputStream pipedOutputStream, String server) {
        return () -> {
            var zipOutputStream = new ZipOutputStream(pipedOutputStream);
            addSandboxFhirServerDetailsToZipFile(sandbox, zipOutputStream, bearerToken, apiUrl, server);
            addSandboxUserRolesAndInviteesToZipFile(sandbox.getSandboxId(), zipOutputStream);
            var appsManifests = addAppsManifestToZipFile(sandbox.getSandboxId(), sbmUserId, zipOutputStream);
            addUserPersonasToZipFile(sandbox.getSandboxId(), sbmUserId, zipOutputStream);
            addCdsHooksToZipFile(sandbox.getSandboxId(), sbmUserId, zipOutputStream);
            addLaunchScenariosToZipFile(sandbox.getSandboxId(), sbmUserId, zipOutputStream, appsManifests);
            addProfilesToZipFile(sandbox.getSandboxId(), zipOutputStream);
            try {
                zipOutputStream.flush();
                zipOutputStream.close();
            } catch (IOException e) {
                LOGGER.error("Exception while creating zip output stream for sandbox export", e);
                throw new RuntimeException(e);
            }
        };
    }

    @Override
    public Runnable sendToS3Bucket(PipedInputStream pipedInputStream, String sandboxExportFileName, User user, String sandboxName) {
        return () -> {
            var transferManager = TransferManagerBuilder.standard()
                                                        .withS3Client(this.amazonS3)
                                                        .build();
            try {
                transferManager.upload(this.s3BucketName, sandboxExportFileName, pipedInputStream, new ObjectMetadata());
                LOGGER.debug("Exported sandbox url: " + this.amazonS3.generatePresignedUrl(this.s3BucketName, sandboxExportFileName, getDownloadLinkValidUntilDate()));
            } catch(AmazonClientException e) {
                LOGGER.error("Exception while uploading sandbox to s3 bucket", e);
            }
            emailService.sendExportNotificationEmail(user, this.amazonS3.generatePresignedUrl(this.s3BucketName, sandboxExportFileName, getDownloadLinkValidUntilDate()), sandboxName);
        };
    }

    private Date getDownloadLinkValidUntilDate() {
        var downloadLinkValidUntil = Calendar.getInstance();
        downloadLinkValidUntil.setTime(new Date());
        downloadLinkValidUntil.add(Calendar.DATE, DOWNLOAD_LINK_VALID_DAYS);
        return downloadLinkValidUntil.getTime();
    }

    private void addSandboxFhirServerDetailsToZipFile(Sandbox sandbox, ZipOutputStream zipOutputStream, String bearerToken, String apiUrl, String server) {
        var response = getClientResponse(sandbox, bearerToken, apiUrl);
        ZipInputStream zipInputStream = null;
        try (var osPipe = new PipedOutputStream();
             var isPipe = new PipedInputStream(osPipe)) {
            Flux<DataBuffer> body = response.body(BodyExtractors.toDataBuffers());
            DataBufferUtils.write(body, osPipe)
                           .subscribe(DataBufferUtils.releaseConsumer());
            zipInputStream = new ZipInputStream(isPipe);
            zipInputStream.getNextEntry();
            addSandboxDetailsToZipFile(sandbox.getSandboxId(), zipOutputStream, Objects.requireNonNull(getZipEntryContents(zipInputStream)), apiUrl, server);
            addSchemaHashToZipFile(zipInputStream, zipInputStream.getNextEntry(), zipOutputStream);
            addZipFileEntry(zipInputStream, zipInputStream.getNextEntry(), zipOutputStream);
        } catch (IOException e) {
            LOGGER.error("Exception while adding fhir server details for sandbox export", e);
            throw new RuntimeException(e);
        } finally {
            try {
                if (zipInputStream != null) {
                    zipInputStream.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void addSchemaHashToZipFile(ZipInputStream zipInputStream, ZipEntry zipEntry, ZipOutputStream zipOutputStream) {
        try {
            var schemaHash = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
            var signature = sandboxEncryptionService.encrypt(schemaHash);
            zipOutputStream.putNextEntry(new ZipEntry("schemaSignature"));
            zipOutputStream.write(signature.getBytes());
        } catch (IOException e) {
            LOGGER.error("Exception while adding zip file entry for schema signature", e);
        }
    }

    private void addSandboxUserRolesAndInviteesToZipFile(String sandboxId, ZipOutputStream zipOutputStream) {
        var sandbox = this.repository.findBySandboxId(sandboxId);
        var sandboxUsers = sandbox.getUserRoles()
                                  .stream()
                                  .map(userRole -> new SandboxExportServiceImpl.SandboxUser(userRole.getUser(), userRole.getRole().getSandboxDownloadRole()))
                                  .collect(Collectors.toSet());
        var sandboxInvites = sandboxInviteService.findInvitesBySandboxId(sandboxId);
        var pendingInviteeEmails = sandboxInvites.stream()
                                                 .filter(sandboxInvite -> sandboxInvite.getStatus() == InviteStatus.PENDING)
                                                 .map(sandboxInvite -> sandboxInvite.getInvitee().getEmail())
                                                 .collect(Collectors.toList());
        var sandboxUserRolesAndInvitees = new HashMap<String, Object>();
        sandboxUsers.add(new SandboxExportServiceImpl.SandboxUser(sandbox.getCreatedBy(), "owner"));
        sandboxUserRolesAndInvitees.put("users", sandboxUsers);
        sandboxUserRolesAndInvitees.put("invitees", pendingInviteeEmails);
        try (var sandboxInputStream = new ByteArrayInputStream(new GsonBuilder().setPrettyPrinting()
                                                                                .create()
                                                                                .toJson(sandboxUserRolesAndInvitees).getBytes())) {
            addZipFileEntry(sandboxInputStream, new ZipEntry("users.json"), zipOutputStream);
        } catch (IOException e) {
            LOGGER.error("Exception while adding sandbox user roles and invites for sandbox download", e);
        }
        addSandboxUsersToZipFile(sandboxUsers, zipOutputStream);
    }

    private ClientResponse getClientResponse(Sandbox sandbox, String bearerToken, String apiUrl) {
        String url = apiUrl + SandboxExportServiceImpl.SANDBOX_DOWNLOAD_URI + "/" + sandbox.getSandboxId();
        var webClient = WebClient.builder()
                                 .baseUrl(apiUrl)
                                 .defaultHeader("Authorization", "BEARER " + bearerToken)
                                 .build();
        var response = webClient.get()
                                .uri(SandboxExportServiceImpl.SANDBOX_DOWNLOAD_URI + "/" + sandbox.getSandboxId())
                                .accept(MediaType.APPLICATION_OCTET_STREAM)
                                .exchange()
                                .block();
        if (response == null) {
            var errorMessage = "No response from fhir server for sandbox " + sandbox.getSandboxId();
            LOGGER.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }
        var statusCode = response.statusCode();
        if (statusCode != HttpStatus.OK) {
            String errorMsg = String.format("There was a problem downloading the sandbox.\n" +
                            "Response Status : %s .\nUrl: :%s",
                    statusCode.toString(),
                    url);
            LOGGER.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        return response;
    }

    private void addZipFileEntry(InputStream inputStream, ZipEntry zipEntry, ZipOutputStream zipOutputStream) {
        try {
            zipOutputStream.putNextEntry(zipEntry);
            IOUtils.copyLarge(inputStream, zipOutputStream);
        } catch (IOException e) {
            LOGGER.error("Exception while adding zip file entry for sandbox download", e);
        }
    }

    private Map<String, String> getZipEntryContents(ZipInputStream inputStream) {
        try (var outputStream = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[1024];
            int length;
            while ((length = inputStream.read(bytes)) >= 0) {
                outputStream.write(bytes, 0, length);
            }
            return convertFhirVersionsJsonStringToMap(outputStream.toString());
        } catch (IOException e) {
            LOGGER.error("Exception while extracting fhir server versions json", e);
        }
        return null;
    }

    private Map<String, String> convertFhirVersionsJsonStringToMap(String fhirServerVersions) {
        var fhirServerVersionsMap = new HashMap<String, String>();
        var jsonObject = new JSONObject(fhirServerVersions);
        fhirServerVersionsMap.put(FHIR_SERVER_VERSION, jsonObject.getString(FHIR_SERVER_VERSION));
        fhirServerVersionsMap.put(HAPI_VERSION, jsonObject.getString(HAPI_VERSION));
        fhirServerVersionsMap.put(FHIR_VERSION, jsonObject.getString(FHIR_VERSION));
        return fhirServerVersionsMap;
    }

    private void addSandboxDetailsToZipFile(String sandboxId, ZipOutputStream zipOutputStream, Map<String, String> fhirServerVersions, String sandboxApiURL, String server) {
        var sandbox = this.repository.findBySandboxId(sandboxId);
        var sandboxDetails = new HashMap<String, Object>();
        sandboxDetails.put("id", sandbox.getSandboxId());
        sandboxDetails.put("name", sandbox.getName());
        sandboxDetails.put("description", sandbox.getDescription());
        sandboxDetails.put("base", sandboxApiURL.substring(0, sandboxApiURL.length() - sandboxId.length() - 1));
        sandboxDetails.put(FHIR_SERVER_VERSION, fhirServerVersions.get(FHIR_SERVER_VERSION));
        sandboxDetails.put(HAPI_VERSION, fhirServerVersions.get(HAPI_VERSION));
        sandboxDetails.put(FHIR_VERSION, fhirServerVersions.get(FHIR_VERSION));
        sandboxDetails.put(SANDBOX_SERVER_URL, server);
        try (var sandboxInputStream = new ByteArrayInputStream(new GsonBuilder().setPrettyPrinting()
                                                                                .create()
                                                                                .toJson(sandboxDetails)
                                                                                .getBytes())) {
            addZipFileEntry(sandboxInputStream, new ZipEntry("sandbox.json"), zipOutputStream);
        } catch (IOException e) {
            LOGGER.error("Exception while adding sandbox details for sandbox download", e);
        }
    }

    private void addSandboxUsersToZipFile(Set<SandboxExportServiceImpl.SandboxUser> sandboxUsers, ZipOutputStream zipOutputStream) {
        var users = sandboxUsers.stream()
                                .map(SandboxUser::getEmail)
                                .distinct()
                                .collect(Collectors.joining(","));
        try (var usersInputStream = new ByteArrayInputStream(users.getBytes())) {
            addZipFileEntry(usersInputStream, new ZipEntry("users.csv"), zipOutputStream);
        } catch (IOException e) {
            LOGGER.error("Exception while adding users for sandbox download", e);
        }
    }

    @Getter
    private static class SandboxUser {
        private final String name;
        private final String email;
        private final String role;

        public SandboxUser(User user, String role) {
            this.name = user.getName();
            this.email = user.getEmail();
            this.role = role;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof SandboxUser)) {
                return false;
            }
            var otherSandboxUser = (SandboxUser) other;
            return (this.name.equals(otherSandboxUser.name) && this.email.equals(otherSandboxUser.email) && this.role.equals(otherSandboxUser.role));
        }

        @Override
        public int hashCode() {
            return this.email.hashCode() + this.email.hashCode() + this.role.hashCode();
        }
    }

    private List<AppManifestTemplate> addAppsManifestToZipFile(String sandboxId, String sbmUserId, ZipOutputStream zipOutputStream) {
        var apps = appService.findBySandboxIdAndCreatedByOrVisibility(sandboxId, sbmUserId, Visibility.PUBLIC);
        var appsList = parseAppsListJson(apps);
        addAppImagesToZipFile(appsList, zipOutputStream);
        try (var inputStream = new ByteArrayInputStream(new GsonBuilder().setPrettyPrinting()
                                                                         .create()
                                                                         .toJson(appsList)
                                                                         .getBytes())) {
            addZipFileEntry(inputStream, new ZipEntry("apps.json"), zipOutputStream);
            return appsList;
        } catch (IOException e) {
            LOGGER.error("Exception while adding apps manifest for sandbox download", e);
        }
        return null;
    }

    private List<AppManifestTemplate> parseAppsListJson(List<App> apps) {
        var appManifests = new ArrayList<AppManifestTemplate>();
        int imageCounter = 0;
        for (App app : apps) {
            var appManifestTemplate = new AppManifestTemplate(++imageCounter, app.getSoftwareId(), app.getClientName(), app.getClientUri(), app.getLogoUri(), app.getLaunchUri(), app.getFhirVersions(), app.getBriefDescription(), app.getSamplePatients());
            try {
                var clientJsonNode = new ObjectMapper().readTree(app.getClientJSON());
                appManifestTemplate.setAppId(clientJsonNode.get("id").asText());
                appManifestTemplate.setClientId(clientJsonNode.get("clientId").asText());
                var redirectUrisIterator = clientJsonNode.get("redirectUris").elements();
                var redirectUris = new ArrayList<String>();
                while (redirectUrisIterator.hasNext()) {
                    redirectUris.add(redirectUrisIterator.next().asText());
                }
                appManifestTemplate.setRedirectUris(redirectUris);
                var scopeIterator = clientJsonNode.get("scope").elements();
                appManifestTemplate.setScope(new ArrayList<>());
                while (scopeIterator.hasNext()) {
                    appManifestTemplate.getScope().add(scopeIterator.next().asText());
                }
                appManifestTemplate.setTokenEndpointAuthMethod(clientJsonNode.get("tokenEndpointAuthMethod").asText());
                appManifests.add(appManifestTemplate);
            } catch (JsonProcessingException e) {
                LOGGER.error("Exception while parsing application client json for sandbox download", e);
            }
        }
        return appManifests;
    }

    @NoArgsConstructor
    @Data
    public static class AppManifestTemplate {
        private transient int imageCounter;
        private transient String appId;
        private String softwareId;
        private String clientId;
        private String clientName;
        private String clientUri;
        private String logo;
        private transient String logoFileName;
        private String launchUri;
        private List<String> redirectUris;
        private List<String> scope;
        private transient String tokenEndpointAuthMethod;
        private String fhirVersions;
        private String description;
        private List<String> samplePatients;

        public AppManifestTemplate(int imageCounter, String softwareId, String clientName, String clientUri, String logo, String launchUri, String fhirVersions, String description, String samplePatients) {
            this.imageCounter = imageCounter;
            this.softwareId = softwareId;
            this.clientName = clientName;
            this.clientUri = clientUri;
            this.logo = logo;
            this.launchUri = launchUri;
            this.fhirVersions = fhirVersions;
            this.description = description;
            if (samplePatients != null) {
                var samplePatientsArray = samplePatients.split(",");
                this.samplePatients = new ArrayList<>(samplePatientsArray.length);
                for (String patient : samplePatientsArray) {
                    this.samplePatients.add(stripFhirQuery(patient));
                }
            }
            setLogoFileName();
        }

        private String stripFhirQuery(String patient) {
            return patient.substring(patient.contains(PATIENT_FHIR_QUERY) ? patient.indexOf(PATIENT_FHIR_QUERY) + PATIENT_FHIR_QUERY.length() : 0);
        }

        private void setLogoFileName() {
            if (this.logo == null) {
                return;
            }
            var logoFilename = this.logo.substring(this.logo.lastIndexOf("/") + 1);
            this.logoFileName = IMAGE_NAME_PREFIX + this.imageCounter;
            if (logoFilename.contains(".")) {
                this.logoFileName += logoFilename.substring(logoFilename.indexOf("."));
                return;
            }
            var pushbackLimit = 100;
            try (var urlStream = new URL(this.logo).openStream();
                 var pushUrlStream = new PushbackInputStream(urlStream, pushbackLimit)) {
                var firstBytes = new byte[pushbackLimit];
                pushUrlStream.read(firstBytes);
                var byteArrayInputStream = new ByteArrayInputStream(firstBytes);
                var mimeType = URLConnection.guessContentTypeFromStream(byteArrayInputStream);
                if (mimeType.startsWith(IMAGE_MIME_TYPE)) {
                    var imageType = mimeType.substring(IMAGE_MIME_TYPE.length());
                    this.logoFileName += "." + imageType;
                }
            } catch (IOException e) {
                LOGGER.error("Exception while accessing app logo image url connection for sandbox download", e);
            }
        }

        public String getLogoFileName() {
            return this.logoFileName;
        }

        public InputStream getLogoInputStream() {
            try {
                return new URL(this.logo).openStream();
            } catch (IOException e) {
                LOGGER.error("Exception while accessing app logo image for sandbox download", e);
            }
            return null;
        }
    }

    private void addAppImagesToZipFile(List<AppManifestTemplate> appsList, ZipOutputStream zipOutputStream) {
        var fileToInputStreamMapping = appsList.stream()
                                               .filter(app -> app.getLogoFileName() != null)
                                               .collect(Collectors.toMap(AppManifestTemplate::getLogoFileName, AppManifestTemplate::getLogoInputStream));
        fileToInputStreamMapping.forEach((fileName, inputStream) -> addZipFileEntry(inputStream, new ZipEntry(IMAGE_FOLDER + fileName), zipOutputStream));
        appsList.forEach(app -> app.setLogo(IMAGE_FOLDER + app.getLogoFileName()));
    }

    private void addUserPersonasToZipFile(String sandboxId, String sbmUserId, ZipOutputStream zipOutputStream) {
        var userPersonas = userPersonaService.findBySandboxIdAndCreatedByOrVisibility(sandboxId, sbmUserId, Visibility.PUBLIC);
        var sandboxUserPersona = userPersonas.stream()
                                             .map(SandboxUserPersona::new)
                                             .collect(Collectors.toList());
        try (var inputStream = new ByteArrayInputStream(new GsonBuilder().setPrettyPrinting()
                                                                         .create()
                                                                         .toJson(sandboxUserPersona)
                                                                         .getBytes())) {
            addZipFileEntry(inputStream, new ZipEntry("personas.json"), zipOutputStream);
        } catch (IOException e) {
            LOGGER.error("Exception while adding personas for sandbox download", e);
        }
    }

    @Getter
    public static class SandboxUserPersona {
        private final String fhirName;
        private final String personaUserId;
        private final String password;
        private final String resourceUrl;

        public SandboxUserPersona(UserPersona userPersona) {
            this.fhirName = userPersona.getFhirName();
            this.personaUserId = userPersona.getPersonaUserId();
            this.password = userPersona.getPassword();
            this.resourceUrl = userPersona.getResourceUrl();
        }
    }

    private void addCdsHooksToZipFile(String sandboxId, String sbmUserId, ZipOutputStream zipOutputStream) {
        var cdsServiceEndpoints = cdsServiceEndpointService.findBySandboxIdAndCreatedByOrVisibility(sandboxId, sbmUserId, Visibility.PUBLIC);
        for (CdsServiceEndpoint cdsServiceEndpoint : cdsServiceEndpoints) {
            List<CdsHook> cdsHooks = cdsHookService.findByCdsServiceEndpointId(cdsServiceEndpoint.getId());
            cdsServiceEndpoint.setCdsHooks(cdsHooks);
        }
        var sandboxCdsServiceEndpoints = cdsServiceEndpoints.stream()
                                                            .map(SandboxCdsServiceEndpoint::new)
                                                            .collect(Collectors.toList());
        try (var inputStream = new ByteArrayInputStream(new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
                                                                          .writerWithDefaultPrettyPrinter()
                                                                          .writeValueAsBytes(sandboxCdsServiceEndpoints))) {
            addZipFileEntry(inputStream, new ZipEntry("cds-hooks.json"), zipOutputStream);
        } catch (IOException e) {
            LOGGER.error("Exception while adding cds hooks for sandbox download", e);
        }
    }

    @Getter
    private static class SandboxCdsServiceEndpoint {
        private final String url;
        private final String title;
        private final String description;
        private final List<SandboxCdsHook> cdsHooks;

        public SandboxCdsServiceEndpoint(CdsServiceEndpoint cdsServiceEndpoint) {
            this.url = cdsServiceEndpoint.getUrl();
            this.title = cdsServiceEndpoint.getTitle();
            this.description = cdsServiceEndpoint.getDescription();
            this.cdsHooks = cdsServiceEndpoint.getCdsHooks()
                                              .stream()
                                              .map(SandboxCdsHook::new)
                                              .collect(Collectors.toList());
        }
    }

    @Getter
    private static class SandboxCdsHook {
        private final String logoUri;
        private final String hook;
        private final String title;
        private final String description;
        private final String hookId;
        private final JsonNode prefetch;
        private final String hookUrl;
        private final String scope;
        private final JsonNode context;

        public SandboxCdsHook(CdsHook cdsHook) {
            this.logoUri = cdsHook.getLogoUri();
            this.hook = cdsHook.getHook();
            this.title = cdsHook.getTitle();
            this.description = cdsHook.getDescription();
            this.hookId = cdsHook.getHookId();
            this.prefetch = cdsHook.getPrefetch();
            this.hookUrl = cdsHook.getHookUrl();
            this.scope = cdsHook.getScope();
            this.context = cdsHook.getContext();
        }
    }

    private void addLaunchScenariosToZipFile(String sandboxId, String sbmUserId, ZipOutputStream zipOutputStream, List<AppManifestTemplate> appsManifests) {
        var launchScenarios = launchScenarioService.findBySandboxIdAndCreatedByOrVisibility(sandboxId, sbmUserId, Visibility.PUBLIC);
        var appIdToClientIdMapper = appsManifests.stream()
                                                 .collect(Collectors.toMap(AppManifestTemplate::getAppId, AppManifestTemplate::getClientId));
        var sandboxLaunchScenarios = launchScenarios.stream()
                                                    .map(launchScenario -> new SandboxLaunchScenario(launchScenario))
                                                    .collect(Collectors.toList());
        try (var inputStream = new ByteArrayInputStream(new GsonBuilder().setPrettyPrinting()
                                                                         .create()
                                                                         .toJson(sandboxLaunchScenarios)
                                                                         .getBytes())) {
            addZipFileEntry(inputStream, new ZipEntry("launch-scenarios.json"), zipOutputStream);
        } catch (IOException e) {
            LOGGER.error("Exception while adding launch scenarios for sandbox download", e);
        }
    }

    @Data
    public static class SandboxLaunchScenario {
        private final String description;
        private final String personaUserId;
        private String clientId;
        private final Map<String, String> contextParams;
        private final String patient;
        private final String encounter;
        private final String location;
        private final String resource;
        private final String intent;
        private final String smartStyleUrl;
        private final String title;
        private final boolean needPatientBanner;
        private final String cdsHookUrl;
        private final JsonNode context;

        public SandboxLaunchScenario(LaunchScenario launchScenario) {
            this.description = launchScenario.getDescription();
            this.personaUserId = launchScenario.getUserPersona() == null ? null : launchScenario.getUserPersona().getPersonaUserId();
            this.clientId = launchScenario.getApp() == null ? null : launchScenario.getApp().getClientId();
            this.contextParams = launchScenario.getContextParams()
                                               .stream()
                                               .collect(Collectors.toMap(ContextParams::getName, ContextParams::getValue));
            this.patient = launchScenario.getPatient();
            this.encounter = launchScenario.getEncounter();
            this.location = launchScenario.getLocation();
            this.resource = launchScenario.getResource();
            this.intent = launchScenario.getIntent();
            this.smartStyleUrl = launchScenario.getSmartStyleUrl();
            this.title = launchScenario.getTitle();
            this.needPatientBanner = "1".equals(launchScenario.getNeedPatientBanner());
            this.cdsHookUrl = launchScenario.getCdsHook() == null ? null : launchScenario.getCdsHook().getHookUrl();
            this.context = launchScenario.getContext();
        }

    }

    @Data
    @AllArgsConstructor
    private static class AppId {
        private String id;
    }

    public void addProfilesToZipFile(String sandboxId, ZipOutputStream zipOutputStream) {
        var profileDetails = fhirProfileDetailService.getAllProfilesForAGivenSandbox(sandboxId);
        var profiles = profileDetails.stream()
                                     .map(SandboxFhirProfileDetail::new)
                                     .peek(sandboxFhirProfileDetail -> {
                                         var sandboxFhirProfiles = fhirProfileService.getAllResourcesForGivenProfileId(sandboxFhirProfileDetail.getId());
                                         sandboxFhirProfileDetail.setSandboxFhirProfiles(sandboxFhirProfiles);
                                     })
                                     .collect(Collectors.toList());
        try (var inputStream = new ByteArrayInputStream(new GsonBuilder().setPrettyPrinting()
                                                                         .create()
                                                                         .toJson(profiles)
                                                                         .getBytes())) {
            addZipFileEntry(inputStream, new ZipEntry("profiles.json"), zipOutputStream);
        } catch (IOException e) {
            LOGGER.error("Exception while adding profiles for sandbox download", e);
        }
    }

    @Getter
    public static class SandboxFhirProfileDetail {
        private final transient Integer id;
        private final String profileName;
        private final String profileId;
        private List<SandboxFhirProfile> fhirProfiles;

        public SandboxFhirProfileDetail(FhirProfileDetail fhirProfileDetail) {
            this.id = fhirProfileDetail.getId();
            this.profileName = fhirProfileDetail.getProfileName();
            this.profileId = fhirProfileDetail.getProfileId();
        }

        public void setSandboxFhirProfiles(List<FhirProfile> fhirProfiles) {
            this.fhirProfiles = fhirProfiles.stream()
                                            .map(SandboxFhirProfile::new)
                                            .collect(Collectors.toList());
        }
    }

    @Getter
    public static class SandboxFhirProfile {
        private final String fullUrl;
        private final String relativeUrl;
        private final String profileType;

        public SandboxFhirProfile(FhirProfile fhirProfile) {
            this.fullUrl = fhirProfile.getFullUrl();
            this.relativeUrl = fhirProfile.getRelativeUrl();
            this.profileType = fhirProfile.getProfileType();
        }
    }

}
