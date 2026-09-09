package ba.sum.kviz.service;

import ba.sum.kviz.dto.CreateQuizRequest;
import ba.sum.kviz.dto.QuizResponse;
import ba.sum.kviz.model.Quiz;
import ba.sum.kviz.model.QuizStatus;
import ba.sum.kviz.model.User;
import ba.sum.kviz.repository.QuizRepository;
import ba.sum.kviz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository quizRepository;
    private final UserRepository userRepository;

    @Transactional
    public QuizResponse create(CreateQuizRequest request, Long userId) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Korisnik ne postoji"));

        Quiz quiz = new Quiz();
        quiz.setTitle(request.title());
        quiz.setDescription(request.description());
        quiz.setTimeLimitMinutes(request.timeLimitMinutes());
        quiz.setCreator(creator);
        quiz.setStatus(QuizStatus.DRAFT);

        return QuizResponse.from(quizRepository.save(quiz));
    }

    @Transactional(readOnly = true)
    public List<QuizResponse> findMyQuizzes(Long userId) {
        return quizRepository.findByCreatorIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(QuizResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuizResponse> findPublished() {
        return quizRepository.findByStatusOrderByCreatedAtDesc(QuizStatus.PUBLISHED)
                .stream()
                .map(QuizResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuizResponse findById(Long quizId) {
        return QuizResponse.from(getQuizOrThrow(quizId));
    }

    @Transactional
    public QuizResponse update(Long quizId, CreateQuizRequest request, Long userId) {
        Quiz quiz = getOwnedQuizOrThrow(quizId, userId);
        requireDraft(quiz);

        quiz.setTitle(request.title());
        quiz.setDescription(request.description());
        quiz.setTimeLimitMinutes(request.timeLimitMinutes());

        return QuizResponse.from(quiz);
    }

    @Transactional
    public QuizResponse publish(Long quizId, Long userId) {
        Quiz quiz = getOwnedQuizOrThrow(quizId, userId);
        requireDraft(quiz);

        if (quiz.getQuestions().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kviz mora imati barem jedno pitanje prije objave");
        }

        quiz.setStatus(QuizStatus.PUBLISHED);
        return QuizResponse.from(quiz);
    }

    @Transactional
    public QuizResponse close(Long quizId, Long userId) {
        Quiz quiz = getOwnedQuizOrThrow(quizId, userId);

        if (quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Zatvoriti se može samo objavljeni kviz");
        }

        quiz.setStatus(QuizStatus.CLOSED);
        return QuizResponse.from(quiz);
    }

    @Transactional
    public void delete(Long quizId, Long userId) {
        Quiz quiz = getOwnedQuizOrThrow(quizId, userId);
        requireDraft(quiz);
        quizRepository.delete(quiz);
    }

    // --- pomoćne metode ---

    Quiz getQuizOrThrow(Long quizId) {
        return quizRepository.findById(quizId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Kviz nije pronađen"));
    }

    Quiz getOwnedQuizOrThrow(Long quizId, Long userId) {
        Quiz quiz = getQuizOrThrow(quizId);
        if (!quiz.getCreator().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Nemate pravo uređivati ovaj kviz");
        }
        return quiz;
    }

    void requireDraft(Quiz quiz) {
        if (quiz.getStatus() != QuizStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Kviz se može mijenjati samo dok je u statusu DRAFT");
        }
    }
}