package ba.sum.kviz.service;

import ba.sum.kviz.dto.CreateQuestionRequest;
import ba.sum.kviz.dto.QuestionResponse;
import ba.sum.kviz.model.*;
import ba.sum.kviz.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuizService quizService;

    @Transactional
    public QuestionResponse add(Long quizId, CreateQuestionRequest request, Long userId) {
        Quiz quiz = quizService.getOwnedQuizOrThrow(quizId, userId);
        quizService.requireDraft(quiz);
        validate(request);

        Question question = new Question();
        question.setQuiz(quiz);
        question.setOrderIndex((int) questionRepository.countByQuizId(quizId));
        applyRequest(question, request);

        quiz.getQuestions().add(question);
        return QuestionResponse.from(questionRepository.save(question));
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listForOrganizer(Long quizId, Long userId) {
        quizService.getOwnedQuizOrThrow(quizId, userId);
        return questionRepository.findByQuizIdOrderByOrderIndexAsc(quizId)
                .stream()
                .map(QuestionResponse::from)
                .toList();
    }

    @Transactional
    public QuestionResponse update(Long quizId, Long questionId,
                                   CreateQuestionRequest request, Long userId) {
        Quiz quiz = quizService.getOwnedQuizOrThrow(quizId, userId);
        quizService.requireDraft(quiz);
        validate(request);

        Question question = getQuestionOfQuiz(quizId, questionId);
        applyRequest(question, request);

        return QuestionResponse.from(question);
    }

    @Transactional
    public void delete(Long quizId, Long questionId, Long userId) {
        Quiz quiz = quizService.getOwnedQuizOrThrow(quizId, userId);
        quizService.requireDraft(quiz);

        Question question = getQuestionOfQuiz(quizId, questionId);
        int removedIndex = question.getOrderIndex();

        quiz.getQuestions().remove(question);
        questionRepository.delete(question);
        questionRepository.flush();

        questionRepository.findByQuizIdOrderByOrderIndexAsc(quizId).stream()
                .filter(q -> q.getOrderIndex() > removedIndex)
                .forEach(q -> q.setOrderIndex(q.getOrderIndex() - 1));
    }

    // --- pravila po tipu pitanja ---

    private void validate(CreateQuestionRequest r) {
        List<?> options = r.options() == null ? List.of() : r.options();
        List<?> acceptable = r.acceptableAnswers() == null ? List.of() : r.acceptableAnswers();

        switch (r.type()) {
            case MULTIPLE_CHOICE -> {
                if (options.size() < 2) {
                    throw badRequest("Pitanje s višestrukim izborom mora imati barem dvije opcije");
                }
                long correctCount = r.options().stream()
                        .filter(o -> o.correct())
                        .count();
                if (correctCount != 1) {
                    throw badRequest("Mora postojati točno jedna točna opcija, a pronađeno ih je " + correctCount);
                }
                if (!acceptable.isEmpty()) {
                    throw badRequest("Pitanje s višestrukim izborom ne smije imati prihvatljive tekstualne odgovore");
                }
            }
            case TRUE_FALSE -> {
                if (r.correctAnswer() == null) {
                    throw badRequest("Za pitanje tipa točno/netočno mora se navesti correctAnswer");
                }
                if (!options.isEmpty() || !acceptable.isEmpty()) {
                    throw badRequest("Opcije za pitanje tipa točno/netočno generira sustav");
                }
            }
            case OPEN -> {
                if (acceptable.isEmpty()) {
                    throw badRequest("Otvoreno pitanje mora imati barem jedan prihvatljiv odgovor");
                }
                if (!options.isEmpty()) {
                    throw badRequest("Otvoreno pitanje ne smije imati ponuđene opcije");
                }
            }
        }
    }

    private void applyRequest(Question question, CreateQuestionRequest r) {
        question.setType(r.type());
        question.setText(r.text());
        question.setTimeLimitSeconds(r.timeLimitSeconds());
        question.setBasePoints(r.basePoints());

        question.getOptions().clear();
        question.getAcceptableAnswers().clear();

        switch (r.type()) {
            case MULTIPLE_CHOICE -> {
                int i = 0;
                for (var opt : r.options()) {
                    question.getOptions().add(buildOption(question, opt.text(), opt.correct(), i++));
                }
            }
            case TRUE_FALSE -> {
                boolean correct = r.correctAnswer();
                question.getOptions().add(buildOption(question, "Točno", correct, 0));
                question.getOptions().add(buildOption(question, "Netočno", !correct, 1));
            }
            case OPEN -> {
                for (String text : r.acceptableAnswers()) {
                    AcceptableAnswer a = new AcceptableAnswer();
                    a.setQuestion(question);
                    a.setText(text.trim());
                    question.getAcceptableAnswers().add(a);
                }
            }
        }
    }

    private AnswerOption buildOption(Question question, String text, boolean correct, int index) {
        AnswerOption option = new AnswerOption();
        option.setQuestion(question);
        option.setText(text);
        option.setCorrect(correct);
        option.setOrderIndex(index);
        return option;
    }

    private Question getQuestionOfQuiz(Long quizId, Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pitanje nije pronađeno"));

        if (!question.getQuiz().getId().equals(quizId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Pitanje ne pripada navedenom kvizu");
        }
        return question;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}