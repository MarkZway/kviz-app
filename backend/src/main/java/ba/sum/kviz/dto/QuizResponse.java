package ba.sum.kviz.dto;

import ba.sum.kviz.model.Quiz;

import java.time.LocalDateTime;

public record QuizResponse(
        Long id,
        String title,
        String description,
        String status,
        String creatorUsername,
        Integer timeLimitMinutes,
        int questionCount,
        LocalDateTime createdAt
) {
    public static QuizResponse from(Quiz quiz) {
        return new QuizResponse(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getStatus().name(),
                quiz.getCreator().getUsername(),
                quiz.getTimeLimitMinutes(),
                quiz.getQuestions().size(),
                quiz.getCreatedAt()
        );
    }
}