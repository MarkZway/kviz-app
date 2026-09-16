const BASE_URL = "http://localhost:8080/api";

/** Greška koja nosi HTTP status, da je ekrani mogu razlikovati. */
export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function getToken() {
  return localStorage.getItem("token");
}

/**
 * Jedno mjesto kroz koje prolazi svaki zahtjev prema backendu.
 * Dodaje token, pretvara odgovor u objekt i pretvara greške u ApiError.
 */
async function request(method, path, body) {
  const headers = {};

  const token = getToken();
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  let response;
  try {
    response = await fetch(BASE_URL + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, "Server nije dostupan. Provjerite je li backend pokrenut.");
  }

  // 204 No Content nema tijelo
  if (response.status === 204) {
    return null;
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new ApiError(response.status, extractMessage(data, response.status));
  }

  return data;
}

/** Backend vraća poruke u nekoliko oblika, pa ih ovdje svodimo na jedan. */
function extractMessage(data, status) {
  if (!data) {
    return `Greška ${status}`;
  }
  // validacijske greške iz GlobalExceptionHandler
  if (data.errors && typeof data.errors === "object") {
    const fields = Object.entries(data.errors)
      .map(([field, msg]) => `${field}: ${msg}`)
      .join(", ");
    if (fields) return fields;
  }
  return data.message || data.detail || data.error || `Greška ${status}`;
}

const get = (path) => request("GET", path);
const post = (path, body) => request("POST", path, body);
const put = (path, body) => request("PUT", path, body);
const patch = (path, body) => request("PATCH", path, body);
const del = (path) => request("DELETE", path);

//  Funkcije koje ekrani zovu

export const api = {
  // --- autentifikacija ---
  register: (data) => post("/auth/register", data),
  login: (data) => post("/auth/login", data),

  // --- kvizovi ---
  getPublishedQuizzes: () => get("/quizzes"),
  getMyQuizzes: () => get("/quizzes/mine"),
  getQuiz: (id) => get(`/quizzes/${id}`),
  createQuiz: (data) => post("/quizzes", data),
  updateQuiz: (id, data) => put(`/quizzes/${id}`, data),
  publishQuiz: (id) => patch(`/quizzes/${id}/publish`),
  closeQuiz: (id) => patch(`/quizzes/${id}/close`),
  deleteQuiz: (id) => del(`/quizzes/${id}`),

  // --- pitanja ---
  getQuestions: (quizId) => get(`/quizzes/${quizId}/questions`),
  addQuestion: (quizId, data) => post(`/quizzes/${quizId}/questions`, data),
  deleteQuestion: (quizId, questionId) =>
    del(`/quizzes/${quizId}/questions/${questionId}`),

  // --- igranje ---
  startQuiz: (quizId) => post(`/quizzes/${quizId}/play`),
  startQuizAsTeam: (quizId, teamId) => post(`/quizzes/${quizId}/play-as-team/${teamId}`),
  getCurrentQuestion: (participationId) =>
    get(`/participations/${participationId}/current-question`),
  submitAnswer: (participationId, questionId, data) =>
    post(`/participations/${participationId}/questions/${questionId}/answer`, data),

  // --- rezultati ---
  getResult: (participationId) => get(`/participations/${participationId}/result`),
  getMyParticipations: () => get("/participations/mine"),
  getLeaderboard: (quizId) => get(`/quizzes/${quizId}/leaderboard`),
  getQuizStats: (quizId) => get(`/quizzes/${quizId}/stats`),

  // --- timovi ---
  getMyTeams: () => get("/teams/mine"),
  createTeam: (data) => post("/teams", data),
  joinTeam: (data) => post("/teams/join", data),
};