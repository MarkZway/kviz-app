package ba.sum.kviz.service;

import ba.sum.kviz.dto.*;
import ba.sum.kviz.model.*;
import ba.sum.kviz.repository.ParticipationRepository;
import ba.sum.kviz.repository.QuizRepository;
import ba.sum.kviz.repository.TeamRepository;
import ba.sum.kviz.repository.UserRepository;
import ba.sum.kviz.service.scoring.ScoringResult;
import ba.sum.kviz.service.scoring.ScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlayService {

    /** Tolerancija za mrežno kašnjenje, u milisekundama. */
    private static final long NETWORK_TOLERANCE_MS = 1500;

    private final ParticipationRepository participationRepository;
    private final QuizRepository quizRepository;
    private final UserRepository userRepository;
    private final ScoringService scoringService;
    private final TeamRepository teamRepository;
    private final TeamService teamService;

    // POKRETANJE

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
        if (participationRepository.existsTeamParticipationForMember(quizId, userId)) {
            throw conflict("Vaš tim je već rješavao ovaj kviz.");
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

    // DOHVAT TRENUTNOG PITANJA

    @Transactional
    public PlayQuestionResponse getCurrent(Long participationId, Long userId) {
        Participation participation = getOwnedParticipation(participationId, userId);
        requireInProgress(participation);
        requireQuizTimeNotExpired(participation);
        return serveCurrentQuestion(participation);
    }


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


    // PREDAJA ODGOVORA

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

        ScoringResult result = scoringService.score(
                question,
                request.selectedOptionId(),
                request.textAnswer(),
                timeTakenMs,
                expired || quizTimeExpired);

        SubmittedAnswer answer = new SubmittedAnswer();
        answer.setParticipation(participation);
        answer.setQuestion(question);
        answer.setSelectedOption(resolveOption(question, request.selectedOptionId()));
        answer.setTextAnswer(request.textAnswer());
        answer.setCorrect(result.correct());
        answer.setTimeTakenMs(timeTakenMs);
        answer.setPointsAwarded(result.pointsAwarded());
        answer.setAnsweredAt(LocalDateTime.now());

        participation.getAnswers().add(answer);
        participation.setTotalScore(participation.getTotalScore() + result.pointsAwarded());

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
                result.correct(), result.expired(), result.pointsAwarded(),
                participation.getTotalScore(), timeTakenMs, finished
        );
    }


    // ODUSTAJANJE I PREGLED

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


    private AnswerOption resolveOption(Question question, Long optionId) {
        if (optionId == null) return null;
        return question.getOptions().stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> badRequest("Odabrana opcija ne pripada ovom pitanju"));
    }

    @Transactional
    public PlayQuestionResponse startAsTeam(Long quizId, Long teamId, Long userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> notFound("Kviz nije pronađen"));

        if (quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw conflict("Kviz trenutno nije otvoren za rješavanje");
        }
        if (quiz.getQuestions().isEmpty()) {
            throw conflict("Kviz nema pitanja");
        }

        Team team = teamService.getTeamOrThrow(teamId);

        if (!teamService.isMember(team, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Niste član ovog tima");
        }

        // MEHANIZAM 1: jedan pokušaj po timu
        if (participationRepository.existsByQuizIdAndTeamId(quizId, teamId)) {
            throw conflict("Vaš tim je već rješavao ovaj kviz.");
        }
        // sudjelovanje je ili pojedinačno ili timsko, ne oboje
        if (participationRepository.existsByQuizIdAndUserId(quizId, userId)) {
            throw conflict("Ovaj kviz ste već rješavali pojedinačno.");
        }

        Participation participation = new Participation();
        participation.setQuiz(quiz);
        participation.setTeam(team);
        participation.setStatus(ParticipationStatus.IN_PROGRESS);
        participation.setCurrentQuestionIndex(0);
        participation.setCurrentQuestionStartedAt(null);
        participation.setTotalScore(0);
        participation.setStartedAt(LocalDateTime.now());

        try {
            participationRepository.saveAndFlush(participation);
        } catch (DataIntegrityViolationException e) {
            throw conflict("Vaš tim je već rješavao ovaj kviz.");
        }

        return serveCurrentQuestion(participation);
    }

    // POMOĆNE METODE

    private Participation getOwnedParticipation(Long participationId, Long userId) {
        Participation participation = participationRepository.findById(participationId)
                .orElseThrow(() -> notFound("Pokušaj nije pronađen"));

        boolean isOwner = participation.getUser() != null
                && participation.getUser().getId().equals(userId);

        boolean isTeamMember = participation.getTeam() != null
                && teamService.isMember(participation.getTeam(), userId);

        if (!isOwner && !isTeamMember) {
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