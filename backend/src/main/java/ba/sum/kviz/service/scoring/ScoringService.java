package ba.sum.kviz.service.scoring;

import ba.sum.kviz.model.AnswerOption;
import ba.sum.kviz.model.Question;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class ScoringService {

    /** Udio bodova koji se zadržava i kod najsporijeg točnog odgovora. */
    private static final double MINIMUM_FACTOR = 0.5;

    /**
     * Vrednuje odgovor i izračunava bodove.
     *
     * @param question    pitanje na koje se odgovara
     * @param selectedOptionId odabrana opcija (MC / T-F), može biti null
     * @param textAnswer  tekstualni odgovor (OPEN), može biti null
     * @param timeTakenMs vrijeme izmjereno na serveru
     * @param expired     je li isteklo vrijeme (utvrđuje pozivatelj)
     */
    public ScoringResult score(Question question,
                               Long selectedOptionId,
                               String textAnswer,
                               long timeTakenMs,
                               boolean expired) {

        if (expired) {
            return ScoringResult.ofExpired();
        }

        boolean correct = isCorrect(question, selectedOptionId, textAnswer);
        if (!correct) {
            return ScoringResult.ofWrong();
        }

        int points = calculatePoints(
                question.getBasePoints(),
                question.getTimeLimitSeconds(),
                timeTakenMs);

        return ScoringResult.ofCorrect(points);
    }

    // =========================================================
    // TOČNOST PO TIPU PITANJA
    // =========================================================

    boolean isCorrect(Question question, Long selectedOptionId, String textAnswer) {
        return switch (question.getType()) {
            case MULTIPLE_CHOICE, TRUE_FALSE -> isOptionCorrect(question, selectedOptionId);
            case OPEN -> isTextCorrect(question, textAnswer);
        };
    }

    private boolean isOptionCorrect(Question question, Long selectedOptionId) {
        if (selectedOptionId == null) {
            return false;
        }
        return question.getOptions().stream()
                .filter(AnswerOption::isCorrect)
                .anyMatch(option -> option.getId().equals(selectedOptionId));
    }

    private boolean isTextCorrect(Question question, String textAnswer) {
        if (textAnswer == null || textAnswer.isBlank()) {
            return false;
        }
        String given = normalize(textAnswer);
        return question.getAcceptableAnswers().stream()
                .anyMatch(acceptable -> normalize(acceptable.getText()).equals(given));
    }

    /**
     * Normalizira tekst prije usporedbe: uklanja rubne razmake, svodi na mala slova,
     * sažima višestruke razmake i uklanja dijakritičke znakove
     * (tako da "Osijek" i "osijek", odnosno "Đakovo" i "Dakovo", budu jednaki).
     */
    String normalize(String text) {
        String lowered = text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        String withoutDiacritics = Normalizer.normalize(lowered, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        // đ se ne rastavlja NFD normalizacijom pa se zamjenjuje posebno
        return withoutDiacritics.replace("đ", "d");
    }

    // =========================================================
    // BODOVANJE PO BRZINI
    // =========================================================

    int calculatePoints(int basePoints, int timeLimitSeconds, long timeTakenMs) {
        long limitMs = timeLimitSeconds * 1000L;

        if (limitMs <= 0) {
            return basePoints;
        }

        double ratio = (double) timeTakenMs / limitMs;
        ratio = Math.max(0.0, Math.min(1.0, ratio));

        double factor = 1.0 - (ratio * (1.0 - MINIMUM_FACTOR));

        return (int) Math.round(basePoints * factor);
    }
}