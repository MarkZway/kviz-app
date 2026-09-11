package ba.sum.kviz.dto;

import java.util.List;

public record QuizStatsResponse(
        Long quizId,
        String quizTitle,
        long participantCount,
        double averageScore,
        int maxPossibleScore,
        List<QuestionStats> questions
) {
    public record QuestionStats(
            Long questionId,
            int orderIndex,
            String type,
            String text,
            long totalAnswers,
            long correctAnswers,
            double successRate,
            double averageTimeSeconds,
            double averagePoints
    ) {}
}