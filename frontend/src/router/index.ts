import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
    { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue') },
    {
      path: '/knowledge-bases',
      name: 'knowledge-bases',
      component: () => import('../views/KnowledgeBaseListView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/knowledge-bases/:id',
      name: 'knowledge-base-detail',
      component: () => import('../views/KnowledgeBaseDetailView.vue'),
      meta: { requiresAuth: true },
    },
    { path: '/', redirect: '/knowledge-bases' },
    { path: '/:pathMatch(.*)*', redirect: '/knowledge-bases' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isAuthenticated.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if ((to.name === 'login' || to.name === 'register') && auth.isAuthenticated.value) {
    return { name: 'knowledge-bases' }
  }
  return true
})

export default router
