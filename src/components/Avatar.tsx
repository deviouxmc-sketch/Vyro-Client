import { useState } from "react";

export function Avatar({ uuid, name, size = 40 }: { uuid: string; name: string; size?: number }) {
  const [failed, setFailed] = useState(false);
  if (failed) {
    return <div className="avatar fallback" style={{ width: size, height: size }}>{name.slice(0, 1).toUpperCase()}</div>;
  }
  return (
    <img
      className="avatar"
      style={{ width: size, height: size }}
      src={`https://mc-heads.net/avatar/${uuid}/${size * 2}`}
      alt={name}
      onError={() => setFailed(true)}
    />
  );
}
