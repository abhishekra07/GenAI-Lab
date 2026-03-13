package com.genailab.security.service;

import com.genailab.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges Spring Security's authentication mechanism with our UserRepository.
 *
 * <p>Spring Security calls {@link #loadUserByUsername} during the
 * authentication process to fetch the user to authenticate against.
 *
 * <p>Our {@link com.genailab.security.domain.User} entity implements
 * {@link UserDetails} directly, so we return it as-is. Spring Security
 * then calls {@code getPassword()} on it and compares it against the
 * submitted password using the configured {@link org.springframework.security.crypto.password.PasswordEncoder}.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }
}