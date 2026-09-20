package ba.sum.kviz.controller;

import ba.sum.kviz.dto.CreateQuestionRequest;
import ba.sum.kviz.dto.QuestionResponse;
import ba.sum.kviz.security.UserPrincipal;
import ba.sum.kviz.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/quizzes/{quizId}/questions")
@PreAuthorize("hasRole('ORGANIZER')")
@RequiredArgsConstructor
@Tag(name = "Kvizovi", description = "Kreiranje, uređivanje i objava kvizova")
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping
    public ResponseEntity<QuestionResponse> add(
            @PathVariable Long quizId,
            @Valid @RequestBody CreateQuestionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        QuestionResponse created = questionService.add(quizId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<QuestionResponse> list(
            @PathVariable Long quizId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return questionService.listForOrganizer(quizId, principal.getId());
    }

    @PutMapping("/{questionId}")
    public QuestionResponse update(
            @PathVariable Long quizId,
            @PathVariable Long questionId,
            @Valid @RequestBody CreateQuestionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return questionService.update(quizId, questionId, request, principal.getId());
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long quizId,
            @PathVariable Long questionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        questionService.delete(quizId, questionId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}