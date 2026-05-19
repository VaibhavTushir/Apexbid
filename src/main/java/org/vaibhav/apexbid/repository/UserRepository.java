package org.vaibhav.apexbid.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.vaibhav.apexbid.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM administrators WHERE user_id = :userId)", nativeQuery = true)
    Boolean isAdmin(@Param("userId") Long userId);
}
