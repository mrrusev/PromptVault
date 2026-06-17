package com.promptvault.services;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.promptvault.exceptions.CollectionNotFoundException;
import com.promptvault.exceptions.PromptNotFoundException;
import com.promptvault.models.PromptCreateRequest;
import com.promptvault.models.PromptResponse;
import com.promptvault.models.PromptUpdateRequest;
import com.promptvault.repository.CollectionRepository;
import com.promptvault.repository.PromptRepository;
import com.promptvault.repository.entities.Prompt;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PromptService {

    private final PromptRepository promptRepository;
    private final CollectionRepository collectionRepository;

    public PromptService(PromptRepository promptRepository, CollectionRepository collectionRepository) {
        this.promptRepository = promptRepository;
        this.collectionRepository = collectionRepository;
    }

    /**
     * Returns the owner's prompts, optionally narrowed to a single collection.
     * Owner-scoping is enforced at the query level, so no cross-user data can be
     * leaked regardless of calling code.
     */
    public Flux<PromptResponse> findAll(Long ownerId, Long collectionId) {
        Flux<Prompt> prompts = collectionId == null
                ? promptRepository.findAllByOwnerId(ownerId)
                : promptRepository.findAllByOwnerIdAndCollectionId(ownerId, collectionId);
        return prompts.map(this::toResponse);
    }

    /**
     * Returns a single prompt by id, scoped to the owner. A missing row or one
     * owned by another user both yield {@link PromptNotFoundException} (404) —
     * existence is never revealed across ownership boundaries.
     */
    public Mono<PromptResponse> findById(Long id, Long ownerId) {
        return promptRepository.findByIdAndOwnerId(id, ownerId)
                .switchIfEmpty(Mono.error(new PromptNotFoundException(id)))
                .map(this::toResponse);
    }

    /**
     * Creates a prompt inside a collection the caller owns. The target collection
     * is verified via an owner-scoped lookup first; if it is missing or owned by
     * someone else, a {@link CollectionNotFoundException} (404) is emitted so a
     * user cannot create prompts in — or probe for — another user's collection.
     * The {@code ownerId} is stamped from the security context, never the body.
     */
    public Mono<PromptResponse> create(PromptCreateRequest request, Long ownerId) {
        return collectionRepository.findByIdAndOwnerId(request.collectionId(), ownerId)
                .switchIfEmpty(Mono.error(new CollectionNotFoundException(request.collectionId())))
                .flatMap(collection -> {
                    Prompt prompt = new Prompt();
                    prompt.setTitle(request.title());
                    prompt.setContent(request.content());
                    prompt.setCollectionId(collection.getId());
                    prompt.setOwnerId(ownerId);
                    prompt.setCreatedAt(Instant.now());
                    return promptRepository.save(prompt);
                })
                .map(this::toResponse);
    }

    /**
     * Partial update: applies only the non-null fields of the request and leaves
     * the rest unchanged. Scoped to the owner; a missing or unowned prompt yields
     * {@link PromptNotFoundException} (404). The both-null guard is enforced in the
     * controller before this method runs.
     */
    public Mono<PromptResponse> update(Long id, PromptUpdateRequest request, Long ownerId) {
        return promptRepository.findByIdAndOwnerId(id, ownerId)
                .switchIfEmpty(Mono.error(new PromptNotFoundException(id)))
                .flatMap(prompt -> {
                    if (request.title() != null) {
                        prompt.setTitle(request.title());
                    }
                    if (request.content() != null) {
                        prompt.setContent(request.content());
                    }
                    return promptRepository.save(prompt);
                })
                .map(this::toResponse);
    }

    /**
     * Deletes the prompt only when it belongs to {@code ownerId}. If the row does
     * not exist or belongs to a different owner, the delete query returns 0 and a
     * {@link PromptNotFoundException} is emitted — both cases are indistinguishable
     * to the caller, which prevents existence-probing across ownership boundaries.
     */
    public Mono<Void> delete(Long id, Long ownerId) {
        return promptRepository.deleteByIdAndOwnerId(id, ownerId)
                .flatMap(count -> count == 0
                        ? Mono.error(new PromptNotFoundException(id))
                        : Mono.<Void>empty());
    }

    PromptResponse toResponse(Prompt prompt) {
        return new PromptResponse(prompt.getId(), prompt.getTitle(), prompt.getContent(),
                prompt.getCollectionId(), prompt.getOwnerId(), prompt.getCreatedAt());
    }
}
