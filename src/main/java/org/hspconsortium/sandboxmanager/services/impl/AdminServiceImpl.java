package org.hspconsortium.sandboxmanager.services.impl;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.hspconsortium.sandboxmanager.services.AdminService;
import org.hspconsortium.sandboxmanager.services.SandboxActivityLogService;
import org.hspconsortium.sandboxmanager.services.SandboxService;
import org.hspconsortium.sandboxmanager.services.UserService;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.util.Date;

@Service
public class AdminServiceImpl implements AdminService {

    private final UserService userService;
    private final SandboxService sandboxService;
    private final SandboxActivityLogService sandboxActivityLogService;

    @Inject
    public AdminServiceImpl(final UserService userService, final SandboxService sandboxService,
                                    final SandboxActivityLogService sandboxActivityLogService) {
        this.userService = userService;
        this.sandboxService = sandboxService;
        this.sandboxActivityLogService = sandboxActivityLogService;
    }

    @Override
    public String getSandboxStatistics(final String intervalDays) {

        int intDays = Integer.parseInt(intervalDays);
        Date d = new Date();
        Date dateBefore = new Date(d.getTime() - intDays * 24 * 3600 * 1000L );
        Timestamp timestamp = new Timestamp(dateBefore.getTime());

        Statistics statistics = new Statistics();
        statistics.setFullSandboxCount(sandboxService.fullCount());
        statistics.setSchema1Sandboxes(sandboxService.schemaCount("1"));
        statistics.setSchema2Sandboxes(sandboxService.schemaCount("2"));
        statistics.setSchema3Sandboxes(sandboxService.schemaCount("3"));
        statistics.setSandboxesInInterval(sandboxService.intervalCount(timestamp));

        statistics.setFullUserCount(userService.fullCount());
        statistics.setNewUsersInInterval(userService.intervalCount(timestamp));
        statistics.setActiveUserInInterval(sandboxActivityLogService.intervalActive(timestamp));

        return toJson(statistics);
    }

    private static String toJson(Statistics statistics) {
        Gson gson = new Gson();
        Type type = new TypeToken<Statistics>() {
        }.getType();
        return gson.toJson(statistics, type);
    }

    private class Statistics {
        private String fullSandboxCount;
        private String schema1Sandboxes;
        private String schema2Sandboxes;
        private String schema3Sandboxes;
        private String sandboxesInInterval;

        private String fullUserCount;
        private String newUsersInInterval;
        private String activeUserInInterval;


        public String getFullSandboxCount() {
            return fullSandboxCount;
        }

        void setFullSandboxCount(String fullSandboxCount) {
            this.fullSandboxCount = fullSandboxCount;
        }

        public String getSchema1Sandboxes() {
            return schema1Sandboxes;
        }

        void setSchema1Sandboxes(String schema1Sandboxes) {
            this.schema1Sandboxes = schema1Sandboxes;
        }

        public String getSchema2Sandboxes() {
            return schema2Sandboxes;
        }

        void setSchema2Sandboxes(String schema2Sandboxes) {
            this.schema2Sandboxes = schema2Sandboxes;
        }

        public String getSchema3Sandboxes() {
            return schema3Sandboxes;
        }

        void setSchema3Sandboxes(String schema3Sandboxes) {
            this.schema3Sandboxes = schema3Sandboxes;
        }

        public String getSandboxesInInterval() {
            return sandboxesInInterval;
        }

        void setSandboxesInInterval(String sandboxesInInterval) {
            this.sandboxesInInterval = sandboxesInInterval;
        }

        public String getFullUserCount() {
            return fullUserCount;
        }

        void setFullUserCount(String fullUserCount) {
            this.fullUserCount = fullUserCount;
        }

        public String getNewUsersInInterval() {
            return newUsersInInterval;
        }

        void setNewUsersInInterval(String newUsersInInterval) {
            this.newUsersInInterval = newUsersInInterval;
        }

        public String getActiveUserInInterval() {
            return activeUserInInterval;
        }

        void setActiveUserInInterval(String activeUserInInterval) {
            this.activeUserInInterval = activeUserInInterval;
        }
    }
}
