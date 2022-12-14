package org.logicahealth.sandboxmanagerapi.services;

import org.logicahealth.sandboxmanagerapi.model.FhirProfile;
import org.logicahealth.sandboxmanagerapi.model.FhirProfileDetail;
import org.logicahealth.sandboxmanagerapi.model.ProfileTask;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipFile;

public interface FhirProfileDetailService {

    String TARBALL_ARCHIVE = "tar";
    String TARBALL_GZIP_ARCHIVE = "tgz";
    String ZIP_ARCHIVE = "zip";

    FhirProfileDetail save(FhirProfileDetail fhirProfileDetail);

    FhirProfileDetail getFhirProfileDetail(Integer fhirProfileId);

    List<FhirProfileDetail> getAllProfilesForAGivenSandbox(String sandboxId);

    List<FhirProfile> getFhirProfileWithASpecificTypeForAGivenSandbox(Integer fhirProfileId, String type);

    void markAsDeleted(Integer fhirProfileId);

    void backgroundDelete(HttpServletRequest request, Integer fhirProfileId, String sandboxId);

    void delete(Integer fhirProfileId);

    void saveZipFile (FhirProfileDetail fhirProfileDetail, ZipFile zipFile, String authToken, String sandboxId, String id) throws IOException;

    ProfileTask getTaskRunning(String id);

    HashMap<String, ProfileTask> getIdProfileTask();

    void saveTGZfile (FhirProfileDetail fhirProfileDetail, InputStream fileInputStream, String authToken, String sandboxId, String id) throws IOException;

    FhirProfileDetail findByProfileIdAndSandboxId(String profileId, String sandboxId);

    List<Integer> getAllFhirProfileIdsAssociatedWithASandbox(String sandboxId);

    void saveTarballfile(FhirProfileDetail fhirProfileDetail, InputStream fileInputStream, String authToken, String sandboxId, String id) throws IOException;
}
