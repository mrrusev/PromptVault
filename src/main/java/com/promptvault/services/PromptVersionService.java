package com.promptvault.services;

import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.promptvault.exceptions.PromptNotFoundException;
import com.promptvault.exceptions.PromptVersionNotFoundException;
import com.promptvault.models.PromptResponse;
import com.promptvault.models.PromptVersionResponse;
import com.promptvault.repository.PromptRepository;
import com.promptvault.repository.PromptVersionRepository;
import com.promptvault.repository.entities.Prompt;
import com.promptvault.repository.entities.PromptVersion;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
public class PromptVersionService {

    private static final int FIRST_VERSION_NUMBER = 1;
    private static final int MAX_VERSION_WRITE_RETRIES = 3;

    private final PromptRepository promptRepository;
    private final PromptVersionRepository promptVersionRepository;

    public PromptVersionService(PromptRepository promptRepository,
                                PromptVersionRepository promptVersionRepository) {
        this.promptRepository = promptRepository;
        this.promptVersionRepository = promptVersionRepository;
    }

    /**
     * Snapshots the current live content of an owned prompt as a new immutable
     * version. Owner-scoping is enforced via {@code findByIdAndOwnerId}; a missing
     * or unowned prompt yields {@link PromptNotFoundException} (404).
     *
     * <p>Version numbering reads the current max and increments it. This is an
     * inherent race: two concurrent saves can read the same max and attempt the
     * same number. A {@code UNIQUE (prompt_id, version_number)} constraint rejects
     * the loser with {@link DataIntegrityViolationException}, and the inner pipeline
     * is retried (bounded), re-reading the max each time so the next free number is
     * assigned — all without blocking.
     */
    public Mono<PromptVersionResponse> save(Long promptId, Long ownerId) {
        return promptRepository.findByIdAndOwnerId(promptId, ownerId)
                .switchIfEmpty(Mono.error(new PromptNotFoundException(promptId)))
                .flatMap(prompt -> assignNextVersionAndSave(prompt, ownerId))
                .map(PromptVersionResponse::from);
    }

    /**
     * Lists all versions of an owned prompt ordered by version number ascending.
     * The ownership gate runs first; a missing or unowned prompt yields
     * {@link PromptNotFoundException} (404) before any version is read.
     */
    public Flux<PromptVersionResponse> listVersions(Long promptId, Long ownerId) {
        return promptRepository.findByIdAndOwnerId(promptId, ownerId)
                .switchIfEmpty(Mono.error(new PromptNotFoundException(promptId)))
                .flatMapMany(prompt -> promptVersionRepository.findAllByPromptIdOrderByVersionNumberAsc(promptId))
                .map(PromptVersionResponse::from);
    }

    /**
     * Restores an old snapshot by copying its content back onto the live prompt.
     * This overwrites the prompt's current content only — it does NOT create a new
     * version. Returns the updated prompt. A missing or unowned prompt yields
     * {@link PromptNotFoundException} (404); an unknown version number yields
     * {@link PromptVersionNotFoundException} (404).
     */
    public Mono<PromptResponse> restore(Long promptId, Integer versionNumber, Long ownerId) {
        return promptRepository.findByIdAndOwnerId(promptId, ownerId)
                .switchIfEmpty(Mono.error(new PromptNotFoundException(promptId)))
                .flatMap(prompt -> promptVersionRepository.findByPromptIdAndVersionNumber(promptId, versionNumber)
                        .switchIfEmpty(Mono.error(new PromptVersionNotFoundException(versionNumber)))
                        .flatMap(version -> {
                            prompt.setContent(version.getContent());
                            return promptRepository.save(prompt);
                        }))
                .map(this::toResponse);
    }

    /**
     * Reads the current max version, increments it, and writes the snapshot. The
     * whole read-then-write is retried on a unique-constraint collision so each
     * attempt re-reads the latest max and claims the next free number.
     */
    private Mono<PromptVersion> assignNextVersionAndSave(Prompt prompt, Long ownerId) {
        return promptVersionRepository.findTopByPromptIdOrderByVersionNumberDesc(prompt.getId())
                .map(latest -> latest.getVersionNumber() + 1)
                .defaultIfEmpty(FIRST_VERSION_NUMBER)
                .flatMap(versionNumber -> {
                    PromptVersion version = new PromptVersion();
                    version.setPromptId(prompt.getId());
                    version.setOwnerId(ownerId);
                    version.setContent(prompt.getContent());
                    version.setVersionNumber(versionNumber);
                    version.setCreatedAt(Instant.now());
                    return promptVersionRepository.save(version);
                })
                .retryWhen(Retry.max(MAX_VERSION_WRITE_RETRIES)
                        .filter(DataIntegrityViolationException.class::isInstance)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    private PromptResponse toResponse(Prompt prompt) {
        return new PromptResponse(prompt.getId(), prompt.getTitle(), prompt.getContent(),
                prompt.getCollectionId(), prompt.getOwnerId(), prompt.getCreatedAt());
    }
}
