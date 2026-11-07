package com.iitsm.repository;

import com.iitsm.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Users of exactly this class, not its subclasses. For example {@code Administrator.class}
     * returns administrators but not IT managers, and {@code Technician.class} does not return team leads.
     */
    @Query("select u from User u where type(u) = :type")
    List<User> findAllByExactType(@Param("type") Class<? extends User> type);
}
