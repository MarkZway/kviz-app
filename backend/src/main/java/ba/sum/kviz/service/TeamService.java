package ba.sum.kviz.service;

import ba.sum.kviz.dto.CreateTeamRequest;
import ba.sum.kviz.dto.JoinTeamRequest;
import ba.sum.kviz.dto.TeamResponse;
import ba.sum.kviz.model.Team;
import ba.sum.kviz.model.User;
import ba.sum.kviz.repository.TeamRepository;
import ba.sum.kviz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    /** Bez znakova koji se lako zamijene: 0/O, 1/I/L. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_CODE_ATTEMPTS = 20;

    private final SecureRandom random = new SecureRandom();

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @Transactional
    public TeamResponse create(CreateTeamRequest request, Long userId) {
        if (teamRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tim s tim nazivom već postoji");
        }

        User captain = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Korisnik ne postoji"));

        Team team = new Team();
        team.setName(request.name().trim());
        team.setCaptain(captain);
        team.setJoinCode(generateUniqueCode());
        team.getMembers().add(captain);   // kapetan je ujedno i član

        return TeamResponse.from(teamRepository.save(team), true);
    }

    @Transactional
    public TeamResponse join(JoinTeamRequest request, Long userId) {
        Team team = teamRepository
                .findByJoinCode(request.joinCode().trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ne postoji tim s tim kodom"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Korisnik ne postoji"));

        boolean alreadyMember = team.getMembers().stream()
                .anyMatch(m -> m.getId().equals(userId));

        if (alreadyMember) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Već ste član ovog tima");
        }

        team.getMembers().add(user);
        return TeamResponse.from(team, true);
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> myTeams(Long userId) {
        return teamRepository.findMyTeams(userId).stream()
                .map(team -> TeamResponse.from(team, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse findById(Long teamId, Long userId) {
        Team team = getTeamOrThrow(teamId);
        return TeamResponse.from(team, isMember(team, userId));
    }

    @Transactional
    public TeamResponse leave(Long teamId, Long userId) {
        Team team = getTeamOrThrow(teamId);

        if (!isMember(team, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Niste član ovog tima");
        }
        if (team.getCaptain().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Kapetan ne može napustiti tim. Raspustite tim ili ga prvo predajte drugom članu.");
        }

        team.getMembers().removeIf(m -> m.getId().equals(userId));
        return TeamResponse.from(team, false);
    }

    @Transactional
    public TeamResponse removeMember(Long teamId, Long memberId, Long userId) {
        Team team = getCaptainedTeamOrThrow(teamId, userId);

        if (memberId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Kapetan ne može ukloniti samog sebe");
        }
        boolean removed = team.getMembers().removeIf(m -> m.getId().equals(memberId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Korisnik nije član ovog tima");
        }

        return TeamResponse.from(team, true);
    }

    @Transactional
    public TeamResponse transferCaptaincy(Long teamId, Long newCaptainId, Long userId) {
        Team team = getCaptainedTeamOrThrow(teamId, userId);

        User newCaptain = team.getMembers().stream()
                .filter(m -> m.getId().equals(newCaptainId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Novi kapetan mora biti član tima"));

        team.setCaptain(newCaptain);
        return TeamResponse.from(team, true);
    }

    @Transactional
    public void disband(Long teamId, Long userId) {
        Team team = getCaptainedTeamOrThrow(teamId, userId);
        teamRepository.delete(team);
    }

    // --- pomoćne metode ---

    Team getTeamOrThrow(Long teamId) {
        return teamRepository.findWithMembers(teamId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Tim nije pronađen"));
    }

    private Team getCaptainedTeamOrThrow(Long teamId, Long userId) {
        Team team = getTeamOrThrow(teamId);
        if (!team.getCaptain().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Samo kapetan tima može izvršiti ovu radnju");
        }
        return team;
    }

    boolean isMember(Team team, Long userId) {
        return team.getMembers().stream().anyMatch(m -> m.getId().equals(userId));
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            String candidate = code.toString();
            if (!teamRepository.existsByJoinCode(candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Nije moguće generirati jedinstveni kod");
    }
}