package ba.sum.kviz.dto;

import ba.sum.kviz.model.Participation;

import java.time.LocalDateTime;

public record ParticipationSummaryResponse(
        Long id,
        Long quizId,
        String quizTitle,
        String status,
        int totalScore,
        int answeredCount,
        int totalQuestions,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
    public static ParticipationSummaryResponse from(Participation p) {
        return new ParticipationSummaryResponse(
                p.getId(),
                p.getQuiz().getId(),
                p.getQuiz().getTitle(),
                p.getStatus().name(),
                p.getTotalScore(),
                p.getAnswers().size(),
                p.getQuiz().getQuestions().size(),
                p.getStartedAt(),
                p.getFinishedAt()
        );
    }
}