package org.hspconsortium.sandboxmanagerapi.services.impl;

import org.hspconsortium.sandboxmanagerapi.model.Sandbox;
import org.hspconsortium.sandboxmanagerapi.model.TermsOfUse;
import org.hspconsortium.sandboxmanagerapi.model.TermsOfUseAcceptance;
import org.hspconsortium.sandboxmanagerapi.model.User;
import org.hspconsortium.sandboxmanagerapi.repositories.UserRepository;
import org.hspconsortium.sandboxmanagerapi.services.TermsOfUseAcceptanceService;
import org.hspconsortium.sandboxmanagerapi.services.TermsOfUseService;
import org.hspconsortium.sandboxmanagerapi.services.UserService;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository repository;
    private final TermsOfUseService termsOfUseService;
    private final TermsOfUseAcceptanceService termsOfUseAcceptanceService;

    @Inject
    public UserServiceImpl(final UserRepository repository,
                           final TermsOfUseService termsOfUseService,
                           final TermsOfUseAcceptanceService termsOfUseAcceptanceService) {
        this.repository = repository;
        this.termsOfUseService = termsOfUseService;
        this.termsOfUseAcceptanceService = termsOfUseAcceptanceService;
    }

    @Override
    @Transactional
    public User save(final User user) {
        return repository.save(user);
    }

    @Override
    @Transactional
    public void delete(final User user) {
        repository.delete(user);
    }

    public User findBySbmUserId(final String sbmUserId) {
        User user = repository.findBySbmUserId(sbmUserId);

        if(user == null)
            return null;

        userHasAcceptedTermsOfUse(user);
        return user;
    }

    public User findByUserEmail(final String email) {
        User user = repository.findByUserEmail(email);

        if(user == null)
            return null;

        userHasAcceptedTermsOfUse(user);
        return user;
    }

    public String fullCount() {
        return repository.fullCount();
    }

    public String intervalCount(final Timestamp intervalTime) {
        return repository.intervalCount(intervalTime);
    }

    @Override
    @Transactional
    public void removeSandbox(Sandbox sandbox, User user) {
        List<Sandbox> sandboxes = user.getSandboxes();
        sandboxes.remove(sandbox);
        user.setSandboxes(sandboxes);
        save(user);
    }

    @Override
    @Transactional
    public void addSandbox(Sandbox sandbox, User user) {
        List<Sandbox> sandboxes = user.getSandboxes();
        if (!sandboxes.contains(sandbox)) {
            sandboxes.add(sandbox);
            user.setSandboxes(sandboxes);
            save(user);
        }
    }

    @Override
    public boolean hasSandbox(Sandbox sandbox, User user) {
        return user.getSandboxes().contains(sandbox);
    }

    @Override
    public void acceptTermsOfUse(final User user, final String termsOfUseId){
        TermsOfUse termsOfUse = termsOfUseService.getById(Integer.parseInt(termsOfUseId));
        TermsOfUseAcceptance termsOfUseAcceptance = new TermsOfUseAcceptance();
        termsOfUseAcceptance.setTermsOfUse(termsOfUse);
        termsOfUseAcceptance.setAcceptedTimestamp(new Timestamp(new Date().getTime()));
        termsOfUseAcceptance = termsOfUseAcceptanceService.save(termsOfUseAcceptance);
        List<TermsOfUseAcceptance> acceptances = user.getTermsOfUseAcceptances();
        acceptances.add(termsOfUseAcceptance);
        user.setTermsOfUseAcceptances(acceptances);
        save(user);
    }

    private void userHasAcceptedTermsOfUse(User user) {
        TermsOfUse latestTermsOfUse = termsOfUseService.mostRecent();
        if (latestTermsOfUse != null) {
            user.setHasAcceptedLatestTermsOfUse(false);
            for (TermsOfUseAcceptance termsOfUseAcceptance : user.getTermsOfUseAcceptances()) {
                if (termsOfUseAcceptance.getTermsOfUse().getId().equals(latestTermsOfUse.getId())) {
                    user.setHasAcceptedLatestTermsOfUse(true);
                    return;
                }
            }
        } else {
            // there are no terms so by default the user has accepted the latest
            user.setHasAcceptedLatestTermsOfUse(true);
        }
    }
}

