package org.logicahealth.sandboxmanagerapi.services.impl;

import org.logicahealth.sandboxmanagerapi.model.LaunchScenario;
import org.logicahealth.sandboxmanagerapi.model.UserPersona;
import org.logicahealth.sandboxmanagerapi.model.Visibility;
import org.logicahealth.sandboxmanagerapi.repositories.UserPersonaRepository;
import org.logicahealth.sandboxmanagerapi.services.LaunchScenarioService;
import org.logicahealth.sandboxmanagerapi.services.UserPersonaService;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

@Service
public class UserPersonaServiceImpl implements UserPersonaService {
    private final UserPersonaRepository repository;

    private LaunchScenarioService launchScenarioService;

    @Inject
    public UserPersonaServiceImpl(final UserPersonaRepository repository) {
        this.repository = repository;
    }

    @Inject
    public void setLaunchScenarioService(LaunchScenarioService launchScenarioService) {
        this.launchScenarioService = launchScenarioService;
    }

    @Override
    @Transactional
    public UserPersona save(UserPersona userPersona) {
        return repository.save(userPersona);
    }

    @Override
    public UserPersona getById(final int id) {
        return  repository.findById(id).orElse(null);
    }

    @Override
    public UserPersona findByPersonaUserId(String personaUserId) {
        return repository.findByPersonaUserId(personaUserId);
    }

    @Override
    public UserPersona findByPersonaUserIdAndSandboxId(final String personaUserId, final String sandboxId) {
        return repository.findByPersonaUserIdAndSandboxId(personaUserId, sandboxId);
    }

    @Override
    public List<UserPersona> findBySandboxIdAndCreatedByOrVisibility(String sandboxId, String createdBy, Visibility visibility) {
        return  repository.findBySandboxIdAndCreatedByOrVisibility(sandboxId, createdBy, visibility);
    }

    @Override
    public UserPersona findDefaultBySandboxId(String sandboxId, String createdBy, Visibility visibility) {
        List<UserPersona> personas = repository.findDefaultBySandboxId(sandboxId, createdBy, visibility);
        return !personas.isEmpty() ? personas.get(0) : null;
    }

    @Override
    public List<UserPersona> findBySandboxIdAndCreatedBy(String sandboxId, String createdBy) {
        return  repository.findBySandboxIdAndCreatedBy(sandboxId, createdBy);
    }

    @Override
    public List<UserPersona> findBySandboxId(final String sandboxId) {
        return  repository.findBySandboxId(sandboxId);
    }

    @Override
    @Transactional
    public void delete(final int id) {
        repository.deleteById(id);
    }

    @Override
    public void delete(UserPersona userPersona) {
        List<LaunchScenario> launchScenarios = launchScenarioService.findByUserPersonaIdAndSandboxId(userPersona.getId(), userPersona.getSandbox().getSandboxId());
        if (launchScenarios.size() > 0) {
            throw new RuntimeException("Can't delete persona. It's tied to one or more launch scenarios.");
        }
        delete(userPersona.getId());
    }

    @Override
    @Transactional
    public UserPersona create(UserPersona userPersona) {
        userPersona.setCreatedTimestamp(new Timestamp(new Date().getTime()));
        checkpersonaUserId(userPersona);
        return createOrUpdate(userPersona);
    }

    @Override
    @Transactional
    public UserPersona update(UserPersona userPersona) {
        return createOrUpdate(userPersona);
    }

    private UserPersona createOrUpdate(final UserPersona userPersona) {
        return save(userPersona);
    }

    private void checkpersonaUserId(UserPersona userPersona) {
        UserPersona userPersonaExists = findByPersonaUserIdAndSandboxId(userPersona.getPersonaUserId(), userPersona.getSandbox().getSandboxId());
        if (userPersonaExists != null) {
            throw new IllegalArgumentException("Persona user " + userPersona.getPersonaUserId() + " already in use in sandbox " + userPersona.getSandbox().getSandboxId());
        }
    }
}