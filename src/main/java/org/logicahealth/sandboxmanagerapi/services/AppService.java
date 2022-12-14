package org.logicahealth.sandboxmanagerapi.services;

import org.logicahealth.sandboxmanagerapi.model.App;
import org.logicahealth.sandboxmanagerapi.model.Image;
import org.logicahealth.sandboxmanagerapi.model.Sandbox;
import org.logicahealth.sandboxmanagerapi.model.Visibility;

import java.util.List;

public interface AppService {

    App save(final App app);

    void delete(final int id);

    void delete(final App app);

    App create(final App app, final Sandbox sandbox);

    App update(final App app);

    App getClientJSON(final App app);

    App updateAppImage(final App app, final Image image);

    App deleteAppImage(final App app);

    App getById(final int id);

    App findByLaunchUriAndClientIdAndSandboxId(final String launchUri, final String clientId, final String sandboxId);

    List<App> findBySandboxId(final String sandboxId);

    //TODO: remove after release of new sandbox manager and custom apps are dead
    List<App> findBySandboxIdIncludingCustomApps(final String sandboxId);

    List<App> findBySandboxIdAndCreatedByOrVisibility(final String sandboxId, final String createdBy, final Visibility visibility);

    List<App> findBySandboxIdAndCreatedBy(final String sandboxId, final String createdBy);

}
