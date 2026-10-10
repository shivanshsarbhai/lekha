/**
 * A readable title for a bank narration: "UPI-SWIGGY-SWIGGY8@YBL-…-ORDER" (HDFC), "UPI/DR/…/SWIGGY/…" (SBI) or
 * "RSP*SWIGGYBANGALORE" (HDFC card) becomes "Swiggy". Display only; the narration itself is never changed. Falls back to the narration when nothing
 * name-like is found.
 */
export function merchantName(narration: string): string {
  const text = narration.trim().toUpperCase();
  if (/^(ATW|NWD|EAW)-/.test(text)) return "ATM withdrawal";

  const sbiUpi = /^UPI\/(?:DR|CR)\/[^/]*\/([^/]+)/.exec(text);
  if (sbiUpi?.[1] !== undefined) return nameFrom([sbiUpi[1]]) ?? narration;

  const sbiImps = /^IMPS\/[^/]*\/([^/]+)/.exec(text);
  if (sbiImps?.[1] !== undefined) return nameFrom(sbiImps[1].split("-").reverse()) ?? narration;

  if (/\bCC PAYMENT\b/.test(text)) return "Card bill payment";
  if (/^[CSI]GST-/.test(text)) return "GST";

  const withoutIds = text.replace(/[A-Z0-9._]+@[A-Z][A-Z0-9]*/g, " ");
  const prefix = PREFIXES.find((candidate) => withoutIds.startsWith(candidate));
  const rest = prefix ? withoutIds.slice(prefix.length) : cardMerchant(withoutIds);
  return nameFrom(rest.split("-")) ?? narration;
}

/**
 * HDFC card rows glue the city onto the merchant ("ACME STOREBANGALORE") and often start with a payment gateway
 * code ("RSP*", "PYU*"). Only a short list of major cities is recognised, so some names keep their city.
 */
function cardMerchant(text: string): string {
  return text.replace(CARD_GATEWAY, "").replace(CARD_CITY, "").trim();
}

const CARD_GATEWAY = /^(?:[A-Z]{2,4}\*|WWW )/;

const CARD_CITY =
  /(?:NEW DELHI|DELHI|BANGALORE|BENGALURU|MUMBAI|GURGAON|GURUGRAM|NOIDA|GAUTAM BUDDH|PUNE|HYDERABAD|CHENNAI|KOLKATA)(?: SO)?$/;

/** The first field that still has a name in it once numbers and legal suffixes are dropped. */
function nameFrom(fields: string[]): string | null {
  for (const field of fields) {
    const words = field
      .replace(/[^A-Z0-9& ]/g, " ")
      .split(/\s+/)
      .filter((word) => word !== "" && !/\d/.test(word));
    while (words.length > 0 && LEGAL_SUFFIXES.has(words.at(-1) ?? "")) words.pop();
    if (words.join("").replace(/[^A-Z]/g, "").length >= 2) return titleCase(words);
  }
  return null;
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
