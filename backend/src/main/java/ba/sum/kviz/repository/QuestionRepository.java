package ba.sum.kviz.repository;

import ba.sum.kviz.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByQuizIdOrderByOrderIndexAsc(Long quizId);

    long countByQuizId(Long quizId);
}