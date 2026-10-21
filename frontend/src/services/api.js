import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('devfolio_token')
  if (token && token !== 'undefined' && token !== 'null') {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response) {
      if (error.response.status === 401) {
        localStorage.removeItem('devfolio_token')
        localStorage.removeItem('devfolio_user')
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
      } else if (error.response.status === 403) {
        if (window.location.pathname !== '/') {
          window.location.href = '/'
        }
      }
    }
    return Promise.reject(error)
  }
)

export default api
