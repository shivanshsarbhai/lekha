import type { Account } from "../api/accounts";
import { currentMonth, isMonth, shiftMonth, type Month } from "../lib/month";
import { Icon } from "./Icon";

export function MonthPicker({ month, onChange }: { month: Month; onChange: (month: Month) => void }) {
  return (
    <div className="month-picker" role="group" aria-label="Month">
      <button type="button" className="icon-button" aria-label="Previous month" onClick={() => onChange(shiftMonth(month, -1))}>
        <Icon name="chevronLeft" />
      </button>
      <input
        type="month"
        className="month-picker__input"
        value={month}
        onChange={(event) => {
          if (isMonth(event.target.value)) onChange(event.target.value);
        }}
        aria-label="Choose month"
      />
      <button type="button" className="icon-button" aria-label="Next month" onClick={() => onChange(shiftMonth(month, 1))}>
        <Icon name="chevronRight" />
      </button>
      {month !== currentMonth() && (
        <button type="button" className="month-picker__today" onClick={() => onChange(currentMonth())}>
          Today
        </button>
      )}
    </div>
  );
}

export function AccountSelect({
  accounts,
  value,
  onChange,
}: {
  accounts: Account[];
  value: string | null;
  onChange: (accountId: string | null) => void;
}) {
  return (
    <label className="toolbar__select">
      <span className="sr-only">Account</span>
      <select
        className="input"
        value={value ?? ""}
        onChange={(event) => onChange(event.target.value === "" ? null : event.target.value)}
      >
        <option value="">All accounts</option>
        {accounts.map((account) => (
          <option key={account.id} value={account.id}>
            {account.nickname}
          </option>
        ))}
      </select>
    </label>
  );
}
