import Link from "next/link";

export default function NotFound() {
  return (
    <div className="flex min-h-[40vh] flex-col items-center justify-center gap-3 text-center">
      <h2 className="text-lg font-semibold">Page not found</h2>
      <p className="text-sm text-muted-foreground">
        The page you requested does not exist.
      </p>
      <Link href="/" className="text-sm font-medium underline">
        Return home
      </Link>
    </div>
  );
}
