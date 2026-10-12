import type { MaterialStatus, MaterialUnit, UserRole } from "./types";

export const materialUnits: MaterialUnit[] = ["KG", "CM", "M", "MM", "LITRE", "M3", "ADET"];

export const maxRequestItems = 50;

export const materialUnitLabels = {
  KG: "KG",
  CM: "CM",
  M: "M",
  MM: "MM",
  LITRE: "Litre",
  M3: "M3",
  ADET: "Adet"
} satisfies Record<MaterialUnit, string>;

export const logisticsTabs = [
  { value: "PENDING", label: "Bekleyen" },
  { value: "PROGRESS", label: "Devam" },
  { value: "DELIVERED", label: "Tamamlanan" },
  { value: "OBJECTED", label: "İtirazlı" },
  { value: "ALL", label: "Tümü" }
] as const;

export const assignmentFilters = [
  { value: "ALL", label: "Tümü" },
  { value: "FLEET_DRIVER", label: "Filo Şoförü" },
  { value: "SPOT", label: "Spot" }
] as const;

export const roleLabels = {
  PURCHASING: "Satın Alma",
  PURCHASING_ADMIN: "Satın Alma Yönetici",
  LOGISTICS_OFFICE: "Sevkiyat",
  LOGISTICS_ADMIN: "Sevkiyat Yönetici",
  DRIVER: "Şoför",
  ADMIN: "Yönetici"
} satisfies Record<UserRole, string>;

export const terminalStatuses: MaterialStatus[] = ["DELIVERED", "CANCELLED"];

export const requestColumnPreferenceKey = "web.request.columns.v2";

export const requestColumnDefinitions = [
  { key: "requestNumber", label: "Talep No", width: "112px" },
  { key: "material", label: "Malzeme / Firma", width: "minmax(150px, 1.4fr)" },
  { key: "driver", label: "Şoför", width: "minmax(88px, 0.9fr)" },
  { key: "requestDate", label: "Talep Tarihi", width: "minmax(80px, 0.85fr)" },
  { key: "plannedDate", label: "Planlanan", width: "minmax(80px, 0.85fr)" },
  { key: "actualDate", label: "Gerçekleşen", width: "minmax(80px, 0.85fr)" },
  { key: "plate", label: "Plaka", width: "minmax(84px, 0.9fr)" },
  { key: "status", label: "Durum", width: "minmax(108px, 1fr)" },
  { key: "action", label: "İşlem", width: "72px" }
] as const;

export type RequestColumnKey = (typeof requestColumnDefinitions)[number]["key"];

// Varsayılan sırada action YOK — sadece şoför ekranı için runtime'da eklenir.
export const defaultRequestColumnOrder = requestColumnDefinitions
  .filter((column) => column.key !== "action")
  .map((column) => column.key) as RequestColumnKey[];

export const driverColumnOrder = [
  ...defaultRequestColumnOrder,
  "action"
] as RequestColumnKey[];
