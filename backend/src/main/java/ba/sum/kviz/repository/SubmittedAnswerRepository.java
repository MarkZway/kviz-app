package ba.sum.kviz.repository;

import ba.sum.kviz.model.SubmittedAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmittedAnswerRepository extends JpaRepository<SubmittedAnswer, Long> {

    List<SubmittedAnswer> findByParticipationIdOrderByIdAsc(Long participationId);
}