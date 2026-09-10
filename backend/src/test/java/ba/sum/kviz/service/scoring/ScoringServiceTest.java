package ba.sum.kviz.service.scoring;

import ba.sum.kviz.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ScoringServiceTest {

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new ScoringService();
    }

    // ---------- pomoćne metode za izgradnju pitanja ----------

    private Question multipleChoice() {
        Question q = new Question();
        q.setType(QuestionType.MULTIPLE_CHOICE);
        q.setBasePoints(100);
        q.setTimeLimitSeconds(30);
        q.getOptions().add(option(1L, "Sydney", false));
        q.getOptions().add(option(2L, "Canberra", true));
        q.getOptions().add(option(3L, "Perth", false));
        return q;
    }

    private Question openQuestion(String... acceptable) {
        Question q = new Question();
        q.setType(QuestionType.OPEN);
        q.setBasePoints(100);
        q.setTimeLimitSeconds(30);
        for (String text : acceptable) {
            AcceptableAnswer a = new AcceptableAnswer();
            a.setText(text);
            q.getAcceptableAnswers().add(a);
        }
        return q;
    }

    private AnswerOption option(Long id, String text, boolean correct) {
        AnswerOption o = new AnswerOption();
        o.setId(id);
        o.setText(text);
        o.setCorrect(correct);
        return o;
    }

    // ---------- bodovanje po brzini ----------

    @Nested
    @DisplayName("Izračun bodova ovisno o brzini")
    class PointCalculation {

        @Test
        @DisplayName("Trenutni odgovor nosi pune bodove")
        void instantAnswerGivesFullPoints() {
            assertEquals(100, scoringService.calculatePoints(100, 30, 0));
        }

        @Test
        @DisplayName("Odgovor na pola vremena nosi 75% bodova")
        void halfTimeGivesThreeQuarters() {
            assertEquals(75, scoringService.calculatePoints(100, 30, 15_000));
        }

        @Test
        @DisplayName("Odgovor u zadnji čas nosi polovicu bodova")
        void lastMomentGivesHalf() {
            assertEquals(50, scoringService.calculatePoints(100, 30, 30_000));
        }

        @Test
        @DisplayName("Prekoračenje unutar tolerancije ne spušta bodove ispod polovice")
        void slightOvershootStaysAtMinimum() {
            assertEquals(50, scoringService.calculatePoints(100, 30, 31_000));
        }

        @ParameterizedTest(name = "P={0}, T={1}s, t={2}ms -> {3} bodova")
        @CsvSource({
                "100, 30,      0, 100",
                "100, 30,  7_500,  88",
                "100, 30, 15_000,  75",
                "100, 30, 30_000,  50",
                "200, 60,      0, 200",
                "200, 60, 30_000, 150",
                " 50, 10,  5_000,  38"
        })
        void calculatesExpectedPoints(int base, int limit, long taken, int expected) {
            assertEquals(expected, scoringService.calculatePoints(base, limit, taken));
        }

        @Test
        @DisplayName("Brži odgovor uvijek nosi barem onoliko bodova koliko sporiji")
        void fasterIsNeverWorse() {
            int previous = Integer.MAX_VALUE;
            for (long t = 0; t <= 30_000; t += 1_000) {
                int points = scoringService.calculatePoints(100, 30, t);
                assertTrue(points <= previous,
                        "Bodovi bi trebali padati s vremenom, a na t=" + t + " su porasli");
                previous = points;
            }
        }
    }

    // ---------- pitanja s ponuđenim odgovorima ----------

    @Nested
    @DisplayName("Vrednovanje pitanja s izborom")
    class ChoiceQuestions {

        @Test
        void correctOptionIsAccepted() {
            ScoringResult result = scoringService.score(
                    multipleChoice(), 2L, null, 0, false);

            assertTrue(result.correct());
            assertEquals(100, result.pointsAwarded());
        }

        @Test
        void wrongOptionGivesZero() {
            ScoringResult result = scoringService.score(
                    multipleChoice(), 1L, null, 0, false);

            assertFalse(result.correct());
            assertEquals(0, result.pointsAwarded());
        }

        @Test
        @DisplayName("Izostanak odgovora se tretira kao netočan")
        void nullOptionGivesZero() {
            ScoringResult result = scoringService.score(
                    multipleChoice(), null, null, 0, false);

            assertFalse(result.correct());
            assertEquals(0, result.pointsAwarded());
        }

        @Test
        @DisplayName("Istek vremena poništava i točan odgovor")
        void expiredCorrectAnswerGivesZero() {
            ScoringResult result = scoringService.score(
                    multipleChoice(), 2L, null, 1_000, true);

            assertFalse(result.correct());
            assertTrue(result.expired());
            assertEquals(0, result.pointsAwarded());
        }
    }

    // ---------- otvorena pitanja ----------

    @Nested
    @DisplayName("Vrednovanje otvorenih pitanja")
    class OpenQuestions {

        @Test
        void exactMatchIsAccepted() {
            assertTrue(scoringService.score(
                    openQuestion("Mostar"), null, "Mostar", 0, false).correct());
        }

        @Test
        @DisplayName("Velika i mala slova se zanemaruju")
        void caseIsIgnored() {
            assertTrue(scoringService.score(
                    openQuestion("Mostar"), null, "MOSTAR", 0, false).correct());
        }

        @Test
        @DisplayName("Rubni i višestruki razmaci se zanemaruju")
        void whitespaceIsNormalized() {
            assertTrue(scoringService.score(
                    openQuestion("Stari most"), null, "  Stari   most  ", 0, false).correct());
        }

        @Test
        @DisplayName("Dijakritički znakovi se zanemaruju")
        void diacriticsAreIgnored() {
            assertTrue(scoringService.score(
                    openQuestion("Šibenik"), null, "sibenik", 0, false).correct());
            assertTrue(scoringService.score(
                    openQuestion("Đakovo"), null, "dakovo", 0, false).correct());
        }

        @Test
        @DisplayName("Prihvaća se bilo koji od navedenih odgovora")
        void anyAcceptableAnswerMatches() {
            Question q = openQuestion("1566", "tisuću petsto šezdeset šeste");

            assertTrue(scoringService.score(q, null, "1566", 0, false).correct());
            assertTrue(scoringService.score(q, null, "Tisuću petsto šezdeset šeste", 0, false).correct());
            assertFalse(scoringService.score(q, null, "1667", 0, false).correct());
        }

        @Test
        void blankAnswerIsWrong() {
            assertFalse(scoringService.score(
                    openQuestion("Mostar"), null, "   ", 0, false).correct());
            assertFalse(scoringService.score(
                    openQuestion("Mostar"), null, null, 0, false).correct());
        }
    }
}