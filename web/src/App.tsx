import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { AuthModal } from './auth/AuthModal'
import { AuthModalProvider, useAuthModal } from './auth/AuthModalContext'
import { RequireAdmin } from './auth/RequireAdmin'
import { RequireAuth } from './auth/RequireAuth'
import { AppShell } from './layout/AppShell'
import { HomePage } from './pages/HomePage'
import { StockPortfoliosPage } from './pages/stock/StockPortfoliosPage'
import { StockPortfolioDetailPage } from './pages/stock/StockPortfolioDetailPage'
import { CryptoPortfoliosPage } from './pages/crypto/CryptoPortfoliosPage'
import { CryptoPortfolioDetailPage } from './pages/crypto/CryptoPortfolioDetailPage'
import { ExpensesPage } from './pages/expenses/ExpensesPage'
import { AdminCatalogPage } from './pages/admin/AdminCatalogPage'
import { ThemeProvider } from './theme/ThemeContext'

function AppRoutes() {
  const { open, mode, close } = useAuthModal()

  return (
    <>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route
          element={
            <RequireAuth>
              <AppShell />
            </RequireAuth>
          }
        >
          <Route path="/stocks" element={<StockPortfoliosPage />} />
          <Route path="/stocks/:id" element={<StockPortfolioDetailPage />} />
          <Route path="/crypto" element={<CryptoPortfoliosPage />} />
          <Route path="/crypto/:id" element={<CryptoPortfolioDetailPage />} />
          <Route path="/expenses" element={<ExpensesPage />} />
          <Route
            path="/admin"
            element={
              <RequireAdmin>
                <AdminCatalogPage />
              </RequireAdmin>
            }
          />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      <AuthModal open={open} initialMode={mode} onClose={close} />
    </>
  )
}

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <AuthModalProvider>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </AuthModalProvider>
      </AuthProvider>
    </ThemeProvider>
  )
}
