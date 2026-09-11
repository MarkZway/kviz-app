package ba.sum.kviz.repository;

import ba.sum.kviz.model.Participation;
import ba.sum.kviz.model.ParticipationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    boolean existsByQuizIdAndUserId(Long quizId, Long userId);

    Optional<Participation> findByQuizIdAndUserId(Long quizId, Long userId);

    List<Participation> findByQuizIdOrderByTotalScoreDesc(Long quizId);

    List<Participation> findByUserIdOrderByStartedAtDesc(Long userId);

    /**
     * Rang-lista: dohvaća pokušaje zajedno s korisnikom i timom u jednom upitu,
     * sortirano po bodovima, pa po trajanju rješavanja.
     */
    @Query("""
            SELECT p FROM Participation p
            LEFT JOIN FETCH p.user
            LEFT JOIN FETCH p.team
            WHERE p.quiz.id = :quizId
              AND p.status = :status
            ORDER BY p.totalScore DESC, p.finishedAt ASC
            """)
    List<Participation> findLeaderboard(@Param("quizId") Long quizId,
                                        @Param("status") ParticipationStatus status);

    @Query("""
            SELECT COUNT(p) FROM Participation p
            WHERE p.quiz.id = :quizId AND p.status = :status
            """)
    long countByQuizIdAndStatus(@Param("quizId") Long quizId,
                                @Param("status") ParticipationStatus status);

    @Query("""
            SELECT AVG(p.totalScore) FROM Participation p
            WHERE p.quiz.id = :quizId AND p.status = :status
            """)
    Double averageScore(@Param("quizId") Long quizId,
                        @Param("status") ParticipationStatus status);
}