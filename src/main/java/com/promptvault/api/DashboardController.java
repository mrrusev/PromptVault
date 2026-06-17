package com.promptvault.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.promptvault.auth.AuthenticatedUser;
import com.promptvault.models.DashboardResponse;
import com.promptvault.services.DashboardService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * GET /dashboard — 200 OK with the authenticated user's aggregate statistics:
     * total collections, total prompts, total versions, and the latest prompt.
     * Every figure is owner-scoped, so no cross-user data is exposed.
     * <p>
     * "Latest" means the most recently <em>created</em> prompt (ORDER BY createdAt DESC).
     * Prompt has no {@code updatedAt} field and PATCH does not touch {@code createdAt},
     * so editing a prompt does not change which prompt the dashboard reports as latest —
     * this is intentional and consistent, not a bug.
     * <p>
     * Requires a valid Bearer token; returns 401 when absent or invalid (the security
     * config protects every exchange via {@code .anyExchange().authenticated()}).
     */
    @GetMapping
    public Mono<DashboardResponse> getDashboard(@AuthenticationPrincipal AuthenticatedUser principal) {
        return dashboardService.getDashboard(principal.id());
    }
}
