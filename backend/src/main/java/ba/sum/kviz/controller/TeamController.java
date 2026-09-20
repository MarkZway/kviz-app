package ba.sum.kviz.controller;

import ba.sum.kviz.dto.CreateTeamRequest;
import ba.sum.kviz.dto.JoinTeamRequest;
import ba.sum.kviz.dto.TeamResponse;
import ba.sum.kviz.security.UserPrincipal;
import ba.sum.kviz.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Tag(name = "Rezultati", description = "Rang-lista, statistika i pregled pokušaja")
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public ResponseEntity<TeamResponse> create(
            @Valid @RequestBody CreateTeamRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TeamResponse created = teamService.create(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/join")
    public TeamResponse join(
            @Valid @RequestBody JoinTeamRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return teamService.join(request, principal.getId());
    }

    @GetMapping("/mine")
    public List<TeamResponse> myTeams(@AuthenticationPrincipal UserPrincipal principal) {
        return teamService.myTeams(principal.getId());
    }

    @GetMapping("/{teamId}")
    public TeamResponse getOne(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return teamService.findById(teamId, principal.getId());
    }

    @DeleteMapping("/{teamId}/members/me")
    public TeamResponse leave(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return teamService.leave(teamId, principal.getId());
    }

    @DeleteMapping("/{teamId}/members/{memberId}")
    public TeamResponse removeMember(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return teamService.removeMember(teamId, memberId, principal.getId());
    }

    @PatchMapping("/{teamId}/captain/{newCaptainId}")
    public TeamResponse transferCaptaincy(
            @PathVariable Long teamId,
            @PathVariable Long newCaptainId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return teamService.transferCaptaincy(teamId, newCaptainId, principal.getId());
    }

    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> disband(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal principal) {
        teamService.disband(teamId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}