import { useState, useEffect } from "react";
import { useParams, Link } from "react-router-dom";
import { api } from "../api";

function LeaderboardPage() {
  const { quizId } = useParams();
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    api
      .getLeaderboard(quizId)
      .then(setEntries)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [quizId]);

  if (loading) return <p className="muted">Ucitavanje...</p>;
  if (error) return <p className="error">{error}</p>;

  return (
    <div>
      <div className="page-header">
        <h2>Rang-lista</h2>
        <Link className="btn btn-ghost" to="/quizzes">
          Natrag
        </Link>
      </div>

      {entries.length === 0 ? (
        <p className="muted">Kviz jos nitko nije zavrsio.</p>
      ) : (
        <div className="card">
          <table className="table">
            <thead>
              <tr>
                <th>#</th>
                <th>Sudionik</th>
                <th>Bodovi</th>
                <th>Tocnih</th>
                <th>Vrijeme</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={entry.participationId}>
                  <td className={entry.rank <= 3 ? "rank-top" : ""}>{entry.rank}</td>
                  <td>
                    {entry.participantName}
                    {entry.isTeam && <span className="badge team-badge">tim</span>}
                  </td>
                  <td>
                    <strong>{entry.totalScore}</strong>
                  </td>
                  <td>
                    {entry.correctAnswers} / {entry.totalQuestions}
                  </td>
                  <td>
                    {entry.durationMs ? `${Math.round(entry.durationMs / 1000)}s` : "-"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default LeaderboardPage;