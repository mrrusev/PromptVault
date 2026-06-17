package com.promptvault.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.promptvault.repository.entities.PromptVersion;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PromptVersionRepository extends ReactiveCrudRepository<PromptVersion, Long> {

    Flux<PromptVersion> findAllByPromptIdOrderByVersionNumberAsc(Long promptId);

    Mono<PromptVersion> findTopByPromptIdOrderByVersionNumberDesc(Long promptId);

    Mono<PromptVersion> findByPromptIdAndVersionNumber(Long promptId, Integer versionNumber);
}
