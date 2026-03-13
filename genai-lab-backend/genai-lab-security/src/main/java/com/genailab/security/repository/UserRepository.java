package com.genailab.security.repository;

import com.genailab.security.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * User data access layer.
 *
 * <p>findByEmail is used by:
 * <ul>
 *   <li>{@code UserDetailsServiceImpl} — Spring Security loads user during auth</li>
 *   <li>{@code AuthService} — checks if email already registered</li>
 * </ul>
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find a user by their email address.
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if an email is already registered — used during registration
     * to return a clear error before attempting an insert that would
     * violate the unique constraint.
     */
    boolean existsByEmail(String email);
}