export default function AuthLayout({ children }: LayoutProps<"/">) {
  return (
    <div className="flex flex-1 items-center justify-center py-12">
      <div className="w-full max-w-md">{children}</div>
    </div>
  );
}
