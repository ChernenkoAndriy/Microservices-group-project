import { Outlet } from 'react-router-dom';
import { Header } from './Header';
import { PlayerBar } from './PlayerBar';
import { Sidebar } from './Sidebar';

export function Layout() {
  return (
    <div className="app">
      <Header />
      <Sidebar />
      <main className="main" id="main">
        <Outlet />
      </main>
      <footer className="footer">© 1997 Spotty Corp. All rights regretted.</footer>
      <PlayerBar />
      <div className="scanlines" aria-hidden="true" />
    </div>
  );
}
