import type { ReactNode } from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/modules/core/ui/card";

interface AuthCardProps {
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
}

/** Marco centrado de las pantallas de auth (marca + título + formulario). */
export function AuthCard({ title, description, children, footer }: AuthCardProps) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/40 p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="items-center text-center">
          <div className="mb-2 flex items-center gap-2">
            <span
              aria-hidden
              className="grid size-10 place-items-center rounded-xl bg-gradient-to-br from-secondary-foreground to-primary text-lg text-primary-foreground shadow"
            >
              🦷
            </span>
            <span className="text-lg font-semibold tracking-tight">OdontoRisas</span>
          </div>
          <CardTitle>{title}</CardTitle>
          {description ? <CardDescription>{description}</CardDescription> : null}
        </CardHeader>
        <CardContent>{children}</CardContent>
        {footer ? <CardFooter className="justify-center">{footer}</CardFooter> : null}
      </Card>
    </div>
  );
}
