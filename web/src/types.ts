export type UserRole =
  | "PURCHASING"
  | "PURCHASING_ADMIN"
  | "LOGISTICS_OFFICE"
  | "LOGISTICS_ADMIN"
  | "DRIVER"
  | "ADMIN";

export type MaterialStatus =
  | "PENDING"
  | "IN_PROGRESS"
  | "PURCHASED"
  | "ON_THE_WAY"
  | "DELIVERED"
  | "CANCELLED";

export type UrgencyLevel = "LOW" | "NORMAL" | "HIGH" | "URGENT";
export type MaterialUnit = "KG" | "CM" | "M" | "MM" | "LITRE" | "M3" | "ADET";

export type User = {
  id: string;
  username: string;
  fullName: string;
  role: UserRole;
  licensePlate?: string | null;
  isActive: boolean;
  isSuperAdmin?: boolean;
  createdAt?: number;
  updatedAt?: number;
};

export type PurchaseRequest = {
  id: string;
  requestNumber: string;
  itemToPurchase: string;
  items?: PurchaseRequestItem[];
  companyName: string;
  companyAddress: string;
  contactNumber: string;
  contactPersonName?: string | null;
  productDimensions: string;
  productWeight: string;
  packageCount?: number | null;
  urgencyLevel: UrgencyLevel;
  purchaseDate: string;
  actualPurchaseDate?: number | null;
  notes: string;
  licensePlate: string;
  spotDriverName?: string | null;
  spotDriverIdentityNumber?: string | null;
  spotDriverCompanyName?: string | null;
  spotDriverPhone?: string | null;
  deliveredTo?: string | null;
  materialStatus: MaterialStatus;
  assignedDriverId?: string | null;
  assignedDriverName?: string | null;
  assignedPurchasingStaffId: string;
  assignedPurchasingStaffName?: string | null;
  createdAt: number;
  updatedAt: number;
  cancellationReason?: string | null;
  cancelledBy?: string | null;
  objectionReason?: string | null;
  hasObjection?: boolean;
  objectionBy?: string | null;
  redirectedToRequestId?: string | null;
  isNew?: boolean;
  seenByUserIds?: string[];
};

export type PurchaseRequestItem = {
  id?: string;
  itemName: string;
  quantity: string;
  unit: MaterialUnit;
  packageCount?: number | null;
  dimensionsNote?: string | null;
  sortOrder?: number;
};

export type AppNotification = {
  id: string;
  message: string;
  timestamp: number;
  isRead: boolean;
  targetUserId: string;
  requestId?: string | null;
};

export type AuditLog = {
  id: string;
  actorUserId?: string | null;
  actorUsername?: string | null;
  actorFullName?: string | null;
  action: string;
  entityType: string;
  entityId?: string | null;
  entityLabel?: string | null;
  oldValue?: string | null;
  newValue?: string | null;
  ipAddress?: string | null;
  createdAt: number;
};

export type LoginResponse = {
  accessToken?: string | null;
  refreshToken?: string | null;
  user?: User | null;
  twoFactorRequired?: boolean;
  twoFactorSetupRequired?: boolean;
  twoFactorSecret?: string | null;
  twoFactorOtpAuthUrl?: string | null;
};

export type TokenResponse = {
  accessToken: string;
  refreshToken: string;
};

export type CreatePurchaseRequest = {
  itemToPurchase: string;
  items?: PurchaseRequestItem[];
  companyName: string;
  companyAddress?: string | null;
  contactNumber?: string | null;
  contactPersonName?: string | null;
  productDimensions?: string | null;
  productWeight?: string | null;
  packageCount?: number | null;
  urgencyLevel: UrgencyLevel;
  purchaseDate?: string | null;
  notes?: string | null;
};

export type CreateUserRequest = {
  username: string;
  fullName: string;
  password: string;
  role: UserRole;
  licensePlate?: string | null;
};

export type UpdateUserRequest = Partial<CreateUserRequest>;

export type ChangePasswordRequest = {
  currentPassword: string;
  newPassword: string;
};

export type Session = {
  accessToken: string;
  refreshToken: string;
  user: User;
};
