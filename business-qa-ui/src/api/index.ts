import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

api.interceptors.response.use(
  (response) => response.data?.data !== undefined ? response.data.data : response.data,
  (error) => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export const dashboardApi = {
  getOverview: () => api.get('/dashboard/overview'),
}

export const moduleApi = {
  list: () => api.get('/module/list'),
  get: (id: number) => api.get(`/module/${id}`),
  create: (data: any) => api.post('/module', data),
  update: (id: number, data: any) => api.put(`/module/${id}`, data),
  delete: (id: number) => api.delete(`/module/${id}`),
}

export const documentApi = {
  page: (params: any) => api.get('/document/page', { params }),
  list: (moduleId: number) => api.get('/document/list', { params: { moduleId } }),
  get: (id: number) => api.get(`/document/${id}`),
  create: (data: any) => api.post('/document', data),
  upload: (formData: FormData) => api.post('/document/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  }),
  download: (id: number) => api.get(`/document/${id}/download`, { responseType: 'blob' }),
  update: (id: number, data: any) => api.put(`/document/${id}`, data),
  delete: (id: number) => api.delete(`/document/${id}`),
  vectorize: (id: number) => api.post(`/document/${id}/vectorize`),
  vectorizeAll: () => api.post('/document/vectorize-all'),
}

export const chatApi = {
  createSession: (title?: string, moduleIds?: number[]) =>
    api.post('/chat/sessions', moduleIds, { params: { title } }),
  listSessions: (limit: number = 20) => api.get('/chat/sessions', { params: { limit } }),
  getSession: (id: number) => api.get(`/chat/sessions/${id}`),
  getMessages: (id: number) => api.get(`/chat/sessions/${id}/messages`),
  deleteSession: (id: number) => api.delete(`/chat/sessions/${id}`),
}

export const changeApi = {
  detect: (moduleId: number) => api.post(`/change/detect/${moduleId}`, null, { timeout: 300000 }),
  listDetections: (moduleId?: number) =>
    api.get('/change/detections', { params: moduleId ? { moduleId } : {} }),
  getDetection: (id: number) => api.get(`/change/detections/${id}`),
  getSuggestions: (detectionId: number) => api.get(`/change/detections/${detectionId}/suggestions`),
  pendingSuggestions: () => api.get('/change/suggestions/pending'),
  applySuggestion: (id: number) => api.put(`/change/suggestions/${id}/apply`),
  ignoreSuggestion: (id: number) => api.put(`/change/suggestions/${id}/ignore`),
}

export default api
