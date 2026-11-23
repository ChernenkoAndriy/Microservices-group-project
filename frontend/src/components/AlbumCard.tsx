import { Link } from 'react-router-dom';
import { Cover } from './Cover';

interface Props {
  id: number;
  title: string;
  artist: string;
  coverUrl?: string;
  sub?: string;
}

export function AlbumCard({ id, title, artist, coverUrl, sub }: Props) {
  return (
    <Link to={`/album/${id}`} className="card album-card" data-testid="album-card">
      <Cover seed={id} url={coverUrl} label={title} />
      <div className="album-card__title" title={title}>
        {title}
      </div>
      <div className="album-card__sub">{sub ?? artist}</div>
    </Link>
  );
}
