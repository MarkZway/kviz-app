import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

function RegisterPage() {
  const [form, setForm] = useState({
    username: "",
    email: "",
    password: "",
    role: "PARTICIPANT",
  });
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const { register } = useAuth();
  const navigate = useNavigate();

  function updateField(field, value) {
    setForm({ ...form, [field]: value });
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setLoading(true);

    try {
      await register(form);
      navigate("/quizzes");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="card card-narrow">
      <h2>Registracija</h2>

      <form onSubmit={handleSubmit}>
        <label>
          Korisničko ime
          <input
            type="text"
            value={form.username}
            onChange={(e) => updateField("username", e.target.value)}
            minLength={3}
            autoFocus
            required
          />
        </label>

        <label>
          Email
          <input
            type="email"
            value={form.email}
            onChange={(e) => updateField("email", e.target.value)}
            required
          />
        </label>

        <label>
          Lozinka
          <input
            type="password"
            value={form.password}
            onChange={(e) => updateField("password", e.target.value)}
            minLength={6}
            required
          />
        </label>

        <label>
          Uloga
          <select
            value={form.role}
            onChange={(e) => updateField("role", e.target.value)}
          >
            <option value="PARTICIPANT">Sudionik</option>
            <option value="ORGANIZER">Organizator</option>
          </select>
        </label>

        {error && <p className="error">{error}</p>}

        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? "Registracija u tijeku..." : "Registriraj se"}
        </button>
      </form>

      <p className="muted">
        Već imate račun? <Link to="/login">Prijavite se</Link>
      </p>
    </div>
  );
}

export default RegisterPage;