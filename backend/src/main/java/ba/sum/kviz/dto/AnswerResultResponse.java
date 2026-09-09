package ba.sum.kviz.dto;

public record AnswerResultResponse(
        boolean correct,
        boolean expired,
        int pointsAwarded,
        int totalScore,
        long timeTakenMs,
        boolean quizFinished
) {}