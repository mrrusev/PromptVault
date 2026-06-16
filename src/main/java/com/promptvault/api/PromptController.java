package com.promptvault.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.promptvault.auth.AuthenticatedUser;
import com.promptvault.models.PromptCreateRequest;
import com.promptvault.models.PromptResponse;
import com.promptvault.models.PromptUpdateRequest;
import com.promptvault.services.PromptService;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/prompts")
public class PromptController {

    private final PromptService promptService;

    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    /**
     * GET /prompts — 200 OK with the authenticated user's prompts, optionally
     * filtered by {@code collectionId}. Requires a valid Bearer token; returns
     * 401 when absent or invalid.
     */
    @GetMapping
    public Flux<PromptResponse> list(@RequestParam(required = false) Long collectionId,
                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        return promptService.findAll(principal.id(), collectionId);
    }

    /**
     * GET /prompts/{id} — 200 OK with a single prompt. Returns 404 when the prompt
     * does not exist or belongs to another user (existence must not be revealed
     * across boundaries). Requires a valid Bearer token; returns 401 when absent.
     */
    @GetMapping("/{id}")
    public Mono<PromptResponse> get(@PathVariable Long id,
                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return promptService.findById(id, principal.id());
    }

    /**
     * POST /prompts — 201 Created with the newly created prompt. Returns 404 when
     * the target collection does not exist or belongs to another user, 400 on
     * validation failure, 401 when the token is absent or invalid.
     */
    @PostMapping
    public Mono<ResponseEntity<PromptResponse>> create(@Valid @RequestBody PromptCreateRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return promptService.create(request, principal.id())
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    /**
     * PATCH /prompts/{id} — 200 OK with the updated prompt. Applies only the
     * non-null fields (title and/or content). A body with both fields null is
     * rejected as 400 ("nothing to update"). Returns 404 when the prompt does not
     * exist or belongs to another user. Requires a valid Bearer token.
     */
    @PatchMapping("/{id}")
    public Mono<PromptResponse> patch(@PathVariable Long id,
                                      @Valid @RequestBody PromptUpdateRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser principal) {
        if (request.title() == null && request.content() == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to update"));
        }
        return promptService.update(id, request, principal.id());
    }

    /**
     * DELETE /prompts/{id} — 204 No Content on success. Returns 404 when the prompt
     * does not exist or belongs to another user (ownership-isolation: existence
     * must not be revealed across boundaries). Requires a valid Bearer token.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Long id,
                             @AuthenticationPrincipal AuthenticatedUser principal) {
        return promptService.delete(id, principal.id());
    }
}
