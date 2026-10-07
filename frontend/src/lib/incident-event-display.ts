import { memberDisplayName } from "@/lib/incident-display";
import type { IncidentEvent } from "@/types/incident";

export type EventNameContext = {
  serviceName: (id: string | null | undefined) => string;
  memberName: (id: string | null | undefined) => string;
};

export function actorDisplayName(event: IncidentEvent) {
  return memberDisplayName(event.actorFirstName, event.actorLastName, event.actorEmail);
}

export function formatEventRelativeTime(iso: string) {
  const date = new Date(iso);
  const diffMs = Date.now() - date.getTime();
  if (Number.isNaN(diffMs)) {
    return "";
  }
  const seconds = Math.floor(diffMs / 1000);
  if (seconds < 60) {
    return "just now";
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes} minute${minutes === 1 ? "" : "s"} ago`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours} hour${hours === 1 ? "" : "s"} ago`;
  }
  const days = Math.floor(hours / 24);
  if (days < 7) {
    return `${days} day${days === 1 ? "" : "s"} ago`;
  }
  return date.toLocaleString();
}

export function formatEventAbsoluteTime(iso: string) {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

function payloadString(value: unknown): string | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === "string") {
    return value;
  }
  return String(value);
}

function oldNewStrings(payload: Record<string, unknown>) {
  return {
    old: payloadString(payload.old),
    new: payloadString(payload.new),
  };
}

export function eventPrimaryText(event: IncidentEvent, names: EventNameContext) {
  const actor = actorDisplayName(event);
  const payload = event.payload ?? {};

  switch (event.type) {
    case "INCIDENT_CREATED":
      return `${actor} created the incident`;
    case "STATUS_CHANGED": {
      const { old, new: next } = oldNewStrings(payload);
      if (old && next) {
        return `${actor} changed status from ${old} to ${next}`;
      }
      return `${actor} changed the incident status`;
    }
    case "SEVERITY_CHANGED": {
      const { old, new: next } = oldNewStrings(payload);
      if (old && next) {
        return `${actor} changed severity from ${old} to ${next}`;
      }
      return `${actor} changed the incident severity`;
    }
    case "SERVICE_CHANGED": {
      const { old, new: next } = oldNewStrings(payload);
      const oldName = names.serviceName(old);
      const newName = names.serviceName(next);
      if (!old && next) {
        return `${actor} assigned the service to ${newName}`;
      }
      if (old && !next) {
        return `${actor} removed the service`;
      }
      if (old && next) {
        return `${actor} changed the service from ${oldName} to ${newName}`;
      }
      return `${actor} changed the service`;
    }
    case "COMMANDER_CHANGED": {
      const { old, new: next } = oldNewStrings(payload);
      const oldName = names.memberName(old);
      const newName = names.memberName(next);
      if (!old && next) {
        return `${actor} assigned ${newName} as commander`;
      }
      if (old && !next) {
        return `${actor} removed ${oldName} as commander`;
      }
      if (old && next) {
        return `${actor} changed commander from ${oldName} to ${newName}`;
      }
      return `${actor} changed the commander`;
    }
    case "TITLE_CHANGED":
      return `${actor} changed the incident title`;
    case "DESCRIPTION_CHANGED":
      return `${actor} changed the incident description`;
    default:
      return `${actor} updated the incident`;
  }
}

export function eventDetailLines(event: IncidentEvent, names: EventNameContext): string[] {
  const payload = event.payload ?? {};

  switch (event.type) {
    case "INCIDENT_CREATED": {
      const lines: string[] = [];
      const title = payloadString(payload.title);
      if (title) {
        lines.push(`"${title}"`);
      }
      const severity = payloadString(payload.severity);
      if (severity) {
        lines.push(severity);
      }
      const serviceId = payloadString(payload.serviceId);
      if (serviceId) {
        lines.push(`Service: ${names.serviceName(serviceId)}`);
      }
      const commanderId = payloadString(payload.commanderId);
      if (commanderId) {
        lines.push(`Commander: ${names.memberName(commanderId)}`);
      }
      return lines;
    }
    case "STATUS_CHANGED":
    case "SEVERITY_CHANGED": {
      const { old, new: next } = oldNewStrings(payload);
      if (old && next) {
        return [`${old} → ${next}`];
      }
      return [];
    }
    case "SERVICE_CHANGED": {
      const { old, new: next } = oldNewStrings(payload);
      return [`${names.serviceName(old)} → ${names.serviceName(next)}`];
    }
    case "COMMANDER_CHANGED": {
      const { old, new: next } = oldNewStrings(payload);
      return [`${names.memberName(old)} → ${names.memberName(next)}`];
    }
    case "TITLE_CHANGED": {
      const { old, new: next } = oldNewStrings(payload);
      if (old && next) {
        return [`"${old}" → "${next}"`];
      }
      return [];
    }
    case "DESCRIPTION_CHANGED":
      return ["Description updated"];
    default:
      return [];
  }
}

export function defaultServiceName(id: string | null | undefined) {
  if (!id) {
    return "Unassigned";
  }
  return "Unknown service";
}

export function defaultMemberName(id: string | null | undefined) {
  if (!id) {
    return "Unassigned";
  }
  return "Unknown member";
}
