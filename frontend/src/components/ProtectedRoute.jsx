import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

/**
 * Propušta samo prijavljene korisnike.
 * Ako je zadan organizerOnly, traži i ulogu ORGANIZER.
 */
function ProtectedRoute({ children, organizerOnly = false }) {
  const { isLoggedIn, isOrganizer } = useAuth();

  if (!isLoggedIn) {
    return <Navigate to="/login" replace />;
  }
  if (organizerOnly && !isOrganizer) {
    return <Navigate to="/quizzes" replace />;
  }

  return children;
}

export default ProtectedRoute;