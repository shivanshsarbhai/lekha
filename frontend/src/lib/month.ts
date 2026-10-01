/** A calendar month written as "YYYY-MM", the same format as <input type="month">. */
export type Month = string;

const MONTH_PATTERN = /^(\d{4})-(0[1-9]|1[0-2])$/;

const monthLabel = new Intl.DateTimeFormat("en-IN", { month: "long", year: "numeric" });

export function isMonth(value: string | null): value is Month {
  return value !== null && MONTH_PATTERN.test(value);
}

export function currentMonth(): Month {
  return toMonth(new Date());
}

/** The first and last day of the month as ISO dates, e.g. "2026-02-01" to "2026-02-28". */
export function monthRange(month: Month): { from: string; to: string } {
  const [year, monthNumber] = parse(month);
  const lastDay = new Date(year, monthNumber, 0).getDate();
  return { from: `${month}-01`, to: `${month}-${pad(lastDay)}` };
}

export function shiftMonth(month: Month, delta: number): Month {
  const [year, monthNumber] = parse(month);
  return toMonth(new Date(year, monthNumber - 1 + delta, 1));
}

export function formatMonth(month: Month): string {
  const [year, monthNumber] = parse(month);
  return monthLabel.format(new Date(year, monthNumber - 1, 1));
}

function parse(month: Month): [number, number] {
  return [Number(month.slice(0, 4)), Number(month.slice(5, 7))];
}

function toMonth(date: Date): Month {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`;
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}
