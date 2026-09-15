package ba.sum.kviz.dto;

import ba.sum.kviz.model.Team;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Comparator;

public record TeamResponse(
        Long id,
        String name,
        String joinCode,
        Long captainId,
        String captainUsername,
        List<TeamMember> members,
        LocalDateTime createdAt
) {
    public record TeamMember(Long id, String username, boolean captain) {}


    public static TeamResponse from(Team team, boolean includeJoinCode) {
        Long captainId = team.getCaptain().getId();

        List<TeamMember> members = team.getMembers().stream()
                .sorted(Comparator.comparing(u -> u.getUsername().toLowerCase()))
                .map(u -> new TeamMember(u.getId(), u.getUsername(),
                        u.getId().equals(captainId)))
                .toList();

        return new TeamResponse(
                team.getId(),
                team.getName(),
                includeJoinCode ? team.getJoinCode() : null,
                captainId,
                team.getCaptain().getUsername(),
                members,
                team.getCreatedAt()
        );
    }
}