package ba.sum.kviz.dto;

public record AuthResponse(
        String token,
        Long userId,
        String username,
        String role
) {}