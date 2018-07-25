package org.hspconsortium.sandboxmanagerapi.services.impl;

import org.hspconsortium.sandboxmanagerapi.model.*;
import org.hspconsortium.sandboxmanagerapi.services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.*;

@Service
public class RuleServiceImpl implements RuleService {

    @Autowired
    private RulesList rulesList;

    private SandboxService sandboxService;
    private UserService userService;
    private AnalyticsService analyticsService;

    public RuleServiceImpl() { }

    @Inject
    public void setSandboxService(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    @Inject
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @Inject
    public void setAnalyticsService(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    public Boolean checkIfUserCanCreateSandbox(User user) {
        if (rulesList.getTierRuleList() == null) {
            return true;
        }
        Rule rules = findRulesByUser(user);
        if (rules == null) {
            return true;
        }
        List<Sandbox> sandboxes = sandboxService.findByPayerId(user.getId());
        if (rules.getSandboxes() > sandboxes.size()) {
            if(rules.getStorage() > analyticsService.retrieveTotalMemoryByUser(user)) {
                return true;
            }
        }
        return false;
    }

    public Boolean checkIfUserCanCreateApp(Integer payerId, Integer appsInSandbox) {
        if (rulesList.getTierRuleList() == null) {
            return true;
        }
        if (payerId == null) {
            return true;
        }
        User user = userService.findById(payerId);
        Rule rules = findRulesByUser(user);
        if (rules == null) {
            return true;
        }
        return rules.getApps() > appsInSandbox;
    }

    public Boolean checkIfUserCanBeAdded(String sandBoxId) {
        if (rulesList.getTierRuleList() == null) {
            return true;
        }
        Integer payerId = sandboxService.findBySandboxId(sandBoxId).getPayerUserId();
        if (payerId == null) {
            return true;
        }
        User user = userService.findById(payerId);
        Rule rules = findRulesByUser(user);
        if (rules == null) {
            return true;
        }
        List<UserRole> usersRoles = sandboxService.findBySandboxId(sandBoxId).getUserRoles();
        Set<String> uniqueUsers = new HashSet<>();
        for (UserRole userRole : usersRoles) {
            // Creates unique list
            uniqueUsers.add(userRole.getUser().getEmail());
        }
        return rules.getUsers() > uniqueUsers.size();
    }

    public Boolean checkIfUserCanPerformTransaction(Sandbox sandbox, String operation) {
        if (rulesList.getTierRuleList() == null) {
            return true;
        }
        Integer payerId = sandbox.getPayerUserId();
        if (payerId == null) {
            return true;
        }
        User user = userService.findById(payerId);
        Rule rules = findRulesByUser(user);
        if (rules == null) {
            return true;
        }
        if (rules.getTransactions() > analyticsService.countTransactionsByPayer(user)) {
            if (!operation.equals("GET") && !operation.equals("DELETE")) {
                // Don't need to check memory for these operations
                if (rules.getStorage() > analyticsService.retrieveTotalMemoryByUser(user)) {
                    return true;
                }
            } else {
                return true;
            }
        }
        return false;
    }

    private Rule findRulesByUser(User user) {
        if (user.getTierLevel() == null) {
            return null;
        }
        return rulesList.getTierRuleList().get(user.getTierLevel().name());
    }

}