package ba.sum.kviz.repository.projection;

public interface QuestionStatsProjection {
    Long getQuestionId();
    Long getTotalAnswers();
    Long getCorrectAnswers();
    Double getAverageTimeMs();
    Double getAveragePoints();
}