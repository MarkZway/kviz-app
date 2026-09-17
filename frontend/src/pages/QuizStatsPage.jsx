import { useState, useEffect } from "react";
import { useParams, Link } from "react-router-dom";
import { api } from "../api";

function QuizStatsPage() {
  const { quizId } = useParams();
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    api
      .getQuizStats(quizId)
      .then(setStats)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [quizId]);

  if (loading) return <p className="muted">Ucitavanje...</p>;
  if (error) return <p className="error">{error}</p>;

  return (
    <div>
      <div className="page-header">
        <h2>Statistika: {stats.quizTitle}</h2>
        <Link className="btn btn-ghost" to="/my-quizzes">
          Natrag
        </Link>
      </div>

      <div className="stat-row">
        <div className="stat-box">
          <span className="stat-value">{stats.participantCount}</span>
          <span className="stat-label">sudionika</span>
        </div>
        <div className="stat-box">
          <span className="stat-value">{stats.averageScore}</span>
          <span className="stat-label">prosjecno bodova</span>
        </div>
        <div className="stat-box">
          <span className="stat-value">{stats.maxPossibleScore}</span>
          <span className="stat-label">maksimalno mogucih</span>
        </div>
      </div>

      {stats.participantCount === 0 ? (
        <p className="muted">Kviz jos nitko nije rijesio.</p>
      ) : (
        <div className="card">
          <h3>Uspjesnost po pitanju</h3>
          <table className="table">
            <thead>
              <tr>
                <th>#</th>
                <th>Pitanje</th>
                <th>Tocnih</th>
                <th>Uspjesnost</th>
                <th>Prosj. vrijeme</th>
              </tr>
            </thead>
            <tbody>
              {stats.questions.map((q) => (
                <tr key={q.questionId}>
                  <td>{q.orderIndex}</td>
                  <td className="question-cell">{q.text}</td>
                  <td>
                    {q.correctAnswers} / {q.totalAnswers}
                  </td>
                  <td>
                    <div className="bar-wrap">
                      <div
                        className="bar"
                        style={{ width: `${q.successRate}%` }}
                      />
                      <span>{q.successRate}%</span>
                    </div>
                  </td>
                  <td>{q.averageTimeSeconds}s</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default QuizStatsPage;