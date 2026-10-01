const rupees = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const shortDate = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" });

export function formatMoney(amount: number): string {
  return rupees.format(amount);
}

export function formatSignedMoney(amount: number): string {
  const absolute = rupees.format(Math.abs(amount));
  return amount < 0 ? `−${absolute}` : `+${absolute}`;
}

/** Sums in whole paise so floating-point error can't creep into totals. */
export function sumMoney(amounts: number[]): number {
  return amounts.reduce((total, amount) => total + Math.round(amount * 100), 0) / 100;
}

/** For LocalDate strings like "2026-09-03": parsed as local midnight so the day never shifts across time zones. */
export function formatLocalDate(isoDate: string): string {
  return shortDate.format(new Date(`${isoDate}T00:00:00`));
}

export function formatInstant(isoInstant: string): string {
  return shortDate.format(new Date(isoInstant));
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
