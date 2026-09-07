package ba.sum.kviz.controller;

import ba.sum.kviz.dto.CreateQuizRequest;
import ba.sum.kviz.dto.QuizResponse;
import ba.sum.kviz.security.UserPrincipal;
import ba.sum.kviz.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<QuizResponse> create(
            @Valid @RequestBody CreateQuizRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        QuizResponse created = quizService.create(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('ORGANIZER')")
    public List<QuizResponse> myQuizzes(@AuthenticationPrincipal UserPrincipal principal) {
        return quizService.findMyQuizzes(principal.getId());
    }

    @GetMapping
    public List<QuizResponse> published() {
        return quizService.findPublished();
    }

    @GetMapping("/{id}")
    public QuizResponse getOne(@PathVariable Long id) {
        return quizService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public QuizResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CreateQuizRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return quizService.update(id, request, principal.getId());
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasRole('ORGANIZER')")
    public QuizResponse publish(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return quizService.publish(id, principal.getId());
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasRole('ORGANIZER')")
    public QuizResponse close(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return quizService.close(id, principal.getId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        quizService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}