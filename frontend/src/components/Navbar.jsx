import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

function Navbar() {
  const { user, isLoggedIn, isOrganizer, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/login");
  }

  return (
    <nav className="navbar">
      <Link to="/" className="navbar-brand">
        Kviz
      </Link>

      {isLoggedIn && (
        <div className="navbar-links">
          <Link to="/quizzes">Kvizovi</Link>
          {isOrganizer && <Link to="/my-quizzes">Moji kvizovi</Link>}
          <Link to="/teams">Timovi</Link>
          <Link to="/my-results">Moji rezultati</Link>
        </div>
      )}

      <div className="navbar-user">
        {isLoggedIn ? (
          <>
            <span className="badge">{user.username}</span>
            <button className="btn btn-ghost" onClick={handleLogout}>
              Odjava
            </button>
          </>
        ) : (
          <Link to="/login">Prijava</Link>
        )}
      </div>
    </nav>
  );
}

export default Navbar;