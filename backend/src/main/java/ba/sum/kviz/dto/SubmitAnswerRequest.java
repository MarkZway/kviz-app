package ba.sum.kviz.dto;

import jakarta.validation.constraints.Size;

public record SubmitAnswerRequest(
        Long selectedOptionId,
        @Size(max = 500) String textAnswer
) {}