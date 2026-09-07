package ba.sum.kviz.repository;

import ba.sum.kviz.model.Quiz;
import ba.sum.kviz.model.QuizStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findByCreatorIdOrderByCreatedAtDesc(Long creatorId);

    List<Quiz> findByStatusOrderByCreatedAtDesc(QuizStatus status);
}