package com.genailab.common.exception;

/**
 * Thrown when a requested resource does not exist or does not belong
 * to the authenticated user. Maps to HTTP 404 Not Found.
 *
 * Used by: ConversationService, DocumentService, ChatService
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(resourceType + " not found: " + resourceId);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
}