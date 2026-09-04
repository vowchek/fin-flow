import { Outlet } from 'react-router-dom'
import { Header } from './Header'

export function AppShell() {
  return (
    <>
      <Header />
      <main>
        <Outlet />
      </main>
    </>
  )
}
