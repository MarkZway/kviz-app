package ba.sum.kviz.controller;

import ba.sum.kviz.dto.*;
import ba.sum.kviz.security.UserPrincipal;
import ba.sum.kviz.service.PlayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequiredArgsConstructor
@Tag(name = "Rješavanje kviza", description = "Tok rješavanja i kontrolni mehanizmi")
public class PlayController {

    private final PlayService playService;

    @Operation(
            summary = "Pokreće rješavanje kviza",
            description = """
                    Stvara novi pokušaj i vraća prvo pitanje.
                    Dopušten je samo jedan pokušaj po korisniku, odnosno po timu.
                    Vrijeme za prvo pitanje počinje teći u trenutku posluživanja.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vraćeno prvo pitanje"),
            @ApiResponse(responseCode = "409", description = "Kviz nije objavljen ili je već rješavan"),
            @ApiResponse(responseCode = "404", description = "Kviz nije pronađen")
    })

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

    @Operation(
            summary = "Predaje odgovor na trenutno pitanje",
            description = """
                    Vrijeme se mjeri na poslužitelju, od trenutka posluživanja pitanja.
                    Broj bodova ovisi o točnosti i brzini: bodovi = P × (1 − t/2T).
                    Odgovarati je moguće isključivo na pitanje na trenutnom indeksu —
                    povratak na prethodna i preskakanje nisu dopušteni.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Odgovor zabilježen i vrednovan"),
            @ApiResponse(responseCode = "409", description = "Pokušaj povratka, preskakanja ili rad na završenom pokušaju")
    })

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