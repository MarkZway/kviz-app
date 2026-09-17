import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api";

function MyQuizzesPage() {
  const [quizzes, setQuizzes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [creating, setCreating] = useState(false);

  const navigate = useNavigate();

  async function loadQuizzes() {
    try {
      const data = await api.getMyQuizzes();
      setQuizzes(data);
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadQuizzes();
  }, []);

  async function handleCreate(event) {
    event.preventDefault();
    setCreating(true);
    try {
      const created = await api.createQuiz({
        title: newTitle,
        description: "",
        timeLimitMinutes: null,
      });
      navigate(`/quizzes/${created.id}/edit`);
    } catch (err) {
      setError(err.message);
      setCreating(false);
    }
  }

  async function handlePublish(quizId) {
    try {
      await api.publishQuiz(quizId);
      loadQuizzes();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleClose(quizId) {
    try {
      await api.closeQuiz(quizId);
      loadQuizzes();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleDelete(quizId, title) {
    if (!window.confirm(`Obrisati kviz "${title}"?`)) {
      return;
    }
    try {
      await api.deleteQuiz(quizId);
      loadQuizzes();
    } catch (err) {
      setError(err.message);
    }
  }

  if (loading) return <p className="muted">Ucitavanje...</p>;

  return (
    <div>
      <div className="page-header">
        <h2>Moji kvizovi</h2>
        <button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>
          {showForm ? "Odustani" : "Novi kviz"}
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      {showForm && (
        <div className="card">
          <form onSubmit={handleCreate}>
            <label>
              Naziv kviza
              <input
                type="text"
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                placeholder="npr. Pub kviz - opca kultura"
                autoFocus
                required
              />
            </label>
            <button type="submit" className="btn btn-primary" disabled={creating}>
              {creating ? "Kreiranje..." : "Kreiraj i uredi"}
            </button>
          </form>
        </div>
      )}

      {quizzes.length === 0 ? (
        <p className="muted">Jos nemate nijedan kviz.</p>
      ) : (
        quizzes.map((quiz) => (
          <div className="card" key={quiz.id}>
            <div className="card-head">
              <h3>{quiz.title}</h3>
              <span className={`status status-${quiz.status.toLowerCase()}`}>
                {quiz.status}
              </span>
            </div>

            <p className="meta">
              {quiz.questionCount} pitanja
              {quiz.timeLimitMinutes && ` \u00b7 ${quiz.timeLimitMinutes} min`}
            </p>

            <div className="button-row">
              {quiz.status === "DRAFT" && (
                <>
                  <Link className="btn btn-ghost" to={`/quizzes/${quiz.id}/edit`}>
                    Uredi
                  </Link>
                  <button
                    className="btn btn-primary"
                    onClick={() => handlePublish(quiz.id)}
                    disabled={quiz.questionCount === 0}
                    title={quiz.questionCount === 0 ? "Dodajte barem jedno pitanje" : ""}
                  >
                    Objavi
                  </button>
                  <button
                    className="btn btn-danger"
                    onClick={() => handleDelete(quiz.id, quiz.title)}
                  >
                    Obrisi
                  </button>
                </>
              )}

              {quiz.status === "PUBLISHED" && (
                <>
                  <Link className="btn btn-ghost" to={`/quizzes/${quiz.id}/stats`}>
                    Statistika
                  </Link>
                  <Link className="btn btn-ghost" to={`/quizzes/${quiz.id}/leaderboard`}>
                    Rang-lista
                  </Link>
                  <button className="btn btn-ghost" onClick={() => handleClose(quiz.id)}>
                    Zatvori
                  </button>
                </>
              )}

              {quiz.status === "CLOSED" && (
                <>
                  <Link className="btn btn-ghost" to={`/quizzes/${quiz.id}/stats`}>
                    Statistika
                  </Link>
                  <Link className="btn btn-ghost" to={`/quizzes/${quiz.id}/leaderboard`}>
                    Rang-lista
                  </Link>
                </>
              )}
            </div>
          </div>
        ))
      )}
    </div>
  );
}

export default MyQuizzesPage;