package com.promptvault.services;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.promptvault.models.DashboardResponse;
import com.promptvault.repository.CollectionRepository;
import com.promptvault.repository.PromptRepository;
import com.promptvault.repository.PromptVersionRepository;

import reactor.core.publisher.Mono;

@Service
public class DashboardService {

    private final CollectionRepository collectionRepository;
    private final PromptRepository promptRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final PromptService promptService;

    public DashboardService(CollectionRepository collectionRepository,
                            PromptRepository promptRepository,
                            PromptVersionRepository promptVersionRepository,
                            PromptService promptService) {
        this.collectionRepository = collectionRepository;
        this.promptRepository = promptRepository;
        this.promptVersionRepository = promptVersionRepository;
        this.promptService = promptService;
    }

    /**
     * Aggregates the owner's workspace statistics with the four queries running
     * concurrently. Every query is owner-scoped, so the result can never include
     * another user's data. When the user has no prompts, {@code latestPrompt} is
     * null and all counts come back as zero.
     */
    public Mono<DashboardResponse> getDashboard(Long ownerId) {
        return Mono.zip(
                        collectionRepository.countByOwnerId(ownerId),
                        promptRepository.countByOwnerId(ownerId),
                        promptVersionRepository.countByOwnerId(ownerId),
                        // why: Mono.zip discards the whole tuple if any source completes empty,
                        // so a user with no prompts would otherwise yield an empty Mono (200 with
                        // no body). Lifting the latest prompt into an Optional keeps this source
                        // emitting, so the no-prompts case still produces a populated response with
                        // a null latestPrompt.
                        promptRepository.findFirstByOwnerIdOrderByCreatedAtDesc(ownerId)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()))
                .map(tuple -> new DashboardResponse(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4().map(promptService::toResponse).orElse(null)));
    }
}
