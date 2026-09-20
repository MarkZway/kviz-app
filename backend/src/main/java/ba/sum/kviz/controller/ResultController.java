package ba.sum.kviz.controller;

import ba.sum.kviz.dto.*;
import ba.sum.kviz.security.UserPrincipal;
import ba.sum.kviz.service.ResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Timovi", description = "Kreiranje timova i upravljanje članstvom")
public class ResultController {

    private final ResultService resultService;

    @GetMapping("/api/quizzes/{quizId}/leaderboard")
    public List<LeaderboardEntry> leaderboard(@PathVariable Long quizId) {
        return resultService.leaderboard(quizId);
    }

    @GetMapping("/api/quizzes/{quizId}/stats")
    @PreAuthorize("hasRole('ORGANIZER')")
    public QuizStatsResponse stats(
            @PathVariable Long quizId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return resultService.quizStats(quizId, principal.getId());
    }

    @GetMapping("/api/participations/{participationId}/result")
    public ParticipationDetailResponse myResult(
            @PathVariable Long participationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return resultService.myResult(participationId, principal.getId());
    }

    @GetMapping("/api/participations/mine")
    public List<ParticipationSummaryResponse> myParticipations(
            @AuthenticationPrincipal UserPrincipal principal) {
        return resultService.myParticipations(principal.getId());
    }
}