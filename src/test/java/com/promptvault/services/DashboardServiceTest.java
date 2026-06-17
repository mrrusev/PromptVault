package com.promptvault.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.promptvault.repository.CollectionRepository;
import com.promptvault.repository.PromptRepository;
import com.promptvault.repository.PromptVersionRepository;
import com.promptvault.repository.entities.Prompt;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long OWNER_ID = 10L;
    private static final Long COLLECTION_ID = 5L;

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private PromptRepository promptRepository;

    @Mock
    private PromptVersionRepository promptVersionRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        // Use a REAL PromptService so toResponse runs for real (not mocked).
        PromptService promptService = new PromptService(promptRepository, collectionRepository);
        dashboardService = new DashboardService(collectionRepository, promptRepository,
                promptVersionRepository, promptService);
    }

    @Test
    void getDashboard_withData_returnsCountsAndLatestPrompt() {
        Instant createdAt = Instant.now();
        Prompt latest = new Prompt(42L, "Latest", "Most recent content", COLLECTION_ID, OWNER_ID, createdAt);
        when(collectionRepository.countByOwnerId(OWNER_ID)).thenReturn(Mono.just(2L));
        when(promptRepository.countByOwnerId(OWNER_ID)).thenReturn(Mono.just(3L));
        when(promptVersionRepository.countByOwnerId(OWNER_ID)).thenReturn(Mono.just(5L));
        when(promptRepository.findFirstByOwnerIdOrderByCreatedAtDesc(OWNER_ID)).thenReturn(Mono.just(latest));

        StepVerifier.create(dashboardService.getDashboard(OWNER_ID))
                .assertNext(response -> {
                    assertThat(response.totalCollections()).isEqualTo(2L);
                    assertThat(response.totalPrompts()).isEqualTo(3L);
                    assertThat(response.totalVersions()).isEqualTo(5L);
                    assertThat(response.latestPrompt()).isNotNull();
                    assertThat(response.latestPrompt().id()).isEqualTo(42L);
                    assertThat(response.latestPrompt().title()).isEqualTo("Latest");
                    assertThat(response.latestPrompt().content()).isEqualTo("Most recent content");
                    assertThat(response.latestPrompt().collectionId()).isEqualTo(COLLECTION_ID);
                    assertThat(response.latestPrompt().ownerId()).isEqualTo(OWNER_ID);
                    assertThat(response.latestPrompt().createdAt()).isEqualTo(createdAt);
                })
                .verifyComplete();
    }

    @Test
    void getDashboard_withNoPrompts_returnsZeroCountsAndNullLatest() {
        // Regression guard for the Mono.zip empty trap: if the latest-prompt source
        // completed empty without the Optional lift, the whole tuple would be discarded
        // and the response would never be emitted.
        when(collectionRepository.countByOwnerId(OWNER_ID)).thenReturn(Mono.just(0L));
        when(promptRepository.countByOwnerId(OWNER_ID)).thenReturn(Mono.just(0L));
        when(promptVersionRepository.countByOwnerId(OWNER_ID)).thenReturn(Mono.just(0L));
        when(promptRepository.findFirstByOwnerIdOrderByCreatedAtDesc(OWNER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(dashboardService.getDashboard(OWNER_ID))
                .assertNext(response -> {
                    assertThat(response.totalCollections()).isZero();
                    assertThat(response.totalPrompts()).isZero();
                    assertThat(response.totalVersions()).isZero();
                    assertThat(response.latestPrompt()).isNull();
                })
                .verifyComplete();
    }
}
