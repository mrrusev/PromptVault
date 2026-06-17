package com.promptvault.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.promptvault.auth.AuthenticatedUser;
import com.promptvault.models.PromptResponse;
import com.promptvault.models.PromptVersionResponse;
import com.promptvault.services.PromptVersionService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/prompts")
public class PromptVersionController {

    private final PromptVersionService promptVersionService;

    public PromptVersionController(PromptVersionService promptVersionService) {
        this.promptVersionService = promptVersionService;
    }

    /**
     * POST /prompts/{id}/versions — 201 Created with the new snapshot of the
     * prompt's current content. Returns 404 when the prompt does not exist or
     * belongs to another user. Requires a valid Bearer token; returns 401 when
     * absent or invalid.
     */
    @PostMapping("/{id}/versions")
    public Mono<ResponseEntity<PromptVersionResponse>> createVersion(@PathVariable Long id,
                                                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        return promptVersionService.save(id, principal.id())
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    /**
     * GET /prompts/{id}/versions — 200 OK with the prompt's versions ordered by
     * version number ascending. Returns 404 when the prompt does not exist or
     * belongs to another user. Requires a valid Bearer token; returns 401 when
     * absent or invalid.
     */
    @GetMapping("/{id}/versions")
    public Flux<PromptVersionResponse> listVersions(@PathVariable Long id,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return promptVersionService.listVersions(id, principal.id());
    }

    /**
     * POST /prompts/{id}/versions/{versionNumber}/restore — 200 OK with the updated
     * prompt after its content is restored from the given version. Returns 404 when
     * the prompt or the version does not exist, or when the prompt belongs to
     * another user. Requires a valid Bearer token; returns 401 when absent or invalid.
     */
    @PostMapping("/{id}/versions/{versionNumber}/restore")
    public Mono<PromptResponse> restore(@PathVariable Long id,
                                        @PathVariable Integer versionNumber,
                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        return promptVersionService.restore(id, versionNumber, principal.id());
    }
}
