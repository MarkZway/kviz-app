import { useState, useEffect } from "react";
import { api } from "../api";

function QuizListPage() {
  const [quizzes, setQuizzes] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api
      .getPublishedQuizzes()
      .then(setQuizzes)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="muted">Učitavanje...</p>;
  if (error) return <p className="error">{error}</p>;

  return (
    <div>
      <h2>Dostupni kvizovi</h2>

      {quizzes.length === 0 ? (
        <p className="muted">Trenutno nema objavljenih kvizova.</p>
      ) : (
        <div className="quiz-grid">
          {quizzes.map((quiz) => (
            <div className="card" key={quiz.id}>
              <h3>{quiz.title}</h3>
              <p className="muted">{quiz.description}</p>
              <p className="meta">
                {quiz.questionCount} pitanja · autor: {quiz.creatorUsername}
              </p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default QuizListPage;