package com.pidabrow.starter.common.security;

/**
 * Port interface for password encoding.
 * This is an outbound port following hexagonal architecture principles.
 * Domain and application layers must not depend on specific hashing implementations.
 */
public interface PasswordEncoder {
    
    /**
     * Encodes a raw password.
     * 
     * @param rawPassword the raw password to encode
     * @return the encoded password (hash)
     */
    String encode(CharSequence rawPassword);
    
    /**
     * Verifies a raw password against an encoded password.
     * 
     * @param rawPassword the raw password to verify
     * @param encodedPassword the encoded password to verify against
     * @return true if the raw password matches the encoded password, false otherwise
     */
    boolean matches(CharSequence rawPassword, String encodedPassword);
}

