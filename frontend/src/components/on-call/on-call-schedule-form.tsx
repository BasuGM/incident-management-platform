"use client";

import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { memberDisplayName } from "@/lib/incident-display";
import {
  addParticipant,
  applyScopeModeChange,
  moveParticipant,
  removeParticipant,
  type OnCallScheduleFormFieldErrors,
  type OnCallScheduleFormValues,
  type OnCallScopeMode,
} from "@/lib/on-call-schedule-form";
import type { OrganizationMember, Service, Team, TeamMember } from "@/types/organization";
import Link from "next/link";
import { useMemo, useState } from "react";

const WEEKDAY_OPTIONS = [
  { value: "1", label: "Monday" },
  { value: "2", label: "Tuesday" },
  { value: "3", label: "Wednesday" },
  { value: "4", label: "Thursday" },
  { value: "5", label: "Friday" },
  { value: "6", label: "Saturday" },
  { value: "7", label: "Sunday" },
];

type OnCallScheduleFormProps = {
  organizationId: string;
  values: OnCallScheduleFormValues;
  onChange: (values: OnCallScheduleFormValues) => void;
  fieldErrors: OnCallScheduleFormFieldErrors;
  formError?: string | null;
  submitLabel: string;
  cancelHref: string;
  isPending: boolean;
  onSubmit: () => void;
  teams: Team[] | undefined;
  services: Service[] | undefined;
  teamsLoading: boolean;
  servicesLoading: boolean;
  teamsError: boolean;
  servicesError: boolean;
  orgMembers: OrganizationMember[] | undefined;
  teamMembers: TeamMember[] | undefined;
  teamMembersLoading: boolean;
  teamMembersError: boolean;
};

