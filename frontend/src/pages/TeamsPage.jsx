import { useState, useEffect } from "react";
import { api } from "../api";
import { useAuth } from "../auth/AuthContext";

function TeamsPage() {
  const { user } = useAuth();
  const [teams, setTeams] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [newName, setNewName] = useState("");
  const [joinCode, setJoinCode] = useState("");
  const [busy, setBusy] = useState(false);

  async function loadTeams() {
    try {
      setTeams(await api.getMyTeams());
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadTeams();
  }, []);

  async function handleCreate(event) {
    event.preventDefault();
    setBusy(true);
    try {
      await api.createTeam({ name: newName });
      setNewName("");
      loadTeams();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function handleJoin(event) {
    event.preventDefault();
    setBusy(true);
    try {
      await api.joinTeam({ joinCode: joinCode.toUpperCase() });
      setJoinCode("");
      loadTeams();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h2>Timovi</h2>

      {error && <p className="error">{error}</p>}

      <div className="two-columns">
        <div className="card">
          <h3>Kreiraj tim</h3>
          <form onSubmit={handleCreate}>
            <label>
              Naziv tima
              <input
                type="text"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                minLength={2}
                required
              />
            </label>
            <button type="submit" className="btn btn-primary" disabled={busy}>
              Kreiraj
            </button>
          </form>
        </div>

        <div className="card">
          <h3>Pridruzi se timu</h3>
          <form onSubmit={handleJoin}>
            <label>
              Kod za pridruzivanje
              <input
                type="text"
                value={joinCode}
                onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                className="code-input"
                maxLength={8}
                required
              />
            </label>
            <button type="submit" className="btn btn-primary" disabled={busy}>
              Pridruzi se
            </button>
          </form>
        </div>
      </div>

      <h3>Moji timovi</h3>

      {loading ? (
        <p className="muted">Ucitavanje...</p>
      ) : teams.length === 0 ? (
        <p className="muted">Niste clan nijednog tima.</p>
      ) : (
        teams.map((team) => (
          <div className="card" key={team.id}>
            <div className="card-head">
              <h4>{team.name}</h4>
              {team.joinCode && <code className="join-code">{team.joinCode}</code>}
            </div>

            <p className="meta">
              Kapetan: {team.captainUsername}
              {team.captainId === user.userId && " (vi)"}
            </p>

            <ul className="member-list">
              {team.members.map((member) => (
                <li key={member.id}>
                  {member.username}
                  {member.captain && <span className="badge">kapetan</span>}
                </li>
              ))}
            </ul>

            <p className="muted">
              Podijelite kod suigracima da vam se pridruze.
            </p>
          </div>
        ))
      )}
    </div>
  );
}

export default TeamsPage;