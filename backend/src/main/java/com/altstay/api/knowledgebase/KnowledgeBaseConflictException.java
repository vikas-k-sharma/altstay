package com.altstay.api.knowledgebase;

/**
 * Thrown when concurrent saves on a knowledge base conflict on version numbers,
 * and the retry attempt also encounters a conflict.
 */
public class KnowledgeBaseConflictException extends RuntimeException {

    public KnowledgeBaseConflictException(String message) {
        super(message);
    }

    public KnowledgeBaseConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
