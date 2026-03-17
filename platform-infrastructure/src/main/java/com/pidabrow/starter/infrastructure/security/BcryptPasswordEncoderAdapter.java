package com.pidabrow.starter.infrastructure.security;

import com.pidabrow.starter.common.security.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing PasswordEncoder port using BCrypt.
 * This is a package-private infrastructure adapter.
 */
@Component
class BcryptPasswordEncoderAdapter implements PasswordEncoder {
    
    private final BCryptPasswordEncoder delegate;
    
    BcryptPasswordEncoderAdapter(BCryptPasswordEncoder bcryptPasswordEncoder) {
        this.delegate = bcryptPasswordEncoder;
    }
    
    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }
    
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return delegate.matches(rawPassword, encodedPassword);
    }
}

