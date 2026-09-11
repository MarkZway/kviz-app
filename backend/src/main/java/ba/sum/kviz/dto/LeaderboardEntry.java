package ba.sum.kviz.dto;

public record LeaderboardEntry(
        int rank,
        Long participationId,
        String participantName,
        boolean isTeam,
        int totalScore,
        int correctAnswers,
        int totalQuestions,
        Long durationMs
) {}