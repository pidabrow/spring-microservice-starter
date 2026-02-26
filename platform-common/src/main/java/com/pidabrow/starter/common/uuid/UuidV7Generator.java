package com.pidabrow.starter.common.uuid;

import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Generator for UUID v7 (time-ordered UUIDs).
 * 
 * UUID v7 format (128 bits):
 * - 48 bits: Unix timestamp in milliseconds
 * - 12 bits: version (0x7) and variant bits
 * - 62 bits: random data
 * 
 * This ensures time-ordered UUIDs that prevent B-Tree fragmentation
 * while maintaining global uniqueness.
 */
public final class UuidV7Generator {
    
    private static final RandomGenerator RANDOM = RandomGenerator.getDefault();
    
    private UuidV7Generator() {
        // Utility class
    }
    
    /**
     * Generates a UUID v7 (time-ordered UUID).
     * 
     * @return a new UUID v7
     */
    public static UUID generate() {
        long timestamp = System.currentTimeMillis();
        return generate(timestamp);
    }
    
    /**
     * Generates a UUID v7 with a specific timestamp.
     * Used primarily for testing.
     * 
     * @param timestampMillis Unix timestamp in milliseconds
     * @return a new UUID v7
     */
    static UUID generate(long timestampMillis) {
        // UUID v7 layout (128 bits, big-endian):
        // - Bits 0-47: Unix timestamp in milliseconds (48 bits)
        // - Bits 48-51: Version (0x7) (4 bits)
        // - Bits 52-63: rand_a (12 bits random)
        // - Bits 64-65: Variant (10) (2 bits)
        // - Bits 66-127: rand_b (62 bits random)
        
        // High 64 bits: timestamp (48 bits) + version (4 bits) + rand_a (12 bits)
        long high = (timestampMillis << 16) | 0x7000L | (nextRandomBits(12) & 0x0FFFL);
        
        // Low 64 bits: variant (2 bits) + rand_b (62 bits)
        long low = 0x8000000000000000L | (nextRandomBits(62));
        
        return new UUID(high, low);
    }
    
    private static long nextRandomBits(int bits) {
        if (bits <= 32) {
            return RANDOM.nextLong() >>> (64 - bits);
        } else {
            // For more than 32 bits, combine two random values
            long high = (RANDOM.nextLong() >>> (64 - (bits - 32))) << 32;
            long low = RANDOM.nextLong() >>> (64 - 32);
            return high | low;
        }
    }
}

