import { createRouter, createWebHistory } from 'vue-router'

import HomeView from '@/views/HomeView.vue'
import RegisterView from '@/views/RegisterView.vue'
import ReviewView from '@/views/ReviewView.vue'
import ExpressionSearchView from '@/views/ExpressionSearchView.vue'

/**
 * 画面(URL)とページコンポーネントの対応表。
 *
 * Vue Router は URL に応じて表示するコンポーネントを切り替える。
 * 画面遷移はページ全体を再読み込みせず、App.vue の <RouterView> 部分だけを差し替える(SPA)。
 *
 * name はコードから画面を指定するときに使う識別名。
 * URL を直接書く代わりに { name: 'home' } のように指定すると、URL を変更しても呼び出し側の修正が不要になる。
 */
const router = createRouter({
  // createWebHistory: 通常のURL形式(/review 等)で画面を切り替える
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView }, // S01 ホーム
    { path: '/register', name: 'register', component: RegisterView }, // S02 新規登録
    { path: '/review', name: 'review', component: ReviewView }, // S03 復習
    { path: '/expressions', name: 'expressions', component: ExpressionSearchView }, // S04 表現検索
  ],
})

export default router
