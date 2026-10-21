<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/services/api'

const route = useRoute()
const router = useRouter()
const project = ref({})

onMounted(async () => {
  try {
    const { data } = await api.get(`/projects/${route.params.id}`)
    project.value = data

    const refParam = new URLSearchParams(window.location.search).get('ref')
    if (refParam) {
      const banner = document.getElementById('ref-banner')
      if (banner) {
        banner.style.display = 'block'
        banner.textContent = `Vous venez de : ${refParam}`
      }
    }
  } catch {
    router.replace('/')
  }
})
</script>

<template>
  <div>
    <div id="ref-banner" class="alert alert-info" style="display:none"></div>

    <div class="card">
      <div class="card-body">
        <div class="d-flex justify-content-between align-items-center mb-2">
          <h2 class="mb-0">{{ project.title }}</h2>
          <span v-if="project.isPublic !== undefined" :class="project.isPublic ? 'badge bg-success' : 'badge bg-secondary'">
            {{ project.isPublic ? 'Public' : 'Privé' }}
          </span>
        </div>
        <p class="text-muted">Propriétaire : #{{ project.ownerId }}</p>
        <p>{{ project.description }}</p>
        <a v-if="project.githubUrl && project.githubUrl.startsWith('https://')" :href="project.githubUrl"
           class="btn btn-dark" target="_blank" rel="noopener noreferrer">
          Voir sur GitHub
        </a>
      </div>
    </div>
  </div>
</template>
