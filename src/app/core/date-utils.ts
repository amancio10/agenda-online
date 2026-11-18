/** Converte uma data local para 'YYYY-MM-DD' (sem fuso horário). */
export function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Converte 'YYYY-MM-DD' para uma data local à meia-noite. */
export function fromIsoDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

/** Combina uma data com um horário 'HH:mm'. */
export function atTime(date: Date, time: string): Date {
  const [h, min] = time.split(':').map(Number);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), h, min);
}

const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

export function formatWeekday(date: Date): string {
  return capitalize(date.toLocaleDateString('pt-BR', { weekday: 'long' }));
}

export function formatLongDate(date: Date): string {
  return date.toLocaleDateString('pt-BR', { day: 'numeric', month: 'long', year: 'numeric' });
}

export function formatMonthYear(date: Date): string {
  return capitalize(date.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' }));
}
