"use client";

import { useAuth } from "@/components/providers/auth-provider";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/lib/api/client";
import { getApiErrorDetails, getApiErrorMessage } from "@/lib/api/errors";
import {
  mapRegisterApiFieldErrors,
  registerPasswordHint,
  validateRegisterForm,
  type RegisterFieldErrors,
} from "@/lib/validation/register";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

export default function RegisterPage() {
  const { register, isAuthenticated, isLoading } = useAuth();
  const router = useRouter();
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<RegisterFieldErrors>({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace("/dashboard");
    }
  }, [isAuthenticated, isLoading, router]);

  function clearFieldError(field: keyof RegisterFieldErrors) {
    setFieldErrors((current) => {
      if (!current[field]) {
        return current;
      }
      const next = { ...current };
      delete next[field];
      if (Object.keys(next).length === 0) {
        return {};
      }
      return next;
    });
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const values = { firstName, lastName, email, password, confirmPassword };
    const clientErrors = validateRegisterForm(values);
    if (Object.keys(clientErrors).length > 0) {
      setFieldErrors(clientErrors);
      return;
    }

    setFieldErrors({});
    setSubmitting(true);
    try {
      await register({
        email: values.email.trim(),
        password: values.password,
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
      });
      router.replace("/dashboard");
    } catch (err) {
      const apiFieldErrors = mapRegisterApiFieldErrors(getApiErrorDetails(err));
      if (Object.keys(apiFieldErrors).length > 0) {
        setFieldErrors(apiFieldErrors);
        return;
      }

      if (err instanceof ApiError && err.status === 409) {
        setFieldErrors({ email: getApiErrorMessage(err, "A user with this email already exists") });
        return;
      }

      setFieldErrors({ form: getApiErrorMessage(err, "Registration failed") });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="rounded-lg border bg-card p-6 shadow-sm">
      <h1 className="text-xl font-semibold">Create account</h1>
      <p className="mt-1 text-sm text-muted-foreground">New users are assigned the Engineer role.</p>
      <form className="mt-6 space-y-4" onSubmit={handleSubmit} noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="firstName">First name</Label>
            <Input
              id="firstName"
              autoComplete="given-name"
              value={firstName}
              aria-invalid={Boolean(fieldErrors.firstName)}
              onChange={(event) => {
                setFirstName(event.target.value);
                clearFieldError("firstName");
              }}
            />
            <FieldError message={fieldErrors.firstName} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="lastName">Last name</Label>
            <Input
              id="lastName"
              autoComplete="family-name"
              value={lastName}
              aria-invalid={Boolean(fieldErrors.lastName)}
              onChange={(event) => {
                setLastName(event.target.value);
                clearFieldError("lastName");
              }}
            />
            <FieldError message={fieldErrors.lastName} />
          </div>
        </div>
        <div className="space-y-2">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            value={email}
            aria-invalid={Boolean(fieldErrors.email)}
            onChange={(event) => {
              setEmail(event.target.value);
              clearFieldError("email");
            }}
          />
          <FieldError message={fieldErrors.email} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="password">Password</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            value={password}
            aria-invalid={Boolean(fieldErrors.password)}
            onChange={(event) => {
              setPassword(event.target.value);
              clearFieldError("password");
            }}
          />
          <p className="text-xs text-muted-foreground">{registerPasswordHint()}</p>
          <FieldError message={fieldErrors.password} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="confirmPassword">Confirm password</Label>
          <Input
            id="confirmPassword"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            aria-invalid={Boolean(fieldErrors.confirmPassword)}
            onChange={(event) => {
              setConfirmPassword(event.target.value);
              clearFieldError("confirmPassword");
            }}
          />
          <FieldError message={fieldErrors.confirmPassword} />
        </div>
        <FieldError message={fieldErrors.form} />
        <Button type="submit" className="w-full" disabled={submitting}>
          {submitting ? "Creating account…" : "Register"}
        </Button>
      </form>
      <p className="mt-4 text-sm text-muted-foreground">
        Already have an account?{" "}
        <Link href="/login" className="text-primary underline-offset-4 hover:underline">
          Sign in
        </Link>
      </p>
    </section>
  );
}
