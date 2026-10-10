import { useWatch, type Control, type FieldError, type Path, type UseFormRegister, type UseFormSetValue, type UseFormTrigger } from "react-hook-form";
import countryCodes from "@/modules/patients/data/american-country-codes.json";
import { Input } from "@/modules/core/ui/input";
import { Label } from "@/modules/core/ui/label";
import type { PatientValues } from "@/modules/patients/schemas/patient";

const DEFAULT_COUNTRY_CODE = "+51";
const codesByLength = [...countryCodes].sort((a, b) => b.code.length - a.code.length);

type PhoneNumberFieldProps = {
  label: string;
  name: Path<PatientValues>;
  control: Control<PatientValues>;
  register: UseFormRegister<PatientValues>;
  setValue: UseFormSetValue<PatientValues>;
  trigger: UseFormTrigger<PatientValues>;
  error?: FieldError;
  hint: string;
  required?: boolean;
};

/** Selector de país americano + número local; el formulario conserva un único valor E.164. */
export function PhoneNumberField({
  label,
  name,
  control,
  register,
  setValue,
  trigger,
  error,
  hint,
  required,
}: PhoneNumberFieldProps) {
  const value = useWatch({ control, name }) ?? "";
  const { countryCode, nationalNumber } = splitPhone(value);
  const errorId = `${name}-error`;
  const hintId = `${name}-hint`;
  const describedBy = error ? errorId : hintId;

  const update = (nextCountryCode: string, nextNationalNumber: string) => {
    setValue(name, nextNationalNumber ? `${nextCountryCode}${nextNationalNumber}` : nextCountryCode, {
      shouldDirty: true,
      shouldTouch: true,
      shouldValidate: Boolean(error),
    });
  };

  return (
    <div className="flex flex-col gap-1.5">
      {/* Mantiene el campo registrado para que los errores del servidor puedan apuntar a él. */}
      <input type="hidden" {...register(name)} />
      <Label htmlFor={name}>
        {label}
        {required ? <span className="text-destructive"> *</span> : null}
      </Label>
      <div className="grid grid-cols-[minmax(8.5rem,0.9fr)_1.6fr] gap-2">
        <select
          aria-label={`Código de país para ${label}`}
          value={countryCode}
          onChange={(event) => update(event.target.value, nationalNumber)}
          className="h-11 rounded-xl border border-input bg-card px-3 text-sm ring-offset-background focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          {countryCodes.map((country) => (
            <option key={`${country.country}-${country.code}`} value={country.code}>
              {country.country} ({country.code})
            </option>
          ))}
        </select>
        <Input
          id={name}
          type="tel"
          inputMode="numeric"
          maxLength={14}
          value={nationalNumber}
          placeholder="Número de teléfono"
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className="h-11 rounded-xl"
          onChange={(event) => update(countryCode, event.target.value.replace(/\D/g, ""))}
          onBlur={() => void trigger(name)}
        />
      </div>
      {!error ? <p id={hintId} className="text-xs text-muted-foreground">{hint}</p> : null}
      {error ? <p id={errorId} role="alert" className="text-xs text-destructive">{error.message}</p> : null}
    </div>
  );
}

function splitPhone(value: string) {
  const digits = value.replace(/\D/g, "");
  const matchingCode = codesByLength.find((country) => digits.startsWith(country.code.slice(1)));
  const countryCode = matchingCode?.code ?? DEFAULT_COUNTRY_CODE;
  return {
    countryCode,
    nationalNumber: matchingCode ? digits.slice(matchingCode.code.length - 1) : digits.replace(/^51/, ""),
  };
}
