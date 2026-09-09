package ba.sum.kviz.dto;

import ba.sum.kviz.model.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CreateQuestionRequest(
        @NotNull QuestionType type,
        @NotBlank @Size(max = 2000) String text,
        @NotNull @Min(5) @Max(600) Integer timeLimitSeconds,
        @NotNull @Min(1) @Max(1000) Integer basePoints,

        @Valid List<AnswerOptionRequest> options,
        List<@NotBlank @Size(max = 500) String> acceptableAnswers,
        Boolean correctAnswer
) {}