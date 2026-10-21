import { defineStore } from 'pinia'
import api from '@/services/api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('devfolio_token') || null,
    user: JSON.parse(localStorage.getItem('devfolio_user') || 'null'),
  }),
  actions: {
    setToken(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem('devfolio_token', token)
      localStorage.setItem('devfolio_user', JSON.stringify(user))
    },
    async checkAuth() {
      const storedToken = localStorage.getItem('devfolio_token')
      if (!storedToken || storedToken === 'undefined' || storedToken === 'null') {
        this.logout()
        return null
      }
      try {
        const { data } = await api.get('/users/me')
        this.token = storedToken
        this.user = data
        localStorage.setItem('devfolio_user', JSON.stringify(data))
        return data
      } catch (err) {
        this.logout()
        return null
      }
    },
    logout() {
      this.token = null
      this.user = null
      localStorage.removeItem('devfolio_token')
      localStorage.removeItem('devfolio_user')
    }
  }
})
