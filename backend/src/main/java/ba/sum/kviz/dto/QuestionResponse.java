package ba.sum.kviz.dto;

import ba.sum.kviz.model.Question;

import java.util.List;

public record QuestionResponse(
        Long id,
        String type,
        String text,
        Integer timeLimitSeconds,
        Integer basePoints,
        Integer orderIndex,
        List<AnswerOptionResponse> options,
        List<String> acceptableAnswers
) {
    public record AnswerOptionResponse(Long id, String text, boolean correct) {}

    public static QuestionResponse from(Question q) {
        return new QuestionResponse(
                q.getId(),
                q.getType().name(),
                q.getText(),
                q.getTimeLimitSeconds(),
                q.getBasePoints(),
                q.getOrderIndex(),
                q.getOptions().stream()
                        .map(o -> new AnswerOptionResponse(o.getId(), o.getText(), o.isCorrect()))
                        .toList(),
                q.getAcceptableAnswers().stream()
                        .map(a -> a.getText())
                        .toList()
        );
    }
}