import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { RequireAdmin } from './auth/RequireAdmin'
import { RequireAuth } from './auth/RequireAuth'
import { AppShell } from './layout/AppShell'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { StockPortfoliosPage } from './pages/stock/StockPortfoliosPage'
import { StockPortfolioDetailPage } from './pages/stock/StockPortfolioDetailPage'
import { CryptoPortfoliosPage } from './pages/crypto/CryptoPortfoliosPage'
import { CryptoPortfolioDetailPage } from './pages/crypto/CryptoPortfolioDetailPage'
import { ExpensesPage } from './pages/expenses/ExpensesPage'
import { AdminCatalogPage } from './pages/admin/AdminCatalogPage'
import { ThemeProvider } from './theme/ThemeContext'

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route
              element={
                <RequireAuth>
                  <AppShell />
                </RequireAuth>
              }
            >
              <Route path="/" element={<HomePage />} />
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
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  )
}
