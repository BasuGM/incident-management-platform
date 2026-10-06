import type { Incident } from "@/types/incident";

export function formatIncidentTimestamp(value: string | null | undefined) {
  if (!value) {
    return "—";
  }
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

export function memberDisplayName(firstName: string, lastName: string, email: string) {
  const name = `${firstName} ${lastName}`.trim();
  return name || email;
}

export function reporterDisplayName(incident: Incident) {
  return memberDisplayName(
    incident.reporterFirstName,
    incident.reporterLastName,
    incident.reporterEmail,
  );
}

export function commanderDisplayName(incident: Incident) {
  if (!incident.commanderId) {
    return null;
  }
  return memberDisplayName(
    incident.commanderFirstName ?? "",
    incident.commanderLastName ?? "",
    incident.commanderEmail ?? "",
  );
}

export function isTerminalIncidentStatus(status: Incident["status"]) {
  return status === "RESOLVED" || status === "CANCELLED";
}
