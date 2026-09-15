package ba.sum.kviz.service;

import ba.sum.kviz.dto.*;
import ba.sum.kviz.model.*;
import ba.sum.kviz.repository.ParticipationRepository;
import ba.sum.kviz.repository.SubmittedAnswerRepository;
import ba.sum.kviz.repository.projection.QuestionStatsProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResultService {

    private final ParticipationRepository participationRepository;
    private final SubmittedAnswerRepository submittedAnswerRepository;
    private final QuizService quizService;

    // RANG-LISTA

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard(Long quizId) {
        Quiz quiz = quizService.getQuizOrThrow(quizId);
        int totalQuestions = quiz.getQuestions().size();

        List<Participation> participations = participationRepository.findLeaderboard(
                quizId, ParticipationStatus.FINISHED);

        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 0;
        int position = 0;
        Integer previousScore = null;

        for (Participation p : participations) {
            position++;
            // isti broj bodova daje isto mjesto
            if (previousScore == null || p.getTotalScore() != previousScore) {
                rank = position;
                previousScore = p.getTotalScore();
            }

            int correct = (int) p.getAnswers().stream()
                    .filter(SubmittedAnswer::isCorrect)
                    .count();

            entries.add(new LeaderboardEntry(
                    rank,
                    p.getId(),
                    participantName(p),
                    p.getTeam() != null,
                    p.getTotalScore(),
                    correct,
                    totalQuestions,
                    durationMs(p)
            ));
        }

        return entries;
    }

    // STATISTIKA KVIZA (samo organizator)

    @Transactional(readOnly = true)
    public QuizStatsResponse quizStats(Long quizId, Long userId) {
        Quiz quiz = quizService.getOwnedQuizOrThrow(quizId, userId);

        Map<Long, QuestionStatsProjection> statsByQuestion =
                submittedAnswerRepository.findQuestionStats(quizId).stream()
                        .collect(Collectors.toMap(
                                QuestionStatsProjection::getQuestionId,
                                Function.identity()));

        List<QuizStatsResponse.QuestionStats> questionStats = quiz.getQuestions().stream()
                .map(question -> toQuestionStats(question, statsByQuestion.get(question.getId())))
                .toList();

        long participantCount = participationRepository.countByQuizIdAndStatus(
                quizId, ParticipationStatus.FINISHED);

        Double average = participationRepository.averageScore(
                quizId, ParticipationStatus.FINISHED);

        int maxScore = quiz.getQuestions().stream()
                .mapToInt(Question::getBasePoints)
                .sum();

        return new QuizStatsResponse(
                quiz.getId(),
                quiz.getTitle(),
                participantCount,
                average == null ? 0.0 : round2(average),
                maxScore,
                questionStats
        );
    }

    private QuizStatsResponse.QuestionStats toQuestionStats(Question question,
                                                            QuestionStatsProjection stats) {
        long total = stats == null || stats.getTotalAnswers() == null
                ? 0 : stats.getTotalAnswers();
        long correct = stats == null || stats.getCorrectAnswers() == null
                ? 0 : stats.getCorrectAnswers();

        double successRate = total == 0 ? 0.0 : round2(100.0 * correct / total);
        double avgTimeSeconds = stats == null || stats.getAverageTimeMs() == null
                ? 0.0 : round2(stats.getAverageTimeMs() / 1000.0);
        double avgPoints = stats == null || stats.getAveragePoints() == null
                ? 0.0 : round2(stats.getAveragePoints());

        return new QuizStatsResponse.QuestionStats(
                question.getId(),
                question.getOrderIndex() + 1,
                question.getType().name(),
                question.getText(),
                total,
                correct,
                successRate,
                avgTimeSeconds,
                avgPoints
        );
    }

    // PREGLED VLASTITOG POKUŠAJA

    @Transactional(readOnly = true)
    public ParticipationDetailResponse myResult(Long participationId, Long userId) {
        Participation participation = participationRepository.findById(participationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pokušaj nije pronađen"));

        boolean isOwner = participation.getUser() != null
                && participation.getUser().getId().equals(userId);
        boolean isTeamMember = participation.getTeam() != null
                && participation.getTeam().getMembers().stream()
                .anyMatch(m -> m.getId().equals(userId));
        boolean isQuizCreator = participation.getQuiz().getCreator().getId().equals(userId);

        if (!isOwner && !isTeamMember && !isQuizCreator) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pokušaj nije pronađen");
        }

        // točni odgovori se otkrivaju tek nakon završetka
        if (participation.getStatus() == ParticipationStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Rezultati su dostupni tek nakon završetka kviza");
        }

        List<SubmittedAnswer> answers =
                submittedAnswerRepository.findDetailedByParticipation(participationId);

        List<ParticipationDetailResponse.AnswerReview> reviews = answers.stream()
                .map(this::toReview)
                .toList();

        Quiz quiz = participation.getQuiz();
        int maxScore = quiz.getQuestions().stream()
                .mapToInt(Question::getBasePoints)
                .sum();

        return new ParticipationDetailResponse(
                participation.getId(),
                quiz.getId(),
                quiz.getTitle(),
                participation.getStatus().name(),
                participation.getTotalScore(),
                maxScore,
                (int) answers.stream().filter(SubmittedAnswer::isCorrect).count(),
                quiz.getQuestions().size(),
                durationMs(participation),
                reviews
        );
    }

    private ParticipationDetailResponse.AnswerReview toReview(SubmittedAnswer answer) {
        Question question = answer.getQuestion();

        String given = answer.getSelectedOption() != null
                ? answer.getSelectedOption().getText()
                : (answer.getTextAnswer() == null ? "(bez odgovora)" : answer.getTextAnswer());

        return new ParticipationDetailResponse.AnswerReview(
                question.getOrderIndex() + 1,
                question.getText(),
                question.getType().name(),
                given,
                correctAnswerText(question),
                answer.isCorrect(),
                answer.getPointsAwarded(),
                question.getBasePoints(),
                answer.getTimeTakenMs()
        );
    }

    private String correctAnswerText(Question question) {
        if (question.getType() == QuestionType.OPEN) {
            return question.getAcceptableAnswers().stream()
                    .map(AcceptableAnswer::getText)
                    .collect(Collectors.joining(" / "));
        }
        return question.getOptions().stream()
                .filter(AnswerOption::isCorrect)
                .map(AnswerOption::getText)
                .findFirst()
                .orElse("-");
    }

    // MOJI POKUŠAJI

    @Transactional(readOnly = true)
    public List<ParticipationSummaryResponse> myParticipations(Long userId) {
        return participationRepository.findAllForUser(userId).stream()
                .map(ParticipationSummaryResponse::from)
                .toList();
    }

    // POMOĆNE METODE

    private String participantName(Participation p) {
        if (p.getTeam() != null) {
            return p.getTeam().getName();
        }
        return p.getUser() != null ? p.getUser().getUsername() : "Nepoznato";
    }

    private Long durationMs(Participation p) {
        if (p.getStartedAt() == null || p.getFinishedAt() == null) {
            return null;
        }
        return Duration.between(p.getStartedAt(), p.getFinishedAt()).toMillis();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}