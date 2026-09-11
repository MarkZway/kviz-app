package ba.sum.kviz.repository;

import ba.sum.kviz.model.SubmittedAnswer;
import ba.sum.kviz.repository.projection.QuestionStatsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubmittedAnswerRepository extends JpaRepository<SubmittedAnswer, Long> {

    @Query("""
            SELECT sa FROM SubmittedAnswer sa
            JOIN FETCH sa.question q
            LEFT JOIN FETCH sa.selectedOption
            WHERE sa.participation.id = :participationId
            ORDER BY q.orderIndex ASC
            """)
    List<SubmittedAnswer> findDetailedByParticipation(
            @Param("participationId") Long participationId);

    /**
     * Statistika po pitanju: koliko je odgovora stiglo, koliko ih je točno,
     * prosječno vrijeme i prosječan broj bodova.
     */
    @Query("""
            SELECT sa.question.id            AS questionId,
                   COUNT(sa)                 AS totalAnswers,
                   SUM(CASE WHEN sa.correct = true THEN 1 ELSE 0 END) AS correctAnswers,
                   AVG(sa.timeTakenMs)       AS averageTimeMs,
                   AVG(sa.pointsAwarded)     AS averagePoints
            FROM SubmittedAnswer sa
            WHERE sa.question.quiz.id = :quizId
            GROUP BY sa.question.id
            """)
    List<QuestionStatsProjection> findQuestionStats(@Param("quizId") Long quizId);
}