export function LoadingState({ label }: { label: string }) {
  return (
    <div className="rounded-md border border-white/10 bg-black/20 px-4 py-3 text-sm text-muted-foreground" role="status">
      {label}
    </div>
  )
}

export function ErrorState({ title, message }: { title: string; message?: string }) {
  return (
    <div className="rounded-md border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm" role="alert">
      <p className="font-medium text-destructive">{title}</p>
      {message ? <p className="mt-1 text-muted-foreground">{message}</p> : null}
    </div>
  )
}
