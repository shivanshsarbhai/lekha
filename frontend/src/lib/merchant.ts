/**
 * A readable title for a bank narration: "UPI-SWIGGY-SWIGGY8@YBL-…-ORDER" becomes "Swiggy". Display only; the
 * narration itself is never changed. Falls back to the narration when nothing name-like is found.
 */
export function merchantName(narration: string): string {
  const text = narration.trim().toUpperCase();
  if (/^(ATW|NWD|EAW)-/.test(text)) return "ATM withdrawal";

  const withoutIds = text.replace(/[A-Z0-9._]+@[A-Z][A-Z0-9]*/g, " ");
  const prefix = PREFIXES.find((candidate) => withoutIds.startsWith(candidate));
  const rest = prefix ? withoutIds.slice(prefix.length) : withoutIds;

  for (const field of rest.split("-")) {
    const words = field
      .replace(/[^A-Z0-9& ]/g, " ")
      .split(/\s+/)
      .filter((word) => word !== "" && !/\d/.test(word));
    while (words.length > 0 && LEGAL_SUFFIXES.has(words.at(-1) ?? "")) words.pop();
    if (words.join("").replace(/[^A-Z]/g, "").length >= 2) return titleCase(words);
  }
  return narration;
}

const PREFIXES = [
  "UPI-",
  "NEFT CR-",
  "NEFT DR-",
  "NEFT-",
  "RTGS CR-",
  "RTGS DR-",
  "RTGS-",
  "IMPS-",
  "ACH D-",
  "ACH C-",
  "NACH-",
  "FT-",
  "POS ",
];

const LEGAL_SUFFIXES = new Set(["PVT", "PRIVATE", "LTD", "LIMITED", "LLP", "INC"]);

/** Short all-caps words such as "NPS", "PVR" or "ATM" are kept as they are. */
function titleCase(words: string[]): string {
  return words
    .map((word) =>
      word.length <= 3 && word !== "THE" && word !== "AND" ? word : word.charAt(0) + word.slice(1).toLowerCase(),
    )
    .join(" ");
}
