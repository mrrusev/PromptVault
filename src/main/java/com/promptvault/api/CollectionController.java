package com.promptvault.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.promptvault.auth.AuthenticatedUser;
import com.promptvault.models.CollectionRequest;
import com.promptvault.models.CollectionResponse;
import com.promptvault.services.CollectionService;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/collections")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    /**
     * GET /collections — 200 OK with the authenticated user's collections.
     * Requires a valid Bearer token; returns 401 when absent or invalid.
     */
    @GetMapping
    public Flux<CollectionResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return collectionService.findAllByOwner(principal.id());
    }

    /**
     * POST /collections — 201 Created with the newly created collection.
     * Requires a valid Bearer token; returns 400 on validation failure,
     * 401 when the token is absent or invalid.
     */
    @PostMapping
    public Mono<ResponseEntity<CollectionResponse>> create(@Valid @RequestBody CollectionRequest request,
                                                           @AuthenticationPrincipal AuthenticatedUser principal) {
        return collectionService.create(request, principal.id())
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    /**
     * DELETE /collections/{id} — 204 No Content on success.
     * Returns 404 when the collection does not exist or belongs to another user
     * (ownership-isolation: existence must not be revealed across boundaries).
     * Requires a valid Bearer token; returns 401 when absent or invalid.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Long id,
                             @AuthenticationPrincipal AuthenticatedUser principal) {
        return collectionService.delete(id, principal.id());
    }
}
