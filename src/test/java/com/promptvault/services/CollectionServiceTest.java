package com.promptvault.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.promptvault.exceptions.CollectionNotFoundException;
import com.promptvault.models.CollectionRequest;
import com.promptvault.models.CollectionResponse;
import com.promptvault.repository.CollectionRepository;
import com.promptvault.repository.entities.Collection;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock
    private CollectionRepository collectionRepository;

    private CollectionService collectionService;

    @BeforeEach
    void setUp() {
        collectionService = new CollectionService(collectionRepository);
    }

    @Test
    void findAllByOwner_returnsOwnerCollections() {
        Collection c1 = new Collection(1L, "Prompts A", 10L, Instant.now());
        Collection c2 = new Collection(2L, "Prompts B", 10L, Instant.now());
        when(collectionRepository.findAllByOwnerId(10L)).thenReturn(Flux.just(c1, c2));

        StepVerifier.create(collectionService.findAllByOwner(10L))
                .assertNext(r -> {
                    assertThat(r.id()).isEqualTo(1L);
                    assertThat(r.name()).isEqualTo("Prompts A");
                    assertThat(r.ownerId()).isEqualTo(10L);
                })
                .assertNext(r -> {
                    assertThat(r.id()).isEqualTo(2L);
                    assertThat(r.name()).isEqualTo("Prompts B");
                })
                .verifyComplete();
    }

    @Test
    void findAllByOwner_emptyFluxIsValid() {
        when(collectionRepository.findAllByOwnerId(10L)).thenReturn(Flux.empty());

        StepVerifier.create(collectionService.findAllByOwner(10L))
                .verifyComplete();
    }

    @Test
    void create_savesAndReturnsWithGeneratedId() {
        CollectionRequest request = new CollectionRequest("My Prompts");
        when(collectionRepository.save(any(Collection.class)))
                .thenAnswer(invocation -> {
                    Collection c = invocation.getArgument(0);
                    c.setId(42L);
                    c.setCreatedAt(Instant.now());
                    return Mono.just(c);
                });

        StepVerifier.create(collectionService.create(request, 10L))
                .assertNext(r -> {
                    assertThat(r.id()).isEqualTo(42L);
                    assertThat(r.name()).isEqualTo("My Prompts");
                    assertThat(r.ownerId()).isEqualTo(10L);
                    assertThat(r.createdAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void delete_ownedCollection_completesEmpty() {
        // deleteByIdAndOwnerId returning 1 means one row was deleted (owned)
        when(collectionRepository.deleteByIdAndOwnerId(5L, 10L)).thenReturn(Mono.just(1L));

        StepVerifier.create(collectionService.delete(5L, 10L))
                .verifyComplete();
    }

    @Test
    void delete_notFound_emitsCollectionNotFoundException() {
        // deleteByIdAndOwnerId returning 0 means no row was deleted (not found)
        when(collectionRepository.deleteByIdAndOwnerId(999L, 10L)).thenReturn(Mono.just(0L));

        StepVerifier.create(collectionService.delete(999L, 10L))
                .expectError(CollectionNotFoundException.class)
                .verify();
    }

    @Test
    void delete_otherOwnersCollection_emitsCollectionNotFoundException() {
        // owner_id mismatch — the query returns 0, same as not found; no data leakage
        when(collectionRepository.deleteByIdAndOwnerId(5L, 99L)).thenReturn(Mono.just(0L));

        StepVerifier.create(collectionService.delete(5L, 99L))
                .expectError(CollectionNotFoundException.class)
                .verify();
    }
}
