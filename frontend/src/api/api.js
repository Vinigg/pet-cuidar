import axios from "axios";

const API_URL = process.env.REACT_APP_API_URL || "http://localhost:8080";

export const api = axios.create({
  baseURL: API_URL,
  headers: { "Content-Type": "application/json" },
});

// ---- Tutores / Pets ----
export const cadastrarTutor = (dados) => api.post("/tutores", dados);
export const listarPets = (tutorId) => api.get("/pets", { params: { tutorId } });

// ---- Veterinarios ----
export const listarVeterinarios = (params) => api.get("/veterinarios", { params });
export const cadastrarVeterinario = (dados) => api.post("/veterinarios", dados);
export const obterVeterinario = (id) => api.get(`/veterinarios/${id}`);
export const atualizarVeterinario = (id, dados) => api.put(`/veterinarios/${id}`, dados);
export const listarHorariosVeterinario = (id) => api.get(`/veterinarios/${id}/horarios-atendimento`);
export const atualizarHorariosVeterinario = (id, dados) => api.put(`/veterinarios/${id}/horarios-atendimento`, dados);
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

// ---- Atendimentos ----
export const registrarAtendimento = (dados) => api.post("/atendimentos", dados);
export const atualizarAtendimento = (dados) => api.patch("/atendimentos", dados);
export const obterAtendimento = (id) => api.get(`/atendimentos/${id}`);
export const listarAtendimentosPorPet = (petId) => api.get(`/atendimentos/pet/${petId}`);
