import type { ComponentProps } from "react";
import type { VariantProps } from "class-variance-authority";
import { cn } from "@/modules/core/utils/cn";
import { buttonVariants } from "./button-variants";

export interface ButtonProps
  extends ComponentProps<"button">, // incluye `ref` (React 19: ref como prop)
    VariantProps<typeof buttonVariants> {}

export function Button({ className, variant, size, ...props }: ButtonProps) {
  return <button className={cn(buttonVariants({ variant, size, className }))} {...props} />;
}
