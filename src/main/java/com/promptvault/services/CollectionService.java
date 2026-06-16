package com.promptvault.services;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.promptvault.exceptions.CollectionNotFoundException;
import com.promptvault.models.CollectionRequest;
import com.promptvault.models.CollectionResponse;
import com.promptvault.repository.CollectionRepository;
import com.promptvault.repository.entities.Collection;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class CollectionService {

    private final CollectionRepository collectionRepository;

    public CollectionService(CollectionRepository collectionRepository) {
        this.collectionRepository = collectionRepository;
    }

    /**
     * Returns all collections owned by the given user. Owner-scoping is enforced
     * at the query level via {@code findAllByOwnerId}, so no cross-user data can
     * be leaked regardless of calling code.
     */
    public Flux<CollectionResponse> findAllByOwner(Long ownerId) {
        return collectionRepository.findAllByOwnerId(ownerId).map(this::toResponse);
    }

    /**
     * Creates a new collection for the given owner and persists it. The
     * {@code ownerId} is taken from the security context in the controller and
     * never from the request body, preventing ownership spoofing.
     */
    public Mono<CollectionResponse> create(CollectionRequest request, Long ownerId) {
        Collection collection = new Collection();
        collection.setName(request.name());
        collection.setOwnerId(ownerId);
        collection.setCreatedAt(Instant.now());
        return collectionRepository.save(collection).map(this::toResponse);
    }

    /**
     * Deletes the collection identified by {@code id} only when it belongs to
     * {@code ownerId}. If the row does not exist or belongs to a different owner,
     * the delete query returns 0 and a {@link CollectionNotFoundException} is
     * emitted — both cases are indistinguishable to the caller, which prevents
     * existence-probing across ownership boundaries.
     */
    public Mono<Void> delete(Long id, Long ownerId) {
        return collectionRepository.deleteByIdAndOwnerId(id, ownerId)
                .flatMap(count -> count == 0
                        ? Mono.error(new CollectionNotFoundException(id))
                        : Mono.<Void>empty());
    }

    private CollectionResponse toResponse(Collection collection) {
        return new CollectionResponse(collection.getId(), collection.getName(),
                collection.getOwnerId(), collection.getCreatedAt());
    }
}
