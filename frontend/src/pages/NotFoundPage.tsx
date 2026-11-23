import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="state state--error" role="alert">
      <strong>ERROR 404: TASTE NOT FOUND</strong>
      <p>This page does not exist. Like your plans for tonight.</p>
      <Link to="/" className="btn">
        Back to safety
      </Link>
    </div>
  );
}
