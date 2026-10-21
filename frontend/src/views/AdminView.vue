<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/services/api'

const router = useRouter()
const users = ref([])

onMounted(async () => {
  try {
    const { data } = await api.get('/admin/users')
    users.value = data
  } catch {
    router.replace('/')
  }
})
</script>

<template>
  <div>
    <h2>Administration</h2>
    <p class="text-muted">Panel d'administration — liste de tous les utilisateurs</p>

    <table class="table table-striped">
      <thead>
        <tr>
          <th>ID</th>
          <th>Email</th>
          <th>Rôle</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in users" :key="user.id">
          <td>{{ user.id }}</td>
          <td>{{ user.email }}</td>
          <td>
            <span :class="user.role === 'ADMIN' ? 'badge bg-danger' : 'badge bg-primary'">
              {{ user.role }}
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
