import { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";

function MyResultsPage() {
  const [participations, setParticipations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    api
      .getMyParticipations()
      .then(setParticipations)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="muted">Ucitavanje...</p>;
  if (error) return <p className="error">{error}</p>;

  return (
    <div>
      <h2>Moji rezultati</h2>

      {participations.length === 0 ? (
        <p className="muted">Jos niste rijesili nijedan kviz.</p>
      ) : (
        participations.map((p) => (
          <div className="card" key={p.id}>
            <div className="card-head">
              <h3>{p.quizTitle}</h3>
              <span className={`status status-${p.status.toLowerCase()}`}>
                {p.status === "IN_PROGRESS" ? "U TIJEKU" : p.status}
              </span>
            </div>

            <p className="meta">
              {p.totalScore} bodova {"\u00b7"} odgovoreno {p.answeredCount} /{" "}
              {p.totalQuestions}
            </p>

            <div className="button-row">
              {p.status === "IN_PROGRESS" ? (
                <Link className="btn btn-primary" to={`/play/${p.quizId}`}>
                  Nastavi
                </Link>
              ) : (
                <Link className="btn btn-ghost" to={`/participations/${p.id}/result`}>
                  Detaljan rezultat
                </Link>
              )}
              <Link className="btn btn-ghost" to={`/quizzes/${p.quizId}/leaderboard`}>
                Rang-lista
              </Link>
            </div>
          </div>
        ))
      )}
    </div>
  );
}

export default MyResultsPage;