package com.altstay.api.knowledgebase;

import com.altstay.api.knowledgebase.dto.KnowledgeBaseVersionResponse;
import com.altstay.api.knowledgebase.dto.SaveKnowledgeBaseRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped knowledge base controller.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/v1/properties/{propertyId}/knowledge-base — returns current version (or 404)</li>
 *   <li>POST /api/v1/properties/{propertyId}/knowledge-base — saves new version or returns existing on unchanged content</li>
 *   <li>GET /api/v1/properties/{propertyId}/knowledge-base/history — lists version history ordered newest first</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/knowledge-base")
@ConditionalOnProperty(name = "spring.datasource.url")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping
    public ResponseEntity<KnowledgeBaseVersionResponse> getCurrent(@PathVariable UUID propertyId) {
        return knowledgeBaseService.getCurrent(propertyId)
                .map(KnowledgeBaseVersionResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<KnowledgeBaseVersionResponse> save(
            @PathVariable UUID propertyId,
            @Valid @RequestBody SaveKnowledgeBaseRequest request) {
        KnowledgeBaseVersion version = knowledgeBaseService.save(propertyId, request.content());
        return ResponseEntity.ok(KnowledgeBaseVersionResponse.from(version));
    }

    @GetMapping("/history")
    public ResponseEntity<List<KnowledgeBaseVersionResponse>> history(
            @PathVariable UUID propertyId,
            @RequestParam(defaultValue = "50") int limit) {
        List<KnowledgeBaseVersionResponse> history = knowledgeBaseService.history(propertyId, limit)
                .stream()
                .map(KnowledgeBaseVersionResponse::from)
                .toList();
        return ResponseEntity.ok(history);
    }
}
