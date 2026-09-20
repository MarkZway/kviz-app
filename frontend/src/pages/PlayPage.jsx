import { useState, useEffect, useRef } from "react";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../api";

const FEEDBACK_MS = 1800;

function PlayPage() {
  const { quizId } = useParams();
  const [searchParams] = useSearchParams();
  const teamId = searchParams.get("teamId");
  const navigate = useNavigate();

  const [participationId, setParticipationId] = useState(null);
  const [question, setQuestion] = useState(null);
  const [deadline, setDeadline] = useState(null);
  const [remaining, setRemaining] = useState(0);

  const [selectedOptionId, setSelectedOptionId] = useState(null);
  const [textAnswer, setTextAnswer] = useState("");

  const [feedback, setFeedback] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Sprjecava dvostruko pokretanje (React StrictMode u razvoju
  // namjerno dvaput izvrsi efekte) i dvostruko slanje odgovora.
  const startedRef = useRef(false);
  const submittingRef = useRef(false);

  // --- pokretanje kviza ---

  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;

    async function begin() {
      try {
        const first = teamId
          ? await api.startQuizAsTeam(quizId, teamId)
          : await api.startQuiz(quizId);
        showQuestion(first);
      } catch (err) {
        // Mozda pokusaj vec postoji i samo ga treba nastaviti
        const resumed = await tryResume();
        if (!resumed) {
          setError(err.message);
          setLoading(false);
        }
      }
    }

    async function tryResume() {
      try {
        const mine = await api.getMyParticipations();
        const active = mine.find(
          (p) => String(p.quizId) === String(quizId) && p.status === "IN_PROGRESS"
        );
        if (!active) return false;

        const current = await api.getCurrentQuestion(active.id);
        showQuestion(current);
        return true;
      } catch {
        return false;
      }
    }

    begin();
  }, [quizId, teamId]);

  function showQuestion(data) {
    setParticipationId(data.participationId);
    setQuestion(data);
    setDeadline(Date.now() + data.remainingMs);
    setRemaining(data.remainingMs);
    setSelectedOptionId(null);
    setTextAnswer("");
    setFeedback(null);
    setLoading(false);
    submittingRef.current = false;
  }

  // --- odbrojavanje ---

  useEffect(() => {
    if (deadline === null || feedback) return;

    const id = setInterval(() => {
      setRemaining(Math.max(0, deadline - Date.now()));
    }, 100);

    return () => clearInterval(id);
  }, [deadline, feedback]);

  // --- automatsko slanje kad vrijeme istekne ---

  useEffect(() => {
    if (remaining === 0 && deadline !== null && !feedback && !submittingRef.current) {
      submitAnswer();
    }
  }, [remaining]);

  // --- slanje odgovora ---

  async function submitAnswer() {
    if (submittingRef.current || !question) return;
    submittingRef.current = true;

    const payload =
      question.type === "OPEN"
        ? { textAnswer: textAnswer.trim() || null }
        : { selectedOptionId };

    try {
      const result = await api.submitAnswer(participationId, question.questionId, payload);
      setFeedback(result);

      setTimeout(async () => {
        if (result.quizFinished) {
          navigate(`/participations/${participationId}/result`);
          return;
        }
        try {
          const next = await api.getCurrentQuestion(participationId);
          showQuestion(next);
        } catch (err) {
          setError(err.message);
        }
      }, FEEDBACK_MS);
    } catch (err) {
      setError(err.message);
      submittingRef.current = false;
    }
  }

  // --- prikaz ---

  if (loading) return <p className="muted">Pokretanje kviza...</p>;

  if (error) {
    return (
      <div className="card card-narrow">
        <p className="error">{error}</p>
        <button className="btn btn-ghost" onClick={() => navigate("/quizzes")}>
          Natrag na kvizove
        </button>
      </div>
    );
  }

  if (!question) return null;

  const seconds = Math.ceil(remaining / 1000);
  const ratio = question.timeLimitSeconds
    ? remaining / (question.timeLimitSeconds * 1000)
    : 0;

  const timerClass =
    ratio > 0.5 ? "timer-ok" : ratio > 0.2 ? "timer-warn" : "timer-danger";

  const canSubmit =
    question.type === "OPEN" ? textAnswer.trim().length > 0 : selectedOptionId !== null;

  return (
    <div className="play-wrap">
      <div className="play-head">
        <span className="badge">
          Pitanje {question.questionNumber} / {question.totalQuestions}
        </span>
        <span className={`timer ${timerClass}`}>{seconds}s</span>
      </div>

      <div className="timer-bar">
        <div
          className={`timer-fill ${timerClass}`}
          style={{ width: `${Math.max(0, ratio * 100)}%` }}
        />
      </div>

      <div className="card">
        <h2 className="question-text">{question.text}</h2>

        {question.type === "OPEN" ? (
          <input
            type="text"
            className="answer-input"
            value={textAnswer}
            onChange={(e) => setTextAnswer(e.target.value)}
            placeholder="Upisite odgovor"
            disabled={feedback !== null}
            autoFocus
          />
        ) : (
          <div className="answer-grid">
            {question.options.map((option) => (
              <button
                key={option.id}
                type="button"
                className={`answer-option ${
                  selectedOptionId === option.id ? "selected" : ""
                }`}
                onClick={() => setSelectedOptionId(option.id)}
                disabled={feedback !== null}
              >
                {option.text}
              </button>
            ))}
          </div>
        )}

        {feedback ? (
          <div className={`feedback ${feedback.correct ? "correct" : "wrong"}`}>
            {feedback.expired
              ? "Vrijeme je isteklo"
              : feedback.correct
              ? "Tocno!"
              : "Netocno"}
            <span className="points">+{feedback.pointsAwarded} bodova</span>
            <span className="muted">Ukupno: {feedback.totalScore}</span>
          </div>
        ) : (
          <button
            className="btn btn-primary btn-wide"
            onClick={submitAnswer}
            disabled={!canSubmit}
          >
            Potvrdi odgovor
          </button>
        )}
      </div>

      <p className="muted center">
        Povratak na prethodna pitanja nije moguc.
      </p>
    </div>
  );
}

export default PlayPage;