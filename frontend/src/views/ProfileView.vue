<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/services/api'

const route = useRoute()
const user = ref({})
const projects = ref([])

onMounted(async () => {
  const userId = route.params.id
  try {
    const { data: userData } = await api.get(`/users/${userId}`)
    user.value = userData

    const { data: projectData } = await api.get(`/projects/user/${userId}`)
    projects.value = projectData
  } catch {
    // Si l'utilisateur n'existe pas
  }
})
</script>

<template>
  <div class="profile">
    <div class="card mb-4">
      <div class="card-body">
        <div class="d-flex align-items-center mb-3">
          <img v-if="user.avatarUrl" :src="user.avatarUrl" class="rounded-circle me-3"
               width="80" height="80" alt="Avatar">
          <div>
            <h2>{{ user.email }}</h2>
            <span class="badge bg-secondary">{{ user.role }}</span>
          </div>
        </div>

        <div class="bio">{{ user.bio }}</div>
      </div>
    </div>

    <h3>Projets</h3>
    <div v-if="projects.length === 0" class="text-muted mb-4">
      Aucun projet visible.
    </div>
    <div class="row">
      <div v-for="project in projects" :key="project.id" class="col-md-4 mb-3">
        <div class="card h-100">
          <div class="card-body d-flex flex-column">
            <div class="d-flex justify-content-between align-items-start mb-2">
              <h5 class="card-title mb-0">{{ project.title }}</h5>
              <span :class="project.isPublic ? 'badge bg-success' : 'badge bg-secondary'">
                {{ project.isPublic ? 'Public' : 'Privé' }}
              </span>
            </div>
            <p class="card-text flex-grow-1">{{ project.description }}</p>
            <router-link :to="`/project/${project.id}`" class="btn btn-sm btn-primary mt-2">
              Voir
            </router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
