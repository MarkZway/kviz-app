package ba.sum.kviz.repository;

import ba.sum.kviz.model.Participation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    boolean existsByQuizIdAndUserId(Long quizId, Long userId);

    Optional<Participation> findByQuizIdAndUserId(Long quizId, Long userId);

    List<Participation> findByQuizIdOrderByTotalScoreDesc(Long quizId);
}