package ba.sum.kviz.service.scoring;

public record ScoringResult(
        boolean correct,
        boolean expired,
        int pointsAwarded
) {
    public static ScoringResult ofExpired() {
        return new ScoringResult(false, true, 0);
    }

    public static ScoringResult ofWrong() {
        return new ScoringResult(false, false, 0);
    }

    public static ScoringResult ofCorrect(int points) {
        return new ScoringResult(true, false, points);
    }
}