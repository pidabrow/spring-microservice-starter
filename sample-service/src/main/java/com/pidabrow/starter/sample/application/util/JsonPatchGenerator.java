package com.pidabrow.starter.sample.application.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.diff.JsonDiff;

/**
 * Utility class for generating JSON Patch (RFC 6902) deltas.
 * Used for creating UserUpdatedEvent deltas.
 */
public class JsonPatchGenerator {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generates a JSON Patch delta between two objects.
     * 
     * @param source the source object (before changes)
     * @param target the target object (after changes)
     * @return JSON Patch string (RFC 6902 format)
     */
    public static String generatePatch(Object source, Object target) {
        try {
            JsonNode sourceNode = objectMapper.valueToTree(source);
            JsonNode targetNode = objectMapper.valueToTree(target);
            JsonPatch patch = JsonDiff.asJsonPatch(sourceNode, targetNode);
            return objectMapper.writeValueAsString(patch);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JSON Patch", e);
        }
    }
}

