import { useState, useEffect } from "react";
import { useParams, Link } from "react-router-dom";
import { api } from "../api";

function ResultPage() {
  const { participationId } = useParams();
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    api
      .getResult(participationId)
      .then(setResult)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [participationId]);

  if (loading) return <p className="muted">Ucitavanje...</p>;
  if (error) return <p className="error">{error}</p>;

  const percent = result.maxPossibleScore
    ? Math.round((result.totalScore / result.maxPossibleScore) * 100)
    : 0;

  return (
    <div>
      <div className="page-header">
        <h2>{result.quizTitle}</h2>
        <Link className="btn btn-ghost" to={`/quizzes/${result.quizId}/leaderboard`}>
          Rang-lista
        </Link>
      </div>

      <div className="score-hero">
        <div className="score-big">{result.totalScore}</div>
        <div className="muted">
          od {result.maxPossibleScore} mogucih ({percent}%)
        </div>
        <div className="muted">
          Tocnih odgovora: {result.correctAnswers} / {result.totalQuestions}
          {result.durationMs &&
            ` \u00b7 ${Math.round(result.durationMs / 1000)}s ukupno`}
        </div>
      </div>

      <h3>Pregled odgovora</h3>

      {result.answers.map((answer) => (
        <div
          className={`card answer-review ${answer.correct ? "correct" : "wrong"}`}
          key={answer.questionNumber}
        >
          <div className="card-head">
            <h4>
              {answer.questionNumber}. {answer.questionText}
            </h4>
            <span className={answer.correct ? "points-ok" : "points-zero"}>
              {answer.pointsAwarded} / {answer.basePoints}
            </span>
          </div>

          <p className="meta">
            Vas odgovor: <strong>{answer.givenAnswer}</strong>
          </p>

          {!answer.correct && (
            <p className="meta">
              Tocan odgovor: <strong>{answer.correctAnswer}</strong>
            </p>
          )}

          <p className="meta">
            Vrijeme: {(answer.timeTakenMs / 1000).toFixed(1)}s
          </p>
        </div>
      ))}
    </div>
  );
}

export default ResultPage;