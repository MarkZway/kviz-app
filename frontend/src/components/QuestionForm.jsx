import { useState } from "react";

const EMPTY_OPTION = { text: "", correct: false };

function QuestionForm({ onSubmit, onCancel }) {
  const [type, setType] = useState("MULTIPLE_CHOICE");
  const [text, setText] = useState("");
  const [timeLimitSeconds, setTimeLimitSeconds] = useState(30);
  const [basePoints, setBasePoints] = useState(100);

  const [options, setOptions] = useState([
    { ...EMPTY_OPTION },
    { ...EMPTY_OPTION },
  ]);
  const [acceptableAnswers, setAcceptableAnswers] = useState([""]);
  const [correctAnswer, setCorrectAnswer] = useState(true);

  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  // --- rad s opcijama ---

  function updateOptionText(index, value) {
    const next = options.map((opt, i) =>
      i === index ? { ...opt, text: value } : opt
    );
    setOptions(next);
  }

  function markCorrect(index) {
    // samo jedna opcija smije biti tocna
    const next = options.map((opt, i) => ({ ...opt, correct: i === index }));
    setOptions(next);
  }

  function addOption() {
    setOptions([...options, { ...EMPTY_OPTION }]);
  }

  function removeOption(index) {
    if (options.length <= 2) return;
    setOptions(options.filter((_, i) => i !== index));
  }

  // --- rad s prihvatljivim odgovorima ---

  function updateAcceptable(index, value) {
    setAcceptableAnswers(acceptableAnswers.map((a, i) => (i === index ? value : a)));
  }

  function addAcceptable() {
    setAcceptableAnswers([...acceptableAnswers, ""]);
  }

  function removeAcceptable(index) {
    if (acceptableAnswers.length <= 1) return;
    setAcceptableAnswers(acceptableAnswers.filter((_, i) => i !== index));
  }

  // --- slanje ---

  function buildPayload() {
    const base = {
      type,
      text: text.trim(),
      timeLimitSeconds: Number(timeLimitSeconds),
      basePoints: Number(basePoints),
    };

    if (type === "MULTIPLE_CHOICE") {
      return {
        ...base,
        options: options.map((opt) => ({
          text: opt.text.trim(),
          correct: opt.correct,
        })),
      };
    }
    if (type === "TRUE_FALSE") {
      return { ...base, correctAnswer };
    }
    return {
      ...base,
      acceptableAnswers: acceptableAnswers
        .map((a) => a.trim())
        .filter((a) => a.length > 0),
    };
  }

  function validateLocally() {
    if (type === "MULTIPLE_CHOICE") {
      if (options.some((o) => !o.text.trim())) {
        return "Sve opcije moraju imati tekst.";
      }
      if (!options.some((o) => o.correct)) {
        return "Oznacite koja je opcija tocna.";
      }
    }
    if (type === "OPEN") {
      if (!acceptableAnswers.some((a) => a.trim())) {
        return "Unesite barem jedan prihvatljiv odgovor.";
      }
    }
    return null;
  }

  async function handleSubmit(event) {
    event.preventDefault();

    const localError = validateLocally();
    if (localError) {
      setError(localError);
      return;
    }

    setError(null);
    setSaving(true);
    try {
      await onSubmit(buildPayload());
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="card">
      <h3>Novo pitanje</h3>

      <form onSubmit={handleSubmit}>
        <label>
          Tip pitanja
          <select value={type} onChange={(e) => setType(e.target.value)}>
            <option value="MULTIPLE_CHOICE">Visestruki izbor</option>
            <option value="TRUE_FALSE">Tocno / netocno</option>
            <option value="OPEN">Otvoreno pitanje</option>
          </select>
        </label>

        <label>
          Tekst pitanja
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            rows={2}
            required
          />
        </label>

        <div className="form-row">
          <label>
            Vrijeme (s)
            <input
              type="number"
              value={timeLimitSeconds}
              onChange={(e) => setTimeLimitSeconds(e.target.value)}
              min={5}
              max={600}
              required
            />
          </label>
          <label>
            Bodovi
            <input
              type="number"
              value={basePoints}
              onChange={(e) => setBasePoints(e.target.value)}
              min={1}
              max={1000}
              required
            />
          </label>
        </div>

        {/* --- dio koji ovisi o tipu --- */}

        {type === "MULTIPLE_CHOICE" && (
          <div className="subsection">
            <p className="muted">Oznacite tocnu opciju:</p>
            {options.map((opt, index) => (
              <div className="option-row" key={index}>
                <input
                  type="radio"
                  name="correctOption"
                  checked={opt.correct}
                  onChange={() => markCorrect(index)}
                />
                <input
                  type="text"
                  value={opt.text}
                  onChange={(e) => updateOptionText(index, e.target.value)}
                  placeholder={`Opcija ${index + 1}`}
                  required
                />
                <button
                  type="button"
                  className="btn btn-ghost btn-small"
                  onClick={() => removeOption(index)}
                  disabled={options.length <= 2}
                >
                  &times;
                </button>
              </div>
            ))}
            <button type="button" className="btn btn-ghost btn-small" onClick={addOption}>
              + Dodaj opciju
            </button>
          </div>
        )}

        {type === "TRUE_FALSE" && (
          <div className="subsection">
            <p className="muted">Tocan odgovor:</p>
            <div className="option-row">
              <label className="inline-label">
                <input
                  type="radio"
                  checked={correctAnswer === true}
                  onChange={() => setCorrectAnswer(true)}
                />
                Tocno
              </label>
              <label className="inline-label">
                <input
                  type="radio"
                  checked={correctAnswer === false}
                  onChange={() => setCorrectAnswer(false)}
                />
                Netocno
              </label>
            </div>
          </div>
        )}

        {type === "OPEN" && (
          <div className="subsection">
            <p className="muted">
              Prihvatljivi odgovori (velika slova, razmaci i kvacice se zanemaruju):
            </p>
            {acceptableAnswers.map((answer, index) => (
              <div className="option-row" key={index}>
                <input
                  type="text"
                  value={answer}
                  onChange={(e) => updateAcceptable(index, e.target.value)}
                  placeholder={`Odgovor ${index + 1}`}
                />
                <button
                  type="button"
                  className="btn btn-ghost btn-small"
                  onClick={() => removeAcceptable(index)}
                  disabled={acceptableAnswers.length <= 1}
                >
                  &times;
                </button>
              </div>
            ))}
            <button type="button" className="btn btn-ghost btn-small" onClick={addAcceptable}>
              + Dodaj varijantu
            </button>
          </div>
        )}

        {error && <p className="error">{error}</p>}

        <div className="button-row">
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? "Spremanje..." : "Dodaj pitanje"}
          </button>
          <button type="button" className="btn btn-ghost" onClick={onCancel}>
            Odustani
          </button>
        </div>
      </form>
    </div>
  );
}

export default QuestionForm;