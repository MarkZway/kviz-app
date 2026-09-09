package ba.sum.kviz.dto;

import ba.sum.kviz.model.Question;

import java.util.List;

public record PlayQuestionResponse(
        Long participationId,
        Long questionId,
        String type,
        String text,
        int questionNumber,
        int totalQuestions,
        Integer timeLimitSeconds,
        long remainingMs,
        List<PlayOption> options
) {
    public record PlayOption(Long id, String text) {}

    public static PlayQuestionResponse of(Long participationId,
                                          Question question,
                                          int questionNumber,
                                          int totalQuestions,
                                          long remainingMs) {
        return new PlayQuestionResponse(
                participationId,
                question.getId(),
                question.getType().name(),
                question.getText(),
                questionNumber,
                totalQuestions,
                question.getTimeLimitSeconds(),
                remainingMs,
                question.getOptions().stream()
                        .map(o -> new PlayOption(o.getId(), o.getText()))
                        .toList()
        );
    }
}