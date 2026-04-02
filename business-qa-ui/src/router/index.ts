import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: () => import('../views/Dashboard.vue') },
    { path: '/chat', component: () => import('../views/Chat.vue') },
    { path: '/modules', component: () => import('../views/Modules.vue') },
    { path: '/documents', component: () => import('../views/Documents.vue') },
    { path: '/changes', component: () => import('../views/Changes.vue') },
    { path: '/prompts', component: () => import('../views/Prompts.vue') },
    { path: '/guardrails', component: () => import('../views/Guardrails.vue') },
  ]
})

export default router
