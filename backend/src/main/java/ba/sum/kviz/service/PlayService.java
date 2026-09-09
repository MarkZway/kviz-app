package ba.sum.kviz.service;

import ba.sum.kviz.dto.*;
import ba.sum.kviz.model.*;
import ba.sum.kviz.repository.ParticipationRepository;
import ba.sum.kviz.repository.QuizRepository;
import ba.sum.kviz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PlayService {

    /** Tolerancija za mrežno kašnjenje, u milisekundama. */
    private static final long NETWORK_TOLERANCE_MS = 1500;

    private final ParticipationRepository participationRepository;
    private final QuizRepository quizRepository;
    private final UserRepository userRepository;

    // =========================================================
    // POKRETANJE
    // =========================================================

    @Transactional
    public PlayQuestionResponse start(Long quizId, Long userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> notFound("Kviz nije pronađen"));

        if (quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw conflict("Kviz trenutno nije otvoren za rješavanje");
        }
        if (quiz.getQuestions().isEmpty()) {
            throw conflict("Kviz nema pitanja");
        }

        // MEHANIZAM 1: jedan pokušaj po korisniku
        if (participationRepository.existsByQuizIdAndUserId(quizId, userId)) {
            throw conflict("Ovaj kviz ste već rješavali. Dopušten je samo jedan pokušaj.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> notFound("Korisnik nije pronađen"));

        Participation participation = new Participation();
        participation.setQuiz(quiz);
        participation.setUser(user);
        participation.setStatus(ParticipationStatus.IN_PROGRESS);
        participation.setCurrentQuestionIndex(0);
        participation.setCurrentQuestionStartedAt(null);
        participation.setTotalScore(0);
        participation.setStartedAt(LocalDateTime.now());

        try {
            participationRepository.saveAndFlush(participation);
        } catch (DataIntegrityViolationException e) {
            // Zaštita od paralelnih zahtjeva - uhvatio ju je jedinstveni indeks u bazi
            throw conflict("Ovaj kviz ste već rješavali.");
        }

        return serveCurrentQuestion(participation);
    }

    // =========================================================
    // DOHVAT TRENUTNOG PITANJA
    // =========================================================

    @Transactional
    public PlayQuestionResponse getCurrent(Long participationId, Long userId) {
        Participation participation = getOwnedParticipation(participationId, userId);
        requireInProgress(participation);
        requireQuizTimeNotExpired(participation);
        return serveCurrentQuestion(participation);
    }

    /**
     * Vraća pitanje na trenutnom indeksu i, ako je to prvi dohvat,
     * bilježi serverski trenutak od kojeg teče vrijeme.
     */
    private PlayQuestionResponse serveCurrentQuestion(Participation participation) {
        List<Question> questions = participation.getQuiz().getQuestions();
        int index = participation.getCurrentQuestionIndex();

        if (index >= questions.size()) {
            throw conflict("Sva pitanja su odgovorena");
        }

        Question question = questions.get(index);

        // MEHANIZAM 3: vrijeme kreće pri prvom posluživanju pitanja.
        // Ponovni dohvat ne resetira tajmer.
        if (participation.getCurrentQuestionStartedAt() == null) {
            participation.setCurrentQuestionStartedAt(LocalDateTime.now());
        }

        long elapsedMs = elapsedMs(participation.getCurrentQuestionStartedAt());
        long remainingMs = Math.max(0,
                question.getTimeLimitSeconds() * 1000L - elapsedMs);

        return PlayQuestionResponse.of(
                participation.getId(),
                question,
                index + 1,
                questions.size(),
                remainingMs
        );
    }

    // =========================================================
    // PREDAJA ODGOVORA
    // =========================================================

    @Transactional
    public AnswerResultResponse submitAnswer(Long participationId,
                                             Long questionId,
                                             SubmitAnswerRequest request,
                                             Long userId) {

        Participation participation = getOwnedParticipation(participationId, userId);
        requireInProgress(participation);

        List<Question> questions = participation.getQuiz().getQuestions();
        int index = participation.getCurrentQuestionIndex();

        if (index >= questions.size()) {
            throw conflict("Sva pitanja su već odgovorena");
        }

        Question question = questions.get(index);

        // MEHANIZAM 2: zabrana povratka i preskakanja.
        // Odgovoriti se smije isključivo na pitanje na trenutnom indeksu.
        if (!question.getId().equals(questionId)) {
            throw conflict("Odgovarati se može samo na trenutno pitanje. "
                    + "Povratak na prethodna pitanja nije dopušten.");
        }

        if (participation.getCurrentQuestionStartedAt() == null) {
            throw badRequest("Pitanje još nije dohvaćeno");
        }

        // MEHANIZAM 3: vrijeme se mjeri na serveru
        long timeTakenMs = elapsedMs(participation.getCurrentQuestionStartedAt());
        long limitMs = question.getTimeLimitSeconds() * 1000L;
        boolean expired = timeTakenMs > limitMs + NETWORK_TOLERANCE_MS;

        boolean quizTimeExpired = isQuizTimeExpired(participation);

        boolean correct = !expired && !quizTimeExpired && evaluate(question, request);
        int points = correct ? question.getBasePoints() : 0;

        SubmittedAnswer answer = new SubmittedAnswer();
        answer.setParticipation(participation);
        answer.setQuestion(question);
        answer.setSelectedOption(resolveOption(question, request.selectedOptionId()));
        answer.setTextAnswer(request.textAnswer());
        answer.setCorrect(correct);
        answer.setTimeTakenMs(timeTakenMs);
        answer.setPointsAwarded(points);
        answer.setAnsweredAt(LocalDateTime.now());

        participation.getAnswers().add(answer);
        participation.setTotalScore(participation.getTotalScore() + points);

        // pomak naprijed - povratak više nije moguć
        participation.setCurrentQuestionIndex(index + 1);
        participation.setCurrentQuestionStartedAt(null);

        boolean finished = participation.getCurrentQuestionIndex() >= questions.size()
                || quizTimeExpired;

        if (finished) {
            participation.setStatus(ParticipationStatus.FINISHED);
            participation.setFinishedAt(LocalDateTime.now());
        }

        return new AnswerResultResponse(
                correct, expired || quizTimeExpired, points,
                participation.getTotalScore(), timeTakenMs, finished
        );
    }

    // =========================================================
    // ODUSTAJANJE I PREGLED
    // =========================================================

    @Transactional
    public ParticipationSummaryResponse abandon(Long participationId, Long userId) {
        Participation participation = getOwnedParticipation(participationId, userId);
        requireInProgress(participation);

        participation.setStatus(ParticipationStatus.ABANDONED);
        participation.setFinishedAt(LocalDateTime.now());

        return ParticipationSummaryResponse.from(participation);
    }

    @Transactional(readOnly = true)
    public ParticipationSummaryResponse getSummary(Long participationId, Long userId) {
        return ParticipationSummaryResponse.from(
                getOwnedParticipation(participationId, userId));
    }

    // =========================================================
    // VREDNOVANJE (privremeno - modul 6 ovo seli u ScoringService)
    // =========================================================

    private boolean evaluate(Question question, SubmitAnswerRequest request) {
        return switch (question.getType()) {
            case MULTIPLE_CHOICE, TRUE_FALSE -> {
                if (request.selectedOptionId() == null) yield false;
                yield question.getOptions().stream()
                        .filter(AnswerOption::isCorrect)
                        .anyMatch(o -> o.getId().equals(request.selectedOptionId()));
            }
            case OPEN -> {
                if (request.textAnswer() == null || request.textAnswer().isBlank()) yield false;
                String given = normalize(request.textAnswer());
                yield question.getAcceptableAnswers().stream()
                        .anyMatch(a -> normalize(a.getText()).equals(given));
            }
        };
    }

    private String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private AnswerOption resolveOption(Question question, Long optionId) {
        if (optionId == null) return null;
        return question.getOptions().stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> badRequest("Odabrana opcija ne pripada ovom pitanju"));
    }

    // =========================================================
    // POMOĆNE METODE
    // =========================================================

    private Participation getOwnedParticipation(Long participationId, Long userId) {
        Participation participation = participationRepository.findById(participationId)
                .orElseThrow(() -> notFound("Pokušaj nije pronađen"));

        boolean isOwner = participation.getUser() != null
                && participation.getUser().getId().equals(userId);

        if (!isOwner) {
            throw notFound("Pokušaj nije pronađen");
        }
        return participation;
    }

    private void requireInProgress(Participation participation) {
        if (participation.getStatus() != ParticipationStatus.IN_PROGRESS) {
            throw conflict("Ovaj pokušaj je završen");
        }
    }

    private void requireQuizTimeNotExpired(Participation participation) {
        if (isQuizTimeExpired(participation)) {
            participation.setStatus(ParticipationStatus.FINISHED);
            participation.setFinishedAt(LocalDateTime.now());
            throw conflict("Isteklo je ukupno vrijeme za rješavanje kviza");
        }
    }

    private boolean isQuizTimeExpired(Participation participation) {
        Integer limitMinutes = participation.getQuiz().getTimeLimitMinutes();
        if (limitMinutes == null) return false;

        long elapsedMs = elapsedMs(participation.getStartedAt());
        return elapsedMs > limitMinutes * 60_000L;
    }

    private long elapsedMs(LocalDateTime from) {
        return Duration.between(from, LocalDateTime.now()).toMillis();
    }

    private ResponseStatusException notFound(String msg) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
    }

    private ResponseStatusException conflict(String msg) {
        return new ResponseStatusException(HttpStatus.CONFLICT, msg);
    }

    private ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}