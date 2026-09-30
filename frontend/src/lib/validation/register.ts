export type RegisterFormValues = {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  confirmPassword: string;
};

export type RegisterFieldErrors = Partial<Record<keyof RegisterFormValues, string>> & {
  form?: string;
};

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const PASSWORD_HINT =
  "Use at least 8 characters including both letters and numbers (max 128 characters).";

export function registerPasswordHint(): string {
  return PASSWORD_HINT;
}

function isValidPassword(password: string): boolean {
  if (password.length < 8 || password.length > 128) {
    return false;
  }
  let hasLetter = false;
  let hasDigit = false;
  for (const char of password) {
    if (/[A-Za-z]/.test(char)) {
      hasLetter = true;
    } else if (/\d/.test(char)) {
      hasDigit = true;
    }
  }
  return hasLetter && hasDigit;
}

export function validateRegisterForm(values: RegisterFormValues): RegisterFieldErrors {
  const errors: RegisterFieldErrors = {};
  const firstName = values.firstName.trim();
  const lastName = values.lastName.trim();
  const email = values.email.trim();

  if (!firstName) {
    errors.firstName = "First name is required";
  } else if (firstName.length > 100) {
    errors.firstName = "First name must be at most 100 characters";
  }

  if (!lastName) {
    errors.lastName = "Last name is required";
  } else if (lastName.length > 100) {
    errors.lastName = "Last name must be at most 100 characters";
  }

  if (!email) {
    errors.email = "Email is required";
  } else if (email.length > 320) {
    errors.email = "Email must be at most 320 characters";
  } else if (!EMAIL_PATTERN.test(email)) {
    errors.email = "Enter a valid email address";
  }

  if (!values.password) {
    errors.password = "Password is required";
  } else if (!isValidPassword(values.password)) {
    errors.password = PASSWORD_HINT;
  }

  if (!values.confirmPassword) {
    errors.confirmPassword = "Confirm your password";
  } else if (values.password !== values.confirmPassword) {
    errors.confirmPassword = "Passwords do not match";
  }

  return errors;
}

const REGISTER_FIELD_KEYS = new Set<keyof RegisterFormValues>([
  "firstName",
  "lastName",
  "email",
  "password",
  "confirmPassword",
]);

export function mapRegisterApiFieldErrors(details: string[]): RegisterFieldErrors {
  const errors: RegisterFieldErrors = {};

  for (const detail of details) {
    const separatorIndex = detail.indexOf(": ");
    if (separatorIndex === -1) {
      continue;
    }
    const field = detail.slice(0, separatorIndex);
    const message = detail.slice(separatorIndex + 2);
    if (REGISTER_FIELD_KEYS.has(field as keyof RegisterFormValues)) {
      errors[field as keyof RegisterFormValues] = message;
    }
  }

  return errors;
}
