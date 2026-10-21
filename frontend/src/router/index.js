import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('@/views/HomeView.vue') },
    { path: '/login', component: () => import('@/views/LoginView.vue') },
    { path: '/register', component: () => import('@/views/RegisterView.vue') },
    { path: '/profile/:id', component: () => import('@/views/ProfileView.vue') },
    { path: '/project/:id', component: () => import('@/views/ProjectView.vue') },

    {
      path: '/admin',
      component: () => import('@/views/AdminView.vue'),
      beforeEnter: async (to, from, next) => {
        const auth = useAuthStore()
        const user = await auth.checkAuth()
        if (user && user.role === 'ADMIN') {
          next()
        } else {
          next('/')
        }
      }
    }
  ]
})

export default router