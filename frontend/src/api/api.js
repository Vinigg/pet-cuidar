import axios from "axios";

const API_URL = process.env.REACT_APP_API_URL || "http://localhost:8080";

export const api = axios.create({
  baseURL: API_URL,
  headers: { "Content-Type": "application/json" },
});

// ---- Tutores / Pets ----
export const cadastrarTutor = (dados) => api.post("/tutores", dados);

// ---- Veterinarios ----
export const listarVeterinarios = () => api.get("/veterinarios");
export const listarDisponibilidade = (veterinarioId, data) =>
  api.get(`/veterinarios/${veterinarioId}/disponibilidade`, { params: { data } });

// ---- Consultas ----
export const criarConsulta = (dados) => api.post("/consultas", dados);
export const listarConsultasDoDia = (data) => api.get("/consultas", { params: { data } });
export const obterPredicaoNoShow = (consultaId) => api.get(`/consultas/${consultaId}/predicao-no-show`);
export const dispararLembrete = (consultaId) => api.post(`/consultas/${consultaId}/lembrete`);

// ---- Historico / Pets ----
export const obterHistoricoPet = (petId) => api.get(`/pets/${petId}/historico`);
export const obterResumoHistoricoPet = (petId) => api.get(`/pets/${petId}/historico/resumo`);
