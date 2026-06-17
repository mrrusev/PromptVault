package com.promptvault.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.promptvault.exceptions.PromptNotFoundException;
import com.promptvault.exceptions.PromptVersionNotFoundException;
import com.promptvault.models.PromptResponse;
import com.promptvault.models.PromptVersionResponse;
import com.promptvault.repository.PromptRepository;
import com.promptvault.repository.PromptVersionRepository;
import com.promptvault.repository.entities.Prompt;
import com.promptvault.repository.entities.PromptVersion;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PromptVersionServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long PROMPT_ID = 10L;

    @Mock
    private PromptRepository promptRepository;

    @Mock
    private PromptVersionRepository promptVersionRepository;

    @InjectMocks
    private PromptVersionService promptVersionService;

    private Prompt ownedPrompt(String content) {
        return new Prompt(PROMPT_ID, "Title", content, 5L, OWNER_ID, Instant.now());
    }

    private PromptVersion versionWithNumber(int number) {
        return new PromptVersion(99L, PROMPT_ID, OWNER_ID, "old content", number, Instant.now());
    }

    @Test
    void save_consecutiveCalls_assignSequentialVersionNumbers() {
        when(promptRepository.findByIdAndOwnerId(PROMPT_ID, OWNER_ID))
                .thenReturn(Mono.just(ownedPrompt("live content")));
        when(promptVersionRepository.findTopByPromptIdOrderByVersionNumberDesc(PROMPT_ID))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.just(versionWithNumber(1)))
                .thenReturn(Mono.just(versionWithNumber(2)));
        when(promptVersionRepository.save(any(PromptVersion.class)))
                .thenAnswer(invocation -> {
                    PromptVersion saved = invocation.getArgument(0);
                    saved.setId(100L);
                    return Mono.just(saved);
                });

        StepVerifier.create(promptVersionService.save(PROMPT_ID, OWNER_ID))
                .assertNext(response -> assertThat(response.versionNumber()).isEqualTo(1))
                .verifyComplete();
        StepVerifier.create(promptVersionService.save(PROMPT_ID, OWNER_ID))
                .assertNext(response -> assertThat(response.versionNumber()).isEqualTo(2))
                .verifyComplete();
        StepVerifier.create(promptVersionService.save(PROMPT_ID, OWNER_ID))
                .assertNext(response -> assertThat(response.versionNumber()).isEqualTo(3))
                .verifyComplete();
    }

    @Test
    void save_missingOrUnownedPrompt_emitsPromptNotFound_andNeverSaves() {
        when(promptRepository.findByIdAndOwnerId(PROMPT_ID, OWNER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(promptVersionService.save(PROMPT_ID, OWNER_ID))
                .expectError(PromptNotFoundException.class)
                .verify();

        verify(promptVersionRepository, never()).save(any(PromptVersion.class));
    }

    @Test
    void save_onUniqueCollision_retriesAndRecovers() {
        when(promptRepository.findByIdAndOwnerId(PROMPT_ID, OWNER_ID))
                .thenReturn(Mono.just(ownedPrompt("live content")));
        when(promptVersionRepository.findTopByPromptIdOrderByVersionNumberDesc(PROMPT_ID))
                .thenReturn(Mono.empty());
        when(promptVersionRepository.save(any(PromptVersion.class)))
                .thenReturn(Mono.error(new DataIntegrityViolationException("dup")))
                .thenReturn(Mono.just(versionWithNumber(1)));

        StepVerifier.create(promptVersionService.save(PROMPT_ID, OWNER_ID))
                .assertNext(response -> assertThat(response.versionNumber()).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void restore_copiesVersionContentOntoLivePromptAndSaves() {
        Prompt prompt = ownedPrompt("current content");
        when(promptRepository.findByIdAndOwnerId(PROMPT_ID, OWNER_ID)).thenReturn(Mono.just(prompt));
        when(promptVersionRepository.findByPromptIdAndVersionNumber(PROMPT_ID, 1))
                .thenReturn(Mono.just(new PromptVersion(99L, PROMPT_ID, OWNER_ID, "v1 content", 1, Instant.now())));
        when(promptRepository.save(any(Prompt.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(promptVersionService.restore(PROMPT_ID, 1, OWNER_ID))
                .assertNext(response -> assertThat(response.content()).isEqualTo("v1 content"))
                .verifyComplete();
    }

    @Test
    void restore_unknownVersionNumber_emitsPromptVersionNotFound() {
        when(promptRepository.findByIdAndOwnerId(PROMPT_ID, OWNER_ID))
                .thenReturn(Mono.just(ownedPrompt("current content")));
        when(promptVersionRepository.findByPromptIdAndVersionNumber(PROMPT_ID, 7))
                .thenReturn(Mono.empty());

        StepVerifier.create(promptVersionService.restore(PROMPT_ID, 7, OWNER_ID))
                .expectError(PromptVersionNotFoundException.class)
                .verify();

        verify(promptRepository, never()).save(any(Prompt.class));
    }
}
