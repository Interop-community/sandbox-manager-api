package org.hspconsortium.sandboxmanager.repositories;

import org.hspconsortium.sandboxmanager.model.SandboxActivity;
import org.hspconsortium.sandboxmanager.model.SandboxActivityLog;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SandboxActivityLogRepository extends CrudRepository<SandboxActivityLog, Integer> {
    public List<SandboxActivityLog> findByUserLdapId(@Param("ldapId") String ldapId);
    public List<SandboxActivityLog> findBySandboxId(@Param("sandboxId") String sandboxId);
    public List<SandboxActivityLog> findBySandboxActivity(@Param("sandboxActivity") SandboxActivity sandboxActivity);
}