export function OnCallScheduleForm({
  organizationId,
  values,
  onChange,
  fieldErrors,
  formError,
  submitLabel,
  cancelHref,
  isPending,
  onSubmit,
  teams,
  services,
  teamsLoading,
  servicesLoading,
  teamsError,
  servicesError,
  orgMembers,
  teamMembers,
  teamMembersLoading,
  teamMembersError,
}: OnCallScheduleFormProps) {
  const [participantToAdd, setParticipantToAdd] = useState("");
  const [participantAddError, setParticipantAddError] = useState<string | null>(null);

  const eligibleMembers = useMemo(() => {
    if (values.scopeMode === "TEAM" && values.teamId) {
      return teamMembers ?? [];
    }
    return orgMembers ?? [];
  }, [values.scopeMode, values.teamId, teamMembers, orgMembers]);

  const participantLabels = useMemo(() => {
    const map = new Map<string, string>();
    for (const member of orgMembers ?? []) {
      map.set(member.userId, memberDisplayName(member.firstName, member.lastName, member.email));
    }
    for (const member of teamMembers ?? []) {
      map.set(member.userId, memberDisplayName(member.firstName, member.lastName, member.email));
    }
    return map;
  }, [orgMembers, teamMembers]);

  const availableToAdd = eligibleMembers.filter(
    (member) => !values.participantUserIds.includes(member.userId),
  );

  return (
    <form
      className="space-y-6"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="on-call-name">Schedule name</Label>
          <Input
            id="on-call-name"
            value={values.name}
            maxLength={100}
            required
            onChange={(event) => onChange({ ...values, name: event.target.value })}
          />
          <FieldError message={fieldErrors.name} />
        </div>

        <div className="space-y-2">
          <Label htmlFor="on-call-rotation">Rotation cadence</Label>
          <select
            id="on-call-rotation"
            className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={String(values.rotationLengthDays)}
            onChange={(event) => {
              const days = Number(event.target.value) === 1 ? 1 : 7;
              onChange({
                ...values,
                rotationLengthDays: days,
                handoffDayOfWeek: days === 7 ? values.handoffDayOfWeek || "1" : "",
              });
            }}
          >
            <option value="1">Daily</option>
            <option value="7">Weekly</option>
          </select>
        </div>

        <div className="space-y-2">
          <Label htmlFor="on-call-timezone">Timezone (IANA)</Label>
          <Input
            id="on-call-timezone"
            value={values.timezone}
            maxLength={64}
            required
            placeholder="America/New_York"
            onChange={(event) => onChange({ ...values, timezone: event.target.value })}
          />
          <FieldError message={fieldErrors.timezone} />
        </div>

        <div className="space-y-2">
          <Label htmlFor="on-call-handoff-time">Handoff time (local)</Label>
          <Input
            id="on-call-handoff-time"
            type="time"
            value={values.handoffLocalTime}
            required
            onChange={(event) => onChange({ ...values, handoffLocalTime: event.target.value })}
          />
          <FieldError message={fieldErrors.handoffLocalTime} />
        </div>

        {values.rotationLengthDays === 7 && (
          <div className="space-y-2">
            <Label htmlFor="on-call-handoff-dow">Weekly handoff day</Label>
            <select
              id="on-call-handoff-dow"
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={values.handoffDayOfWeek}
              onChange={(event) => onChange({ ...values, handoffDayOfWeek: event.target.value })}
            >
              <option value="">Select weekday</option>
              {WEEKDAY_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors.handoffDayOfWeek} />
          </div>
        )}

        <div className="space-y-2">
          <Label htmlFor="on-call-effective-from">Effective from</Label>
          <Input
            id="on-call-effective-from"
            type="date"
            value={values.effectiveFrom}
            required
            onChange={(event) => onChange({ ...values, effectiveFrom: event.target.value })}
          />
          <FieldError message={fieldErrors.effectiveFrom} />
        </div>
      </div>

      <fieldset className="space-y-3 rounded-lg border p-4">
        <legend className="px-1 text-sm font-semibold">Scope</legend>
        <div className="flex flex-wrap gap-4">
          {(["ORGANIZATION", "TEAM", "SERVICE"] as OnCallScopeMode[]).map((mode) => (
            <label key={mode} className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="on-call-scope"
                checked={values.scopeMode === mode}
                onChange={() => onChange(applyScopeModeChange(values, mode))}
              />
              {mode === "ORGANIZATION"
                ? "Organization-wide"
                : mode === "TEAM"
                  ? "Team"
                  : "Service"}
            </label>
          ))}
        </div>

        {values.scopeMode === "TEAM" && (
          <div className="space-y-2">
            <Label htmlFor="on-call-team">Team scope</Label>
            {teamsLoading && <p className="text-sm text-muted-foreground">Loading teams…</p>}
            {teamsError && (
              <p className="text-sm text-destructive">Failed to load teams.</p>
            )}
            {!teamsLoading && !teamsError && (teams?.length ?? 0) === 0 && (
              <p className="text-sm text-muted-foreground">
                Create a team in this organization before using team-scoped schedules.
              </p>
            )}
            <select
              id="on-call-team"
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={values.teamId}
              onChange={(event) =>
                onChange({ ...values, teamId: event.target.value, participantUserIds: [] })
              }
            >
              <option value="">Select team</option>
              {teams?.map((team) => (
                <option key={team.id} value={team.id}>
                  {team.name}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors.teamId} />
          </div>
        )}

        {values.scopeMode === "SERVICE" && (
          <div className="space-y-2">
            <Label htmlFor="on-call-service">Service scope</Label>
            {servicesLoading && <p className="text-sm text-muted-foreground">Loading services…</p>}
            {servicesError && (
              <p className="text-sm text-destructive">Failed to load services.</p>
            )}
            {!servicesLoading && !servicesError && (services?.length ?? 0) === 0 && (
              <p className="text-sm text-muted-foreground">
                Create a service in this organization before using service-scoped schedules.
              </p>
            )}
            <select
              id="on-call-service"
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={values.serviceId}
              onChange={(event) => onChange({ ...values, serviceId: event.target.value })}
            >
              <option value="">Select service</option>
              {services?.map((service) => (
                <option key={service.id} value={service.id}>
                  {service.name}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors.serviceId} />
          </div>
        )}
      </fieldset>

      <fieldset className="space-y-3 rounded-lg border p-4">
        <legend className="px-1 text-sm font-semibold">Rotation participants</legend>
        {values.scopeMode === "TEAM" && values.teamId && (
          <p className="text-sm text-muted-foreground">
            Team-scoped schedules require participants who belong to the selected team.
          </p>
        )}
        {values.scopeMode === "TEAM" && values.teamId && teamMembersLoading && (
          <p className="text-sm text-muted-foreground">Loading team members…</p>
        )}
        {values.scopeMode === "TEAM" && values.teamId && teamMembersError && (
          <p className="text-sm text-destructive">Failed to load team members.</p>
        )}

        <div className="flex flex-wrap items-end gap-2">
          <div className="min-w-[12rem] flex-1 space-y-2">
            <Label htmlFor="on-call-add-participant">Add participant</Label>
            <select
              id="on-call-add-participant"
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={participantToAdd}
              onChange={(event) => setParticipantToAdd(event.target.value)}
              disabled={
                values.scopeMode === "TEAM" &&
                (!values.teamId || teamMembersLoading || availableToAdd.length === 0)
              }
            >
              <option value="">Select member</option>
              {availableToAdd.map((member) => (
                <option key={member.userId} value={member.userId}>
                  {memberDisplayName(member.firstName, member.lastName, member.email)}
                </option>
              ))}
            </select>
          </div>
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              const result = addParticipant(values.participantUserIds, participantToAdd);
              if ("error" in result) {
                setParticipantAddError(result.error);
                return;
              }
              setParticipantAddError(null);
              setParticipantToAdd("");
              onChange({ ...values, participantUserIds: result });
            }}
          >
            Add
          </Button>
        </div>
        <FieldError message={participantAddError ?? fieldErrors.participants} />

        {values.participantUserIds.length === 0 ? (
          <p className="text-sm text-muted-foreground">No participants added yet.</p>
        ) : (
          <ol className="space-y-2">
            {values.participantUserIds.map((userId, index) => (
              <li
                key={userId}
                className="flex flex-wrap items-center justify-between gap-2 rounded-md border px-3 py-2"
              >
                <span className="text-sm">
                  <span className="mr-2 font-mono text-xs text-muted-foreground">{index + 1}</span>
                  {participantLabels.get(userId) ?? "Member"}
                </span>
                <div className="flex gap-1">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    aria-label={`Move ${participantLabels.get(userId) ?? "participant"} up`}
                    disabled={index === 0}
                    onClick={() =>
                      onChange({
                        ...values,
                        participantUserIds: moveParticipant(values.participantUserIds, index, "up"),
                      })
                    }
                  >
                    Up
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    aria-label={`Move ${participantLabels.get(userId) ?? "participant"} down`}
                    disabled={index === values.participantUserIds.length - 1}
                    onClick={() =>
                      onChange({
                        ...values,
                        participantUserIds: moveParticipant(
                          values.participantUserIds,
                          index,
                          "down",
                        ),
                      })
                    }
                  >
                    Down
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() =>
                      onChange({
                        ...values,
                        participantUserIds: removeParticipant(values.participantUserIds, index),
                      })
                    }
                  >
                    Remove
                  </Button>
                </div>
              </li>
            ))}
          </ol>
        )}
      </fieldset>

      <FieldError message={formError ?? fieldErrors.form} />

      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={isPending}>
          {isPending ? "Saving…" : submitLabel}
        </Button>
        <Link
          href={cancelHref}
          className="inline-flex h-9 items-center justify-center rounded-md border px-4 text-sm hover:bg-muted/60"
        >
          Cancel
        </Link>
      </div>

      <p className="sr-only" data-organization-id={organizationId} />
    </form>
  );
}
