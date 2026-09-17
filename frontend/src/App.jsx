import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import ProtectedRoute from "./components/ProtectedRoute";
import Navbar from "./components/Navbar";

import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import QuizListPage from "./pages/QuizListPage";
import MyQuizzesPage from "./pages/MyQuizzesPage";
import QuizEditorPage from "./pages/QuizEditorPage";
import QuizStatsPage from "./pages/QuizStatsPage";

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Navbar />
        <main className="container">
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />

            <Route
              path="/quizzes"
              element={
                <ProtectedRoute>
                  <QuizListPage />
                </ProtectedRoute>
              }
            />

                        <Route
              path="/my-quizzes"
              element={
                <ProtectedRoute organizerOnly>
                  <MyQuizzesPage />
                </ProtectedRoute>
              }
            />

            <Route
              path="/quizzes/:quizId/edit"
              element={
                <ProtectedRoute organizerOnly>
                  <QuizEditorPage />
                </ProtectedRoute>
              }
            />

            <Route
              path="/quizzes/:quizId/stats"
              element={
                <ProtectedRoute organizerOnly>
                  <QuizStatsPage />
                </ProtectedRoute>
              }
            />

            <Route path="/" element={<Navigate to="/quizzes" replace />} />
            <Route path="*" element={<p>Stranica nije pronađena.</p>} />
          </Routes>
        </main>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;