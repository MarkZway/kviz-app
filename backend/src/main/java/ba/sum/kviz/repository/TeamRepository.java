package ba.sum.kviz.repository;

import ba.sum.kviz.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);

    boolean existsByNameIgnoreCase(String name);

    @Query("""
            SELECT t FROM Team t
            LEFT JOIN FETCH t.members
            LEFT JOIN FETCH t.captain
            WHERE t.id = :teamId
            """)
    Optional<Team> findWithMembers(@Param("teamId") Long teamId);

    @Query("""
            SELECT DISTINCT t FROM Team t
            LEFT JOIN FETCH t.members m
            LEFT JOIN FETCH t.captain
            WHERE EXISTS (SELECT 1 FROM Team t2 JOIN t2.members m2
                          WHERE t2.id = t.id AND m2.id = :userId)
            ORDER BY t.createdAt DESC
            """)
    List<Team> findMyTeams(@Param("userId") Long userId);
}