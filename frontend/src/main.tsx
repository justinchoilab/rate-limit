import React from 'react'
import ReactDOM from 'react-dom/client'
import './styles/globals.css'
import './styles/app.css'
import RateLimitPage from './features/rate-limit/RateLimitPage'

document.documentElement.setAttribute(
  'data-theme',
  localStorage.getItem('theme') || 'dark'
)

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <div className="content-shell" style={{ minHeight: '100vh' }}>
      <RateLimitPage />
    </div>
  </React.StrictMode>
)
