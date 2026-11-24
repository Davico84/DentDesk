import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "@/test/utils";
import { ScheduleNextAppointmentLink } from "./ScheduleNextAppointmentLink";

// Scenarios de "Agendar la próxima cita propuesta en una nota" (add-appointments 4.6).
describe("ScheduleNextAppointmentLink", () => {
  it("con próxima cita lleva a la Agenda con paciente y fecha", () => {
    renderWithProviders(<ScheduleNextAppointmentLink patientId={42} date="2030-12-05" />);

    expect(screen.getByRole("link", { name: "Agendar próxima cita" })).toHaveAttribute(
      "href",
      "/appointments?patientId=42&date=2030-12-05",
    );
  });

  it("sin próxima cita no se muestra", () => {
    renderWithProviders(<ScheduleNextAppointmentLink patientId={42} date={undefined} />);

    expect(screen.queryByRole("link", { name: "Agendar próxima cita" })).not.toBeInTheDocument();
  });
});
