package ba.sum.kviz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinTeamRequest(
        @NotBlank @Size(min = 4, max = 8) String joinCode
) {}