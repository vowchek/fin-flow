import './Footer.css'

export function Footer() {
  return (
    <footer className="footer">
      <div className="footer-inner">
        <span className="footer-brand">Fin Flow</span>
        <span className="footer-copy">© {new Date().getFullYear()}</span>
      </div>
    </footer>
  )
}
