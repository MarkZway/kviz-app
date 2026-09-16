import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setLoading(true);

    try {
      await login({ username, password });
      navigate("/quizzes");
    } catch (err) {
      setError(
        err.status === 401 || err.status === 403
          ? "Pogrešno korisničko ime ili lozinka"
          : err.message
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="card card-narrow">
      <h2>Prijava</h2>

      <form onSubmit={handleSubmit}>
        <label>
          Korisničko ime
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoFocus
            required
          />
        </label>

        <label>
          Lozinka
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>

        {error && <p className="error">{error}</p>}

        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? "Prijava u tijeku..." : "Prijavi se"}
        </button>
      </form>

      <p className="muted">
        Nemate račun? <Link to="/register">Registrirajte se</Link>
      </p>
    </div>
  );
}

export default LoginPage;