package ba.sum.kviz.controller;

import ba.sum.kviz.dto.*;
import ba.sum.kviz.security.UserPrincipal;
import ba.sum.kviz.service.PlayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PlayController {

    private final PlayService playService;

    @PostMapping("/api/quizzes/{quizId}/play")
    public PlayQuestionResponse start(
            @PathVariable Long quizId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return playService.start(quizId, principal.getId());
    }

    @GetMapping("/api/participations/{participationId}/current-question")
    public PlayQuestionResponse current(
            @PathVariable Long participationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return playService.getCurrent(participationId, principal.getId());
    }

    @PostMapping("/api/participations/{participationId}/questions/{questionId}/answer")
    public AnswerResultResponse answer(
            @PathVariable Long participationId,
            @PathVariable Long questionId,
            @Valid @RequestBody SubmitAnswerRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return playService.submitAnswer(participationId, questionId, request, principal.getId());
    }

    @PatchMapping("/api/participations/{participationId}/abandon")
    public ParticipationSummaryResponse abandon(
            @PathVariable Long participationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return playService.abandon(participationId, principal.getId());
    }

    @GetMapping("/api/participations/{participationId}")
    public ParticipationSummaryResponse summary(
            @PathVariable Long participationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return playService.getSummary(participationId, principal.getId());
    }

    @PostMapping("/api/quizzes/{quizId}/play-as-team/{teamId}")
    public PlayQuestionResponse startAsTeam(
            @PathVariable Long quizId,
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return playService.startAsTeam(quizId, teamId, principal.getId());
    }
}