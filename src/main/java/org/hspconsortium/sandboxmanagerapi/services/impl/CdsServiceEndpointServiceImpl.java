package org.hspconsortium.sandboxmanagerapi.services.impl;

import org.hspconsortium.sandboxmanagerapi.metrics.PublishAtomicMetric;
import org.hspconsortium.sandboxmanagerapi.model.*;
import org.hspconsortium.sandboxmanagerapi.repositories.CdsServiceEndpointRepository;
import org.hspconsortium.sandboxmanagerapi.services.*;
import org.springframework.stereotype.Service;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

@Service
public class CdsServiceEndpointServiceImpl implements CdsServiceEndpointService {

    private final CdsServiceEndpointRepository repository;
    private CdsHookService cdsHookService;
    private ImageService imageService;
    private LaunchScenarioService launchScenarioService;
    private UserLaunchService userLaunchService;

    @Inject
    public CdsServiceEndpointServiceImpl(final CdsServiceEndpointRepository cdsServiceEndpointRepository) { this.repository = cdsServiceEndpointRepository; }

    @Inject
    public void setCdsHookService(CdsHookService cdsHookService) {
        this.cdsHookService = cdsHookService;
    }

    @Inject
    public void setImageService(ImageService imageService) {
        this.imageService = imageService;
    }

    @Inject
    public void setLaunchScenarioService(LaunchScenarioService launchScenarioService) {
        this.launchScenarioService = launchScenarioService;
    }

    @Inject
    public void setUserLaunchService(UserLaunchService userLaunchService) {
        this.userLaunchService = userLaunchService;
    }

    @Inject
    public void setLaunchScenarioCdsServices(LaunchScenarioService launchScenarioService, UserLaunchService userLaunchService) {
        this.launchScenarioService = launchScenarioService;
        this.userLaunchService = userLaunchService;
    }

    @Override
    @Transactional
    public CdsServiceEndpoint save(final CdsServiceEndpoint cdsServiceEndpoint) {
        return repository.save(cdsServiceEndpoint);
    }

    @Override
    @Transactional
    public void delete(final int id) {
        repository.delete(id);
    }

    @Override
    @Transactional
    public void delete(final CdsServiceEndpoint cdsServiceEndpoint, CdsHook cdsHook) {
        // Delete all associated CDS-Service Launch Scenarios
        List<LaunchScenario> launchScenarioList = launchScenarioService.findByCdsServiceEndpointIdIdAndSandboxId(cdsServiceEndpoint.getId(), cdsServiceEndpoint.getSandbox().getId());
        for (LaunchScenario launchScenario: launchScenarioList) {
            for (UserLaunch userLaunchCdsServiceEndpoint: userLaunchService.findByLaunchScenarioCdsServiceEndpointId(launchScenario.getId())) {
                userLaunchService.delete(userLaunchCdsServiceEndpoint.getId());
            }
            launchScenarioService.delete(launchScenario.getId());
        }
        if (cdsHook.getLogo() != null) {
            int logoId = cdsHook.getLogo().getId();
            cdsHook.setLogo(null);
            imageService.delete(logoId);
        }
        delete(cdsServiceEndpoint.getId());
    }

    @Override
    @Transactional
    @PublishAtomicMetric
    public CdsServiceEndpoint create(final CdsServiceEndpoint cdsServiceEndpoint, final Sandbox sandbox) {
        cdsServiceEndpoint.setCreatedTimestamp(new Timestamp(new Date().getTime()));
        List<CdsHook> cdsHooks = cdsServiceEndpoint.getCdsHooks();
        for (CdsHook cdsHook: cdsHooks) {
            cdsHookService.save(cdsHook);
        }
        return save(cdsServiceEndpoint);
    }

    @Override
    @Transactional
    public  CdsServiceEndpoint update(final CdsServiceEndpoint cdsServiceEndpoint) {
        CdsServiceEndpoint existingCdsServiceEndpoint = getById(cdsServiceEndpoint.getId());
        existingCdsServiceEndpoint.setUrl(cdsServiceEndpoint.getUrl());
        existingCdsServiceEndpoint.setTitle(cdsServiceEndpoint.getTitle());
        existingCdsServiceEndpoint.setDescription(cdsServiceEndpoint.getDescription());
        existingCdsServiceEndpoint.setCdsHooks(cdsServiceEndpoint.getCdsHooks());
        return save(cdsServiceEndpoint);
    }

    @Override
    public CdsServiceEndpoint getById(final int id) {
        return repository.findOne(id);
    }

    public List<CdsServiceEndpoint> findBySandboxId(final String sandboxId) {
        return repository.findBySandboxId(sandboxId);
    }

    public List<CdsServiceEndpoint> findBySandboxIdAndCreatedByOrVisibility(final String sandboxId, final String createdBy, final Visibility visibility) {
        return repository.findBySandboxIdAndCreatedByOrVisibility(sandboxId, createdBy, visibility);
    }

    public List<CdsServiceEndpoint> findBySandboxIdAndCreatedBy(final String sandboxId, final String createdBy) {
        return repository.findBySandboxIdAndCreatedBy(sandboxId, createdBy);
    }

    public CdsServiceEndpoint findByCdsServiceEndpointUrlAndSandboxId (final String url, final String sandboxId) {
        return repository.findByCdsServiceEndpointUrlAndSandboxId(url, sandboxId);
    }

    public void addCdsServiceEndpointCdsHook(final CdsServiceEndpoint cdsServiceEndpoint, final CdsHook cdsHook) {
        List<CdsHook> cdsHooks = cdsServiceEndpoint.getCdsHooks();
        cdsHooks.add(cdsHook);
        cdsServiceEndpoint.setCdsHooks(cdsHooks);
        save(cdsServiceEndpoint);
    }
}