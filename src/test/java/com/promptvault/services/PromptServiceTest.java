package com.promptvault.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.promptvault.exceptions.CollectionNotFoundException;
import com.promptvault.exceptions.PromptNotFoundException;
import com.promptvault.models.PromptCreateRequest;
import com.promptvault.models.PromptUpdateRequest;
import com.promptvault.repository.CollectionRepository;
import com.promptvault.repository.PromptRepository;
import com.promptvault.repository.entities.Collection;
import com.promptvault.repository.entities.Prompt;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PromptServiceTest {

    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_OWNER_ID = 99L;
    private static final Long COLLECTION_ID = 5L;

    @Mock
    private PromptRepository promptRepository;

    @Mock
    private CollectionRepository collectionRepository;

    private PromptService promptService;

    @BeforeEach
    void setUp() {
        promptService = new PromptService(promptRepository, collectionRepository);
    }

    @Test
    void create_inOwnedCollection_stampsOwnerIdAndReturnsResponse() {
        PromptCreateRequest request = new PromptCreateRequest(COLLECTION_ID, "Refactor", "Make it reactive");
        Collection ownedCollection = new Collection(COLLECTION_ID, "Work", OWNER_ID, Instant.now());
        when(collectionRepository.findByIdAndOwnerId(COLLECTION_ID, OWNER_ID))
                .thenReturn(Mono.just(ownedCollection));
        when(promptRepository.save(any(Prompt.class)))
                .thenAnswer(invocation -> {
                    Prompt prompt = invocation.getArgument(0);
                    prompt.setId(42L);
                    return Mono.just(prompt);
                });

        StepVerifier.create(promptService.create(request, OWNER_ID))
                .assertNext(response -> {
                    assertThat(response.id()).isEqualTo(42L);
                    assertThat(response.title()).isEqualTo("Refactor");
                    assertThat(response.content()).isEqualTo("Make it reactive");
                    assertThat(response.collectionId()).isEqualTo(COLLECTION_ID);
                    assertThat(response.ownerId()).isEqualTo(OWNER_ID);
                    assertThat(response.createdAt()).isNotNull();
                })
                .verifyComplete();

        verify(collectionRepository).findByIdAndOwnerId(COLLECTION_ID, OWNER_ID);
    }

    @Test
    void create_inUnownedOrMissingCollection_emitsCollectionNotFound() {
        PromptCreateRequest request = new PromptCreateRequest(COLLECTION_ID, "Refactor", "content");
        when(collectionRepository.findByIdAndOwnerId(COLLECTION_ID, OWNER_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(promptService.create(request, OWNER_ID))
                .expectError(CollectionNotFoundException.class)
                .verify();

        verify(promptRepository, never()).save(any(Prompt.class));
    }

    @Test
    void update_appliesOnlyTitle_leavesContentUnchanged() {
        Prompt existing = new Prompt(1L, "Old title", "Original content", COLLECTION_ID, OWNER_ID, Instant.now());
        when(promptRepository.findByIdAndOwnerId(1L, OWNER_ID)).thenReturn(Mono.just(existing));
        when(promptRepository.save(any(Prompt.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        PromptUpdateRequest request = new PromptUpdateRequest("New title", null);

        StepVerifier.create(promptService.update(1L, request, OWNER_ID))
                .assertNext(response -> {
                    assertThat(response.title()).isEqualTo("New title");
                    assertThat(response.content()).isEqualTo("Original content");
                })
                .verifyComplete();
    }

    @Test
    void update_appliesOnlyContent_leavesTitleUnchanged() {
        Prompt existing = new Prompt(1L, "Original title", "Old content", COLLECTION_ID, OWNER_ID, Instant.now());
        when(promptRepository.findByIdAndOwnerId(1L, OWNER_ID)).thenReturn(Mono.just(existing));
        when(promptRepository.save(any(Prompt.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        PromptUpdateRequest request = new PromptUpdateRequest(null, "New content");

        StepVerifier.create(promptService.update(1L, request, OWNER_ID))
                .assertNext(response -> {
                    assertThat(response.title()).isEqualTo("Original title");
                    assertThat(response.content()).isEqualTo("New content");
                })
                .verifyComplete();
    }

    @Test
    void update_missingOrUnownedPrompt_emitsPromptNotFound() {
        when(promptRepository.findByIdAndOwnerId(404L, OWNER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(promptService.update(404L, new PromptUpdateRequest("x", null), OWNER_ID))
                .expectError(PromptNotFoundException.class)
                .verify();
    }

    @Test
    void findById_missingOrUnownedPrompt_emitsPromptNotFound() {
        when(promptRepository.findByIdAndOwnerId(404L, OWNER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(promptService.findById(404L, OWNER_ID))
                .expectError(PromptNotFoundException.class)
                .verify();
    }

    @Test
    void findById_ownedPrompt_returnsResponse() {
        Prompt existing = new Prompt(1L, "Title", "Content", COLLECTION_ID, OWNER_ID, Instant.now());
        when(promptRepository.findByIdAndOwnerId(1L, OWNER_ID)).thenReturn(Mono.just(existing));

        StepVerifier.create(promptService.findById(1L, OWNER_ID))
                .assertNext(response -> assertThat(response.id()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void delete_ownedPrompt_completesEmpty() {
        when(promptRepository.deleteByIdAndOwnerId(1L, OWNER_ID)).thenReturn(Mono.just(1L));

        StepVerifier.create(promptService.delete(1L, OWNER_ID))
                .verifyComplete();
    }

    @Test
    void delete_missingOrUnownedPrompt_emitsPromptNotFound() {
        // deleteByIdAndOwnerId returns 0 for both not-found and owner mismatch — indistinguishable.
        when(promptRepository.deleteByIdAndOwnerId(1L, OTHER_OWNER_ID)).thenReturn(Mono.just(0L));

        StepVerifier.create(promptService.delete(1L, OTHER_OWNER_ID))
                .expectError(PromptNotFoundException.class)
                .verify();
    }

    @Test
    void findAll_withoutCollectionId_usesFindAllByOwnerId() {
        Prompt prompt = new Prompt(1L, "Title", "Content", COLLECTION_ID, OWNER_ID, Instant.now());
        when(promptRepository.findAllByOwnerId(OWNER_ID)).thenReturn(Flux.just(prompt));

        StepVerifier.create(promptService.findAll(OWNER_ID, null))
                .assertNext(response -> assertThat(response.id()).isEqualTo(1L))
                .verifyComplete();

        verify(promptRepository).findAllByOwnerId(OWNER_ID);
        verify(promptRepository, never()).findAllByOwnerIdAndCollectionId(any(), any());
    }

    @Test
    void findAll_withCollectionId_usesFindAllByOwnerIdAndCollectionId() {
        Prompt prompt = new Prompt(1L, "Title", "Content", COLLECTION_ID, OWNER_ID, Instant.now());
        when(promptRepository.findAllByOwnerIdAndCollectionId(OWNER_ID, COLLECTION_ID))
                .thenReturn(Flux.just(prompt));

        StepVerifier.create(promptService.findAll(OWNER_ID, COLLECTION_ID))
                .assertNext(response -> assertThat(response.collectionId()).isEqualTo(COLLECTION_ID))
                .verifyComplete();

        verify(promptRepository).findAllByOwnerIdAndCollectionId(OWNER_ID, COLLECTION_ID);
        verify(promptRepository, never()).findAllByOwnerId(any());
    }
}
