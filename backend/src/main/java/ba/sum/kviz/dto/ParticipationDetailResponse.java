package ba.sum.kviz.dto;

import java.util.List;

public record ParticipationDetailResponse(
        Long participationId,
        Long quizId,
        String quizTitle,
        String status,
        int totalScore,
        int maxPossibleScore,
        int correctAnswers,
        int totalQuestions,
        Long durationMs,
        List<AnswerReview> answers
) {
    public record AnswerReview(
            int questionNumber,
            String questionText,
            String questionType,
            String givenAnswer,
            String correctAnswer,
            boolean correct,
            int pointsAwarded,
            int basePoints,
            long timeTakenMs
    ) {}
}