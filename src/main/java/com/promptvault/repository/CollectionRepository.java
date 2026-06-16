package com.promptvault.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.promptvault.repository.entities.Collection;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CollectionRepository extends ReactiveCrudRepository<Collection, Long> {

    Flux<Collection> findAllByOwnerId(Long ownerId);

    Mono<Collection> findByIdAndOwnerId(Long id, Long ownerId);

    Mono<Long> deleteByIdAndOwnerId(Long id, Long ownerId);
}
