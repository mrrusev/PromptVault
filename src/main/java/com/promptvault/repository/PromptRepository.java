package com.promptvault.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.promptvault.repository.entities.Prompt;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PromptRepository extends ReactiveCrudRepository<Prompt, Long> {

    Flux<Prompt> findAllByOwnerId(Long ownerId);

    Flux<Prompt> findAllByOwnerIdAndCollectionId(Long ownerId, Long collectionId);

    Mono<Prompt> findByIdAndOwnerId(Long id, Long ownerId);

    Mono<Long> deleteByIdAndOwnerId(Long id, Long ownerId);
}
