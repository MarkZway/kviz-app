import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api";

function QuizListPage() {
  const [quizzes, setQuizzes] = useState([]);
  const [participations, setParticipations] = useState([]);
  const [teams, setTeams] = useState([]);
  const [choice, setChoice] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const navigate = useNavigate();

  useEffect(() => {
    async function load() {
      try {
        const [quizData, partData, teamData] = await Promise.all([
          api.getPublishedQuizzes(),
          api.getMyParticipations(),
          api.getMyTeams(),
        ]);
        setQuizzes(quizData);
        setParticipations(partData);
        setTeams(teamData);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  function participationFor(quizId) {
    return participations.find((p) => String(p.quizId) === String(quizId));
  }

  function handlePlay(quizId) {
    const selected = choice[quizId];
    if (selected && selected !== "solo") {
      navigate(`/play/${quizId}?teamId=${selected}`);
    } else {
      navigate(`/play/${quizId}`);
    }
  }

  if (loading) return <p className="muted">Ucitavanje...</p>;
  if (error) return <p className="error">{error}</p>;

  return (
    <div>
      <h2>Dostupni kvizovi</h2>

      {quizzes.length === 0 ? (
        <p className="muted">Trenutno nema objavljenih kvizova.</p>
      ) : (
        <div className="quiz-grid">
          {quizzes.map((quiz) => {
            const played = participationFor(quiz.id);

            return (
              <div className="card" key={quiz.id}>
                <h3>{quiz.title}</h3>
                {quiz.description && <p className="muted">{quiz.description}</p>}
                <p className="meta">
                  {quiz.questionCount} pitanja {"\u00b7"} autor: {quiz.creatorUsername}
                  {quiz.timeLimitMinutes && ` \u00b7 ${quiz.timeLimitMinutes} min`}
                </p>

                {played ? (
                  played.status === "IN_PROGRESS" ? (
                    <button
                      className="btn btn-primary"
                      onClick={() => navigate(`/play/${quiz.id}`)}
                    >
                      Nastavi
                    </button>
                  ) : (
                    <div className="button-row">
                      <span className="badge">
                        Rijeseno {"\u00b7"} {played.totalScore} bodova
                      </span>
                      <button
                        className="btn btn-ghost btn-small"
                        onClick={() => navigate(`/participations/${played.id}/result`)}
                      >
                        Rezultat
                      </button>
                    </div>
                  )
                ) : (
                  <div className="button-row">
                    {teams.length > 0 && (
                      <select
                        value={choice[quiz.id] || "solo"}
                        onChange={(e) =>
                          setChoice({ ...choice, [quiz.id]: e.target.value })
                        }
                      >
                        <option value="solo">Pojedinacno</option>
                        {teams.map((team) => (
                          <option key={team.id} value={team.id}>
                            Tim: {team.name}
                          </option>
                        ))}
                      </select>
                    )}
                    <button
                      className="btn btn-primary"
                      onClick={() => handlePlay(quiz.id)}
                    >
                      Igraj
                    </button>
                  </div>
                )}

                <button
                  className="btn btn-ghost btn-small"
                  onClick={() => navigate(`/quizzes/${quiz.id}/leaderboard`)}
                >
                  Rang-lista
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default QuizListPage;