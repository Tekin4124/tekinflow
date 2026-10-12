import { Plus, Trash2 } from "lucide-react";
import { materialUnitLabels, materialUnits, maxRequestItems } from "../constants";
import type { PurchaseRequestItem } from "../types";

type Props = {
  items: PurchaseRequestItem[];
  onChange: (items: PurchaseRequestItem[]) => void;
};

const emptyItem = (): PurchaseRequestItem => ({
  itemName: "",
  quantity: "1",
  unit: "KG",
  packageCount: null,
  dimensionsNote: ""
});

export function RequestItemsEditor({ items, onChange }: Props) {
  const rows = items.length ? items : [emptyItem()];
  const isAtLimit = rows.length >= maxRequestItems;

  const updateRow = (index: number, patch: Partial<PurchaseRequestItem>) => {
    onChange(rows.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)));
  };

  const removeRow = (index: number) => {
    const next = rows.filter((_, rowIndex) => rowIndex !== index);
    onChange(next.length ? next : [emptyItem()]);
  };

  return (
    <div className="items-editor">
      <div className="items-editor-head">
        <div>
          <strong>Talep Kalemleri</strong>
          <small className="items-editor-limit">
            {rows.length}/{maxRequestItems} satır
          </small>
        </div>
        <button
          type="button"
          className="secondary-button mini"
          onClick={() => onChange([...rows, emptyItem()])}
          disabled={isAtLimit}
          title={isAtLimit ? `En fazla ${maxRequestItems} satır eklenebilir` : "Satır ekle"}
        >
          <Plus size={14} />
          Satır
        </button>
      </div>
      <div className="items-table">
        {rows.map((item, index) => (
          <div className="items-row" key={index}>
            <label className="field">
              <span>Kalem</span>
              <input value={item.itemName} onChange={(event) => updateRow(index, { itemName: event.target.value })} required />
            </label>
            <label className="field">
              <span>Miktar</span>
              <input
                value={item.quantity}
                onChange={(event) => updateRow(index, { quantity: event.target.value.replace(",", ".") })}
                inputMode="decimal"
                required
              />
            </label>
            <label className="field">
              <span>Birim</span>
              <select value={item.unit} onChange={(event) => updateRow(index, { unit: event.target.value as PurchaseRequestItem["unit"] })}>
                {materialUnits.map((unit) => (
                  <option key={unit} value={unit}>
                    {materialUnitLabels[unit]}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Kap</span>
              <input
                type="number"
                value={item.packageCount ?? ""}
                onChange={(event) => updateRow(index, { packageCount: event.target.value ? Number(event.target.value) : null })}
              />
            </label>
            <label className="field">
              <span>Ölçü Notu</span>
              <input value={item.dimensionsNote ?? ""} onChange={(event) => updateRow(index, { dimensionsNote: event.target.value })} />
            </label>
            <button type="button" className="icon-button danger-text" onClick={() => removeRow(index)} disabled={rows.length === 1}>
              <Trash2 size={15} />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
