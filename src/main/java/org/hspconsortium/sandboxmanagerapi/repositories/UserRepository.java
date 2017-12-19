package org.hspconsortium.sandboxmanagerapi.repositories;

import org.hspconsortium.sandboxmanagerapi.model.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;

public interface UserRepository extends CrudRepository<User, Integer> {
    User findBySbmUserId(@Param("sbmUserId") String sbmUserId);
    User findByUserEmail(@Param("email") String email);
    String fullCount();
    String intervalCount(@Param("intervalTime") Timestamp intervalTime);
}
