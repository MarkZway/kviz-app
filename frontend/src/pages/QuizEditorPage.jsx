import { useState, useEffect } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { api } from "../api";
import QuestionForm from "../components/QuestionForm";

const TYPE_LABELS = {
  MULTIPLE_CHOICE: "Visestruki izbor",
  TRUE_FALSE: "Tocno / netocno",
  OPEN: "Otvoreno",
};

function QuizEditorPage() {
  const { quizId } = useParams();
  const navigate = useNavigate();

  const [quiz, setQuiz] = useState(null);
  const [questions, setQuestions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showQuestionForm, setShowQuestionForm] = useState(false);

  const [details, setDetails] = useState({
    title: "",
    description: "",
    timeLimitMinutes: "",
  });
  const [savingDetails, setSavingDetails] = useState(false);
  const [savedMessage, setSavedMessage] = useState(null);

  async function loadAll() {
    try {
      const [quizData, questionData] = await Promise.all([
        api.getQuiz(quizId),
        api.getQuestions(quizId),
      ]);
      setQuiz(quizData);
      setQuestions(questionData);
      setDetails({
        title: quizData.title,
        description: quizData.description || "",
        timeLimitMinutes: quizData.timeLimitMinutes ?? "",
      });
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadAll();
  }, [quizId]);

  async function handleSaveDetails(event) {
    event.preventDefault();
    setSavingDetails(true);
    setSavedMessage(null);
    try {
      await api.updateQuiz(quizId, {
        title: details.title,
        description: details.description,
        timeLimitMinutes: details.timeLimitMinutes === ""
          ? null
          : Number(details.timeLimitMinutes),
      });
      setSavedMessage("Spremljeno.");
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setSavingDetails(false);
    }
  }

  async function handleAddQuestion(payload) {
    await api.addQuestion(quizId, payload);
    setShowQuestionForm(false);
    loadAll();
  }

  async function handleDeleteQuestion(questionId) {
    if (!window.confirm("Obrisati ovo pitanje?")) return;
    try {
      await api.deleteQuestion(quizId, questionId);
      loadAll();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handlePublish() {
    try {
      await api.publishQuiz(quizId);
      navigate("/my-quizzes");
    } catch (err) {
      setError(err.message);
    }
  }

  if (loading) return <p className="muted">Ucitavanje...</p>;
  if (!quiz) return <p className="error">{error || "Kviz nije pronaden."}</p>;

  const isDraft = quiz.status === "DRAFT";

  return (
    <div>
      <div className="page-header">
        <h2>{quiz.title}</h2>
        <Link className="btn btn-ghost" to="/my-quizzes">
          Natrag
        </Link>
      </div>

      {error && <p className="error">{error}</p>}

      {!isDraft && (
        <p className="notice">
          Kviz je objavljen i vise se ne moze mijenjati.
        </p>
      )}

      {isDraft && (
        <div className="card">
          <h3>Podaci o kvizu</h3>
          <form onSubmit={handleSaveDetails}>
            <label>
              Naziv
              <input
                type="text"
                value={details.title}
                onChange={(e) => setDetails({ ...details, title: e.target.value })}
                required
              />
            </label>

            <label>
              Opis
              <textarea
                value={details.description}
                onChange={(e) => setDetails({ ...details, description: e.target.value })}
                rows={2}
              />
            </label>

            <label>
              Ukupno vrijeme u minutama (prazno = bez ogranicenja)
              <input
                type="number"
                value={details.timeLimitMinutes}
                onChange={(e) =>
                  setDetails({ ...details, timeLimitMinutes: e.target.value })
                }
                min={1}
              />
            </label>

            {savedMessage && <p className="success">{savedMessage}</p>}

            <button type="submit" className="btn btn-primary" disabled={savingDetails}>
              {savingDetails ? "Spremanje..." : "Spremi"}
            </button>
          </form>
        </div>
      )}

      <div className="page-header">
        <h3>Pitanja ({questions.length})</h3>
        {isDraft && !showQuestionForm && (
          <button className="btn btn-primary" onClick={() => setShowQuestionForm(true)}>
            Dodaj pitanje
          </button>
        )}
      </div>

      {showQuestionForm && (
        <QuestionForm
          onSubmit={handleAddQuestion}
          onCancel={() => setShowQuestionForm(false)}
        />
      )}

      {questions.length === 0 ? (
        <p className="muted">Kviz jos nema pitanja.</p>
      ) : (
        questions.map((question) => (
          <div className="card" key={question.id}>
            <div className="card-head">
              <h4>
                {question.orderIndex + 1}. {question.text}
              </h4>
              {isDraft && (
                <button
                  className="btn btn-ghost btn-small"
                  onClick={() => handleDeleteQuestion(question.id)}
                >
                  Obrisi
                </button>
              )}
            </div>

            <p className="meta">
              {TYPE_LABELS[question.type]} {"\u00b7"} {question.timeLimitSeconds}s{" "}
              {"\u00b7"} {question.basePoints} bodova
            </p>

            {question.options.length > 0 && (
              <ul className="option-list">
                {question.options.map((opt) => (
                  <li key={opt.id} className={opt.correct ? "correct" : ""}>
                    {opt.text}
                    {opt.correct && " \u2713"}
                  </li>
                ))}
              </ul>
            )}

            {question.acceptableAnswers.length > 0 && (
              <p className="meta">
                Prihvaca se: {question.acceptableAnswers.join(" / ")}
              </p>
            )}
          </div>
        ))
      )}

      {isDraft && questions.length > 0 && (
        <div className="card">
          <p className="muted">
            Nakon objave kviz se vise ne moze mijenjati.
          </p>
          <button className="btn btn-primary" onClick={handlePublish}>
            Objavi kviz
          </button>
        </div>
      )}
    </div>
  );
}

export default QuizEditorPage;