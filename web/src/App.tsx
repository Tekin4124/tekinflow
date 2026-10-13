import {
  AlertCircle,
  ArrowLeft,
  Ban,
  Bell,
  CalendarDays,
  Check,
  CheckCircle2,
  ClipboardList,
  Clock,
  Edit3,
  GripVertical,
  Inbox,
  KeyRound,
  LogOut,
  MapPin,
  Moon,
  Navigation,
  PackageCheck,
  Phone,
  Plus,
  RefreshCw,
  Search,
  Send,
  Shield,
  Sun,
  Trash2,
  Truck,
  UserPlus,
  UserRound,
  Users,
  X
} from "lucide-react";
import { type CSSProperties, type FormEvent, type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api, loadSession, saveSession, demoRoles, demoLabels } from "./api";
import {
  assignmentFilters,
  defaultRequestColumnOrder,
  driverColumnOrder,
  maxRequestItems,
  materialUnitLabels,
  requestColumnDefinitions,
  requestColumnPreferenceKey,
  terminalStatuses,
  type RequestColumnKey
} from "./constants";
import { RequestItemsEditor } from "./components/RequestItemsEditor";
import type {
  AppNotification,
  AuditLog,
  ChangePasswordRequest,
  CreatePurchaseRequest,
  CreateUserRequest,
  MaterialStatus,
  PurchaseRequest,
  PurchaseRequestItem,
  Session,
  UpdateUserRequest,
  UrgencyLevel,
  User,
  UserRole
} from "./types";

type Toast = { type: "success" | "error" | "info"; message: string } | null;
type Screen = "requests" | "detail" | "admin" | "audit";
type StatusFilter = "ALL" | "PENDING" | "PROGRESS" | "DELIVERED" | "OBJECTED" | MaterialStatus;
type AssignmentFilter = "ALL" | "FLEET_DRIVER" | "SPOT";
type DriverTab = "mine" | "open" | "completed" | "objected";

const statusMeta = {
  PENDING: { label: "Beklemede", className: "status-pending", Icon: Clock },
  IN_PROGRESS: { label: "Devam Ediyor", className: "status-progress", Icon: Truck },
  PURCHASED: { label: "Devam Ediyor", className: "status-purchased", Icon: Truck },
  ON_THE_WAY: { label: "Yolda", className: "status-way", Icon: Navigation },
  DELIVERED: { label: "Teslim Edildi", className: "status-delivered", Icon: PackageCheck },
  CANCELLED: { label: "İptal Edildi", className: "status-cancelled", Icon: Ban }
} satisfies Record<MaterialStatus, { label: string; className: string; Icon: typeof Clock }>;

function displayStatusForRequest(request: PurchaseRequest): MaterialStatus {
  if (!terminalStatuses.includes(request.materialStatus) && !hasDriverAssignment(request)) {
    return "PENDING";
  }
  return request.materialStatus;
}

const urgencyMeta = {
  LOW: { label: "Düşük", className: "urgency-low" },
  NORMAL: { label: "Normal", className: "urgency-normal" },
  HIGH: { label: "Yüksek", className: "urgency-high" },
  URGENT: { label: "Acil", className: "urgency-urgent" }
} satisfies Record<UrgencyLevel, { label: string; className: string }>;

const roleLabels = {
  PURCHASING: "Satın Alma",
  PURCHASING_ADMIN: "Satın Alma Yönetici",
  LOGISTICS_OFFICE: "Sevkiyat",
  LOGISTICS_ADMIN: "Sevkiyat Yönetici",
  DRIVER: "Şoför",
  ADMIN: "Yönetici"
} satisfies Record<UserRole, string>;

const nonAdminAssignableRoles: UserRole[] = ["PURCHASING", "PURCHASING_ADMIN", "LOGISTICS_OFFICE", "LOGISTICS_ADMIN", "DRIVER"];

const roleTitles = {
  PURCHASING: "Satın Alma Talepleri",
  PURCHASING_ADMIN: "Satın Alma Yönetimi",
  LOGISTICS_OFFICE: "Sevkiyat Yönetimi",
  LOGISTICS_ADMIN: "Sevkiyat Yönetimi",
  DRIVER: "Şoför Paneli",
  ADMIN: "Yönetim Paneli"
} satisfies Record<UserRole, string>;

const emptyRequestForm: CreatePurchaseRequest = {
  itemToPurchase: "",
  items: [{ itemName: "", quantity: "1", unit: "KG", packageCount: null, dimensionsNote: "" }],
  companyName: "",
  companyAddress: "",
  contactNumber: "",
  contactPersonName: "",
  productDimensions: "",
  productWeight: "",
  packageCount: null,
  urgencyLevel: "NORMAL",
  purchaseDate: "",
  notes: ""
};

const emptyUserForm: CreateUserRequest = {
  username: "",
  fullName: "",
  password: "",
  role: "DRIVER",
  licensePlate: ""
};

function App() {
  const [session, setSession] = useState<Session | null>(() => loadSession());
  const [requests, setRequests] = useState<PurchaseRequest[]>(() => []);
  const [users, setUsers] = useState<User[]>(() => []);
  const [auditLogs, setAuditLogs] = useState<AuditLog[] | null>(null);
  const [notifications, setNotifications] = useState<AppNotification[]>(() =>
    []
  );
  const [screen, setScreen] = useState<Screen>(() => {
    const user = loadSession()?.user;
    return user?.role === "ADMIN" ? "admin" : "requests";
  });
  const [selectedRequestId, setSelectedRequestId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [assignmentFilter, setAssignmentFilter] = useState<AssignmentFilter>("ALL");
  const [driverTab, setDriverTab] = useState<DriverTab>("mine");
  const [isDark, setIsDark] = useState(() => localStorage.getItem("TekinFlow-theme-v2") !== "light");
  const [isLoading, setIsLoading] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [toast, setToast] = useState<Toast>(null);
  const [lastSync, setLastSync] = useState<number | null>(null);
  const [requestForm, setRequestForm] = useState<PurchaseRequest | "new" | null>(null);
  const [userForm, setUserForm] = useState<User | "new" | null>(null);
  const [passwordFormOpen, setPasswordFormOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const notificationIdsRef = useRef<Set<string>>(new Set(notifications.map((item) => item.id)));
  const [requestColumnOrder, setRequestColumnOrder] = useState<RequestColumnKey[]>(defaultRequestColumnOrder);

  const currentUser = session?.user ?? null;
  const selectedRequest = requests.find((request) => request.id === selectedRequestId) ?? null;
  const drivers = users.filter((user) => user.role === "DRIVER" && user.isActive);
  const unreadCount = notifications.filter((notification) => !notification.isRead).length;

  useEffect(() => {
    document.title = currentUser ? `TekinFlow · ${roleLabels[currentUser.role]}` : "TekinFlow · Operasyon";
  }, [currentUser?.role]);

  const notify = useCallback((message: string, type: NonNullable<Toast>["type"] = "info") => {
    setToast({ message, type });
    window.setTimeout(() => setToast(null), 3200);
  }, []);

  useEffect(() => {
    if (!session) {
      setRequestColumnOrder(defaultRequestColumnOrder);
      return;
    }

    let cancelled = false;
    api
      .getPreference(requestColumnPreferenceKey)
      .then((preference) => {
        if (!cancelled) setRequestColumnOrder(readStoredColumnOrder(preference.value));
      })
      .catch(() => {
        if (!cancelled) setRequestColumnOrder(defaultRequestColumnOrder);
      });

    return () => {
      cancelled = true;
    };
  }, [session?.user.id]);

  const changeRequestColumnOrder = useCallback(
    (nextOrder: RequestColumnKey[]) => {
      const normalized = normalizeColumnOrder(nextOrder);
      setRequestColumnOrder(normalized);
      const value = JSON.stringify(normalized);

      api.putPreference(requestColumnPreferenceKey, value).catch(() => {
        notify("Sütun sırası kaydedilemedi", "error");
      });
    },
    [notify]
  );

  const refreshData = useCallback(
    async (silent = false) => {
      if (!session) return;
      if (silent) setIsRefreshing(true);
      else setIsLoading(true);

      try {
        const [requestList, notificationList] = await Promise.all([
          api.getRequests(),
          api.getNotifications().catch(() => [] as AppNotification[])
        ]);

        const knownNotificationIds = notificationIdsRef.current;
        const newNotifications = notificationList.filter((item) => !knownNotificationIds.has(item.id) && !item.isRead);
        if (silent && newNotifications.length > 0) {
          const isHidden = typeof document !== "undefined" && document.visibilityState === "hidden";
          newNotifications.forEach((item) => {
            // In-app toast (sağ alt köşede): kullanıcı sayfaya geri döndüğünde görür
            notify(item.message, "info");
            // Native bildirim: sayfa kapalı / başka sekmedeyken işletim sisteminden uyarı verir
            if (typeof Notification !== "undefined" && Notification.permission === "granted") {
              const notif = new Notification("TekinFlow", {
                body: item.message,
                tag: item.id,
                requireInteraction: isHidden
              });
              notif.onclick = () => {
                window.focus();
                notif.close();
              };
            }
          });
        }
        notificationIdsRef.current = new Set(notificationList.map((item) => item.id));
        setRequests(requestList);
        setNotifications(notificationList);
        setLastSync(Date.now());

        if (canManageUsers(session.user)) {
          const [userList, auditList] = await Promise.all([
            api.getUsers(),
            isSuperAdmin(session.user) ? api.getAuditLogs().catch(() => null as AuditLog[] | null) : Promise.resolve([] as AuditLog[])
          ]);
          setUsers(userList);
          setAuditLogs(auditList);
        } else if (isLogisticsRole(session.user.role)) {
          const driverList = await api.getDrivers().catch(() => [] as User[]);
          setUsers(driverList);
          setAuditLogs([]);
        }
      } catch (error) {
        notify(error instanceof Error ? error.message : "Veriler alnamad", "error");
        if ((error as Error)?.message?.includes("Oturum")) {
          setSession(null);
        }
      } finally {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    },
    [notify, session]
  );

  useEffect(() => {
    document.documentElement.classList.toggle("dark", isDark);
    localStorage.setItem("TekinFlow-theme-v2", isDark ? "dark" : "light");
  }, [isDark]);

  useEffect(() => {
    if (!session) return;
    void refreshData();
    const timer = window.setInterval(() => void refreshData(true), 30_000);
    return () => window.clearInterval(timer);
  }, [refreshData, session]);

  const filteredRequests = useMemo(() => {
    const lowerQuery = query.trim().toLowerCase();

    return requests.filter((request) => {
      if (currentUser?.role === "DRIVER") {
        const isMine = request.assignedDriverId === currentUser.id;
        const isOpen = canDriverTakeOver(request);
        if (driverTab === "mine" && !isMine) return false;
        if (driverTab === "open" && !isOpen) return false;
        if (driverTab === "completed" && !(isMine && request.materialStatus === "DELIVERED")) return false;
        if (driverTab === "objected" && !(isMine && request.hasObjection)) {
          return false;
        }
      } else {
        const isActive = !terminalStatuses.includes(request.materialStatus);
        if (statusFilter === "PENDING" && !(isActive && !hasDriverAssignment(request))) return false;
        if (statusFilter === "PROGRESS" && !(isActive && hasDriverAssignment(request))) return false;
        if (statusFilter === "DELIVERED" && request.materialStatus !== "DELIVERED") return false;
        if (statusFilter === "OBJECTED" && !request.hasObjection) return false;
        if (
          statusFilter !== "ALL" &&
          !["PENDING", "PROGRESS", "DELIVERED", "OBJECTED"].includes(statusFilter) &&
          request.materialStatus !== statusFilter
        ) {
          return false;
        }
        if (assignmentFilter === "FLEET_DRIVER" && !isFleetDriverAssignment(request)) return false;
        if (assignmentFilter === "SPOT" && !isSpotDriverAssignment(request)) return false;
      }

      if (!lowerQuery) return true;
      return [
        request.requestNumber,
        requestItemsText(request),
        request.companyName,
        request.companyAddress,
        request.licensePlate,
        request.spotDriverName,
        request.spotDriverCompanyName
      ]
        .join(" ")
        .toLowerCase()
        .includes(lowerQuery);
    });
  }, [assignmentFilter, currentUser?.id, currentUser?.role, driverTab, query, requests, statusFilter]);

  const updateLocalRequest = (updated: PurchaseRequest) => {
    setRequests((items) => items.map((item) => (item.id === updated.id ? updated : item)));
    setSelectedRequestId(updated.id);
  };

  const runAction = async (key: string, action: () => Promise<PurchaseRequest | void>, success: string) => {
    setBusyKey(key);
    try {
      const updated = await action();
      if (updated) updateLocalRequest(updated);
      notify(success, "success");
      if (screen === "detail") setScreen("requests");
      void refreshData(true);
    } catch (error) {
      notify(error instanceof Error ? error.message : "İşlem yapılamadı", "error");
    } finally {
      setBusyKey(null);
    }
  };

  const deleteRequest = (request: PurchaseRequest) =>
    runAction(
      `delete-request-${request.id}`,
      async () => {
        await api.deleteRequest(request.id);
        setRequests((items) => items.filter((item) => item.id !== request.id));
        if (selectedRequestId === request.id) {
          setSelectedRequestId(null);
          setScreen("requests");
        }
      },
      "Talep kalıcı olarak silindi"
    );

  const clearObjection = (request: PurchaseRequest) =>
    runAction(
      `clear-objection-${request.id}`,
      () => api.clearObjection(request.id),
      "İtiraz silindi"
    );

  const openRequest = async (request: PurchaseRequest) => {
    setSelectedRequestId(request.id);
    setScreen("detail");
    if (!request.seenByUserIds?.includes(currentUser?.id ?? "")) {
      api.markSeen(request.id).catch(() => undefined);
    }
  };

  const handleLogout = async () => {
    await api.logout();
    setSession(null);
    setRequests([]);
    setUsers([]);
    setAuditLogs([]);
    setNotifications([]);
    setScreen("requests");
  };

  const openNotifications = async () => {
    setNotificationsOpen((value) => !value);
    const unread = notifications.filter((notification) => !notification.isRead);
    if (unread.length === 0) return;
    setNotifications((items) => items.map((item) => ({ ...item, isRead: true })));
    await Promise.all(unread.map((item) => api.readNotification(item.id).catch(() => undefined)));
  };

  const jumpFromNotification = (notification: AppNotification) => {
    const directTarget = notification.requestId
      ? requests.find((request) => request.id === notification.requestId)
      : null;
    if (directTarget) {
      void openRequest(directTarget);
      setNotificationsOpen(false);
      return;
    }

    const requestNumber = notification.message.match(/TLP-\d{4}-\d+/)?.[0];
    const target = requestNumber
      ? requests.find((request) => request.requestNumber === requestNumber)
      : null;
    if (target) void openRequest(target);
    else notify("Bu bildirime ait talep listede bulunamad", "info");
    setNotificationsOpen(false);
  };

  if (!session || !currentUser) {
    return (
      <LoginPage
        isDark={isDark}
        onToggleTheme={() => setIsDark((value) => !value)}
        onLogin={(nextSession) => {
          saveSession(nextSession);
          setSession(nextSession);
          setScreen(isSuperAdmin(nextSession.user) ? "admin" : "requests");
        }}
      />
    );
  }

  const activeRequestCount = requests.filter(
    (request) => request.hasObjection || !["DELIVERED", "CANCELLED"].includes(request.materialStatus)
  ).length;

  return (
    <div className={`app-shell ${isLogisticsRole(currentUser.role) ? "logistics-shell" : ""}`}>
      <TopBar
        user={currentUser}
        isDark={isDark}
        unreadCount={unreadCount}
        lastSync={lastSync}
        notificationsOpen={notificationsOpen}
        notifications={notifications}
        onToggleTheme={() => setIsDark((value) => !value)}
        onRefresh={() => void refreshData(true)}
        onLogout={handleLogout}
        onOpenPassword={() => setPasswordFormOpen(true)}
        onOpenNotifications={openNotifications}
        onNotificationClick={jumpFromNotification}
        isRefreshing={isRefreshing}
      />


      <nav className="demo-controls" aria-label="Çalışma alanı"><span>ÖRNEK VERİ</span><select aria-label="Rol" value={currentUser.role} onChange={event=>{
        const next = api.selectRole(event.target.value as UserRole);
        saveSession(next); setSession(next); setScreen(isSuperAdmin(next.user) ? "admin" : "requests");
        setSelectedRequestId(null); setQuery(""); setStatusFilter("ALL"); setAssignmentFilter("ALL");
        setUsers([]); setNotifications([]); setNotificationsOpen(false); notificationIdsRef.current = new Set();
        window.history.replaceState(null, "", `?role=${next.user.role}`);
      }}>{demoRoles.map((role,index)=><option key={role} value={role}>{demoLabels[index]}</option>)}</select><span>Veriler yalnız bu tarayıcıda saklanır</span><button onClick={()=>{if(window.confirm("Örnek veriler sıfırlansın mı?")){api.resetDemo();window.location.reload();}}}>Örnek verileri sıfırla</button><a className="apk-link" href="https://github.com/Tekin4124/tekinflow/releases/latest/download/TekinFlow.apk">Android APK indir ↗</a></nav>
      <main className="main-grid">
        <aside className="side-rail">
          <div
            className={screen === "requests" || screen === "detail" ? "rail-card active" : "rail-card"}
            onClick={() => setScreen("requests")}
          >
            <ClipboardList size={18} />
            <span>Talepler</span>
          </div>
          {canManageUsers(currentUser) && (
            <div
              className={screen === "admin" ? "rail-card active" : "rail-card"}
              onClick={() => setScreen("admin")}
            >
              <Users size={18} />
              <span>Kullanıcılar</span>
            </div>
          )}
          {isSuperAdmin(currentUser) && (
            <div
              className={screen === "audit" ? "rail-card active" : "rail-card"}
              onClick={() => setScreen("audit")}
            >
              <Shield size={18} />
              <span>İşlem Geçmişi</span>
            </div>
          )}
          <div className="rail-divider" />
          <div className="summary-card">
            <span>Aktif Talep</span>
            <strong>{activeRequestCount}</strong>
          </div>
          <div className="summary-card">
            <span>Bildirim</span>
            <strong>{unreadCount}</strong>
          </div>
        </aside>

        <section className="content-card">
          {screen === "admin" ? (
            <AdminPanel
              currentUser={currentUser}
              users={users}
              busyKey={busyKey}
              onNew={() => setUserForm("new")}
              onEdit={setUserForm}
              onDeactivate={(user) =>
                runAction(
                  `delete-user-${user.id}`,
                  async () => {
                    await api.deleteUser(user.id);
                    setUsers((items) => items.map((item) => (item.id === user.id ? { ...item, isActive: false } : item)));
                  },
                  "Kullanıcı pasifleştirildi"
                )
              }
              onActivate={(user) =>
                runAction(
                  `activate-user-${user.id}`,
                  async () => {
                    const updated = await api.activateUser(user.id);
                    setUsers((items) => items.map((item) => (item.id === updated.id ? updated : item)));
                  },
                  "Kullanıcı aktif edildi"
                )
              }
              onRemove={(user) =>
                runAction(
                  `remove-user-${user.id}`,
                  async () => {
                    await api.deleteUserPermanently(user.id);
                    setUsers((items) => items.filter((item) => item.id !== user.id));
                  },
                  "Kullanıcı kalıcı olarak silindi"
                )
              }
            />
          ) : screen === "audit" ? (
            <AuditPanel logs={auditLogs} />
          ) : screen === "detail" && selectedRequest ? (
            <RequestDetail
              request={selectedRequest}
              user={currentUser}
              drivers={drivers}
              busyKey={busyKey}
              onBack={() => setScreen("requests")}
              onEdit={() => setRequestForm(selectedRequest)}
              onCancel={(reason) =>
                runAction(
                  `cancel-${selectedRequest.id}`,
                  () => api.cancelRequest(selectedRequest.id, reason),
                  "Talep iptal edildi"
                )
              }
              onAssign={(driver) =>
                runAction(
                  `assign-${selectedRequest.id}`,
                  () => api.assignDriver(selectedRequest.id, driver.id, driver.licensePlate),
                  "Şoför atandı"
                )
              }
              onAssignSpot={(payload) =>
                runAction(
                  `assign-${selectedRequest.id}`,
                  () => api.assignSpotDriver(selectedRequest.id, payload),
                  "Spot Şoför bilgisi kaydedildi"
                )
              }
              onUnassign={() =>
                runAction(
                  `unassign-${selectedRequest.id}`,
                  () => api.unassignDriver(selectedRequest.id),
                  "Şoför ataması kaldırıldı"
                )
              }
              onTakeOver={() =>
                runAction(
                  `take-${selectedRequest.id}`,
                  () => api.takeOver(selectedRequest.id),
                  "Talep üstlenildi"
                )
              }
              onStatus={(status, deliveredTo) =>
                runAction(
                  `status-${selectedRequest.id}-${status}`,
                  () => api.updateStatus(selectedRequest.id, status, deliveredTo),
                  "Durum güncellendi"
                )
              }
              onObjection={(reason) =>
                runAction(
                  `object-${selectedRequest.id}`,
                  () => api.submitObjection(selectedRequest.id, reason),
                  "İtiraz gönderildi"
                )
              }
              onClearObjection={() => clearObjection(selectedRequest)}
              onDelete={() => deleteRequest(selectedRequest)}
              driverName={(id) => users.find((user) => user.id === id)?.fullName ?? "Atanan Şoför"}
            />
          ) : (
            <RequestList
              user={currentUser}
              requests={filteredRequests}
              allRequests={requests}
              query={query}
              statusFilter={statusFilter}
              assignmentFilter={assignmentFilter}
              requestColumnOrder={requestColumnOrder}
              driverTab={driverTab}
              isLoading={isLoading}
              busyKey={busyKey}
              onQuery={setQuery}
              onStatusFilter={setStatusFilter}
              onAssignmentFilter={setAssignmentFilter}
              onRequestColumnOrder={changeRequestColumnOrder}
              onDriverTab={setDriverTab}
              onOpen={openRequest}
              onNew={() => setRequestForm("new")}
              driverName={(id) => users.find((user) => user.id === id)?.fullName ?? "Atanan Şoför"}
              onTakeOver={(request) =>
                runAction(`take-${request.id}`, () => api.takeOver(request.id), "Talep üstlenildi")
              }
              onDelete={deleteRequest}
            />
          )}
        </section>
      </main>

      {requestForm && (
        <RequestFormModal
          request={requestForm === "new" ? null : requestForm}
          onClose={() => setRequestForm(null)}
          onSubmit={async (payload) => {
            try {
              const saved =
                requestForm === "new"
                  ? await api.createRequest(payload)
                  : await api.updateRequest(requestForm.id, payload);
              setRequests((items) =>
                requestForm === "new"
                  ? [saved, ...items]
                  : items.map((item) => (item.id === saved.id ? saved : item))
              );
              setRequestForm(null);
              setSelectedRequestId(saved.id);
              setScreen("detail");
              notify(requestForm === "new" ? "Talep oluşturuldu" : "Talep güncellendi", "success");
            } catch (error) {
              notify(error instanceof Error ? error.message : "Talep kaydedilemedi", "error");
            }
          }}
        />
      )}

      {userForm && (
        <UserFormModal
          user={userForm === "new" ? null : userForm}
          currentUser={currentUser}
          onClose={() => setUserForm(null)}
          onSubmit={async (payload) => {
            try {
              const saved =
                userForm === "new"
                  ? await api.createUser(payload as CreateUserRequest)
                  : await api.updateUser(userForm.id, payload as UpdateUserRequest);
              setUsers((items) =>
                userForm === "new"
                  ? [saved, ...items]
                  : items.map((item) => (item.id === saved.id ? saved : item))
              );
              setUserForm(null);
              notify(userForm === "new" ? "Kullanıcı oluşturuldu" : "Kullanıcı güncellendi", "success");
            } catch (error) {
              notify(error instanceof Error ? error.message : "Kullanıcı kaydedilemedi", "error");
            }
          }}
        />
      )}

      {passwordFormOpen && (
        <ChangePasswordModal
          onClose={() => setPasswordFormOpen(false)}
          onSubmit={async (payload) => {
            await api.changePassword(payload);
            window.alert("Şifre değiştirildi. Lütfen yeniden giriş yap.");
            setPasswordFormOpen(false);
            saveSession(null);
            setSession(null);
          }}
        />
      )}

      {toast && (
        <div className={`toast toast-${toast.type}`}>
          {toast.type === "success" ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
          {toast.message}
        </div>
      )}
    </div>
  );
}

function TekinFlowLogo({ variant = "dark", size = "md" }: { variant?: "dark" | "light"; size?: "sm" | "md" | "lg" }) {
  return <div className={`tf-logo tf-logo-${variant} tf-logo-${size}`} role="img" aria-label="TekinFlow"><img src="./tekinflow.svg" width="42" height="42" alt=""/><div><strong>TEKINFLOW</strong><small>SATIN ALMA VE SEVKİYAT</small></div></div>;
}

const avatarPalettes = [
  { bg: "#f6d9de", mark: "#0b8b83", line: "#0c1f3a" },
  { bg: "#e3e6eb", mark: "#0c1f3a", line: "#0b8b83" },
  { bg: "#f0d5cf", mark: "#a33a2f", line: "#0c1f3a" },
  { bg: "#d9e1ec", mark: "#254260", line: "#0b8b83" },
  { bg: "#eee4d9", mark: "#7b4b2a", line: "#0b8b83" },
  { bg: "#dce6df", mark: "#2c5d48", line: "#0c1f3a" }
];

function ProfileAvatar({ user, size = "md" }: { user: User; size?: "sm" | "md" }) {
  const seed = hashString(`${user.id}-${user.username}`);
  const palette = avatarPalettes[seed % avatarPalettes.length];
  const style = {
    "--avatar-bg": palette.bg,
    "--avatar-mark": palette.mark,
    "--avatar-line": palette.line
  } as CSSProperties;

  return (
    <span className={`profile-avatar profile-avatar-${size}`} style={style} aria-label={`${user.fullName} profil avatari`}>
      <span>{getInitials(user.fullName)}</span>
    </span>
  );
}

function getInitials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  return parts
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toLocaleUpperCase("tr-TR");
}

function hashString(value: string) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) >>> 0;
  }
  return hash;
}

function isSuperAdmin(user: User) {
  return Boolean(user.isSuperAdmin);
}

function canManageUsers(user: User) {
  return isSuperAdmin(user) || user.role === "PURCHASING_ADMIN" || user.role === "LOGISTICS_ADMIN";
}


function assignableRolesForUser(user: User): UserRole[] {
  if (isSuperAdmin(user)) return nonAdminAssignableRoles;
  if (user.role === "PURCHASING_ADMIN") return ["PURCHASING"];
  if (user.role === "LOGISTICS_ADMIN") return ["LOGISTICS_OFFICE", "DRIVER"];
  return [];
}

function canManageUserRecord(currentUser: User, target: User) {
  if (isSuperAdmin(currentUser)) return true;
  if (currentUser.id === target.id || target.isSuperAdmin || target.role === "ADMIN") return false;
  return assignableRolesForUser(currentUser).includes(target.role);
}

function isPurchasingRole(role: UserRole) {
  return role === "PURCHASING" || role === "PURCHASING_ADMIN";
}

function canManagePurchasing(user: User) {
  return user.role === "PURCHASING_ADMIN" || isSuperAdmin(user);
}

function isLogisticsRole(role: UserRole) {
  return role === "LOGISTICS_OFFICE" || role === "LOGISTICS_ADMIN";
}

function canManageLogistics(user: User) {
  return isLogisticsRole(user.role) || isSuperAdmin(user);
}

function hasText(value?: string | null) {
  return Boolean(value?.trim());
}

function isFleetDriverAssignment(request: PurchaseRequest) {
  return Boolean(request.assignedDriverId);
}

function isSpotDriverAssignment(request: PurchaseRequest) {
  return !isFleetDriverAssignment(request) && [
    request.licensePlate,
    request.spotDriverName,
    request.spotDriverIdentityNumber,
    request.spotDriverCompanyName
  ].some(hasText);
}

function hasDriverAssignment(request: PurchaseRequest) {
  return isFleetDriverAssignment(request) || isSpotDriverAssignment(request);
}

function hasReadyTransportAssignment(request: PurchaseRequest) {
  if (!hasText(request.licensePlate)) return false;
  if (isFleetDriverAssignment(request)) return true;
  return (
    hasText(request.spotDriverName) &&
    hasText(request.spotDriverIdentityNumber) &&
    hasText(request.spotDriverCompanyName) &&
    hasText(request.spotDriverPhone)
  );
}

function canDriverTakeOver(request: PurchaseRequest) {
  return !hasDriverAssignment(request) && !["DELIVERED", "CANCELLED"].includes(request.materialStatus);
}

function assignmentDriverLabel(request: PurchaseRequest, driverName: (id?: string | null) => string) {
  if (request.assignedDriverId) {
    return request.assignedDriverName?.trim() || driverName(request.assignedDriverId);
  }
  if (isSpotDriverAssignment(request)) {
    return request.spotDriverName?.trim() || request.spotDriverCompanyName?.trim() || "Spot Şoför";
  }
  return "—";
}

function LoginPage({isDark,onToggleTheme,onLogin}:{isDark:boolean;onToggleTheme:()=>void;onLogin:(session:Session)=>void}) {
  const icons = [ClipboardList, Truck, Navigation, Shield];
  const descriptions = ["Talep ve tedarikçi takibi", "Şoför atama ve sevkiyat takibi", "Atanan işler ve teslimatlar", "Kullanıcılar ve işlem geçmişi"];
  return <main className="demo-login-shell">
    <section className="demo-login-card">
      <header className="demo-login-head"><TekinFlowLogo size="lg"/>
        <button className="ghost-button compact" onClick={onToggleTheme} aria-label={isDark ? "Açık temaya geç" : "Koyu temaya geç"}>
          {isDark ? <Sun size={18}/> : <Moon size={18}/>}<span>{isDark ? "Açık tema" : "Koyu tema"}</span>
        </button>
      </header>
      <div className="demo-login-body">
        <div className="demo-login-intro">
          <span className="demo-entry-label">ÇALIŞMA ALANLARI</span>
          <h1>Çalışma alanını seç</h1>
          <p>Bir rol seçerek devam et.</p>
          <div className="demo-entry-flow" aria-label="İş akışı"><span>Talep</span><span aria-hidden="true">→</span><span>Atama</span><span aria-hidden="true">→</span><span>Teslimat</span></div>
        </div>
        <section className="demo-role-panel" aria-label="Roller">
          <div className="demo-role-panel-head"><span>ROLLER</span><span>4 rol</span></div>
          <div className="demo-role-grid">{demoRoles.map((role,index)=>{
        const Icon = icons[index];
        return <button className="demo-role-card" key={role} onClick={()=>onLogin(api.selectRole(role))}>
          <span className="demo-role-icon"><Icon size={24}/></span>
          <span className="demo-role-copy"><strong>{demoLabels[index]}</strong><small>{descriptions[index]}</small></span>
          <span className="demo-role-arrow" aria-hidden="true">→</span>
        </button>;
          })}</div>
        </section>
      </div>
      <footer className="demo-login-foot"><span><Shield size={14}/> Örnek veriler</span><span>Hesap gerekmez</span><a className="apk-link" href="https://github.com/Tekin4124/tekinflow/releases/latest/download/TekinFlow.apk">Android APK indir ↗</a></footer>
    </section>
  </main>;
}

function TopBar({
  user,
  isDark,
  unreadCount,
  lastSync,
  notificationsOpen,
  notifications,
  onToggleTheme,
  onRefresh,
  onLogout,
  onOpenPassword,
  onOpenNotifications,
  onNotificationClick,
  isRefreshing
}: {
  user: User;
  isDark: boolean;
  unreadCount: number;
  lastSync: number | null;
  notificationsOpen: boolean;
  notifications: AppNotification[];
  onToggleTheme: () => void;
  onRefresh: () => void;
  onLogout: () => void;
  onOpenPassword: () => void;
  onOpenNotifications: () => void;
  onNotificationClick: (notification: AppNotification) => void;
  isRefreshing: boolean;
}) {
  return (
    <header className="topbar">
      <div className="brand-lockup">
        <TekinFlowLogo variant="dark" size="sm" />
        <div className="topbar-divider" aria-hidden="true" />
        <div className="topbar-title">
          <h1>Satın Alma ve Sevkiyat</h1>
          <span className="sync-text">
            {roleLabels[user.role]} ·{" "}
            {lastSync ? `Son güncelleme ${formatTime(lastSync)}` : "Veriler hazırlanıyor"}
          </span>
        </div>
      </div>

      <div className="top-actions">
        <button className="icon-button" onClick={onRefresh} aria-label="Yenile">
          <RefreshCw size={19} className={isRefreshing ? "spin" : ""} />
        </button>
        <button className="icon-button" onClick={onToggleTheme} aria-label="Tema">
          {isDark ? <Sun size={19} /> : <Moon size={19} />}
        </button>
        <div className="notification-anchor">
          <button className="icon-button bell-button" onClick={onOpenNotifications} aria-label="Bildirimler">
            <Bell size={20} />
            {unreadCount > 0 && <span className="unread-dot">{unreadCount}</span>}
          </button>
          {notificationsOpen && (
            <div className="notification-popover">
              <div className="popover-head">
                <strong>Bildirimler</strong>
                <span>{notifications.length} kayt</span>
              </div>
              {notifications.length === 0 ? (
                <div className="empty-small">
                  <Inbox size={22} />
                  Bildirim yok
                </div>
              ) : (
                notifications.slice(0, 8).map((notification) => (
                  <button
                    key={notification.id}
                    className="notification-row"
                    onClick={() => onNotificationClick(notification)}
                  >
                    <span>{notification.message}</span>
                    <small>{formatTime(notification.timestamp)}</small>
                  </button>
                ))
              )}
            </div>
          )}
        </div>
        <div className="profile-chip">
          <ProfileAvatar user={user} size="sm" />
          <span>{user.fullName}</span>
        </div>

        <button className="ghost-button danger-text" onClick={onLogout}>
          <LogOut size={18} />
          çıkış
        </button>
      </div>
    </header>
  );
}

function RequestList({
  user,
  requests,
  allRequests,
  query,
  statusFilter,
  assignmentFilter,
  requestColumnOrder,
  driverTab,
  isLoading,
  busyKey,
  onQuery,
  onStatusFilter,
  onAssignmentFilter,
  onRequestColumnOrder,
  onDriverTab,
  onOpen,
  onNew,
  driverName,
  onTakeOver,
  onDelete
}: {
  user: User;
  requests: PurchaseRequest[];
  allRequests: PurchaseRequest[];
  query: string;
  statusFilter: StatusFilter;
  assignmentFilter: AssignmentFilter;
  requestColumnOrder: RequestColumnKey[];
  driverTab: DriverTab;
  isLoading: boolean;
  busyKey: string | null;
  onQuery: (value: string) => void;
  onStatusFilter: (value: StatusFilter) => void;
  onAssignmentFilter: (value: AssignmentFilter) => void;
  onRequestColumnOrder: (value: RequestColumnKey[]) => void;
  onDriverTab: (value: DriverTab) => void;
  onOpen: (request: PurchaseRequest) => void;
  onNew: () => void;
  driverName: (id?: string | null) => string;
  onTakeOver: (request: PurchaseRequest) => void;
  onDelete: (request: PurchaseRequest) => void;
}) {
  const isDriver = user.role === "DRIVER";
  const isLogistics = isLogisticsRole(user.role);
  const showActionColumn = isDriver || isSuperAdmin(user);
  const visibleColumnOrder = showActionColumn ? driverColumnOrder : normalizeColumnOrder(requestColumnOrder);
  const requestGridStyle = {
    "--request-grid-template": requestGridTemplate(visibleColumnOrder)
  } as CSSProperties;

  return (
    <>
      <div className="section-head">
        <div>
          <p className="eyebrow">{isLogistics ? "OPERASYON MERKEZİ" : "Talep Yönetimi"}</p>
          <h2>{isLogistics ? "Sevkiyat kontrol paneli" : roleTitles[user.role]}</h2>
          {isLogistics && <p className="muted">Atama bekleyen talepleri planla, yoldaki araçları ve tamamlanan teslimatları takip et.</p>}
        </div>
        {(isPurchasingRole(user.role) || isSuperAdmin(user)) && (
          <button className="primary-button compact" onClick={onNew}>
            <Plus size={18} />
            Yeni Talep
          </button>
        )}
      </div>

      {!isDriver && (
        <div className="stats-strip">
          <StatCard
            label={isLogistics ? "Atama bekliyor" : "Bekleyen"}
            value={countPending(allRequests)}
            tone="info"
            selected={statusFilter === "PENDING"}
            onClick={() => onStatusFilter(statusFilter === "PENDING" ? "ALL" : "PENDING")}
          />
          <StatCard
            label="Devam"
            value={countInProgress(allRequests)}
            tone="orange"
            selected={statusFilter === "PROGRESS"}
            onClick={() => onStatusFilter(statusFilter === "PROGRESS" ? "ALL" : "PROGRESS")}
          />
          <StatCard
            label="Tamamlanan"
            value={countByStatus(allRequests, "DELIVERED")}
            tone="success"
            selected={statusFilter === "DELIVERED"}
            onClick={() => onStatusFilter(statusFilter === "DELIVERED" ? "ALL" : "DELIVERED")}
          />
          <StatCard
            label="İtirazlı"
            value={countObjected(allRequests)}
            tone="warning"
            selected={statusFilter === "OBJECTED"}
            onClick={() => onStatusFilter(statusFilter === "OBJECTED" ? "ALL" : "OBJECTED")}
          />
          <StatCard
            label="Tümü"
            value={allRequests.length}
            tone="info"
            selected={statusFilter === "ALL"}
            onClick={() => onStatusFilter("ALL")}
          />
        </div>
      )}

      <div className="toolbar">
        <label className="search-field">
          <Search size={18} />
          <input
            aria-label="Taleplerde ara"
            placeholder={isLogistics ? "Talep no, firma, malzeme veya plaka ara…" : "Talep, firma, ürün veya plaka ara"}
            value={query}
            onChange={(event) => onQuery(event.target.value)}
          />
        </label>
      </div>

      {isLogisticsRole(user.role) && (
        <div className="logistics-filter-bar">
          <div className="chip-row">
          {assignmentFilters.map((filter) => (
            <button
              key={filter.value}
              className={assignmentFilter === filter.value ? "filter-chip selected" : "filter-chip"}
              onClick={() => onAssignmentFilter(filter.value)}
            >
              {filter.label}
            </button>
          ))}
          <button className={statusFilter === "ON_THE_WAY" ? "filter-chip selected" : "filter-chip"}
            onClick={() => onStatusFilter(statusFilter === "ON_THE_WAY" ? "ALL" : "ON_THE_WAY")}>
            <Navigation size={15}/> Yolda · {countByStatus(allRequests, "ON_THE_WAY")}
          </button>
          </div>
          <div className="logistics-result-count"><span>{requests.length} sevkiyat</span>
            {(query || statusFilter !== "ALL" || assignmentFilter !== "ALL") && <button className="ghost-button compact"
              onClick={() => { onQuery(""); onStatusFilter("ALL"); onAssignmentFilter("ALL"); }}>Filtreleri temizle</button>}
          </div>
        </div>
      )}

      {user.role === "DRIVER" && (
        <div className="segmented">
          <button className={driverTab === "mine" ? "active" : ""} onClick={() => onDriverTab("mine")}>
            Bana Atanan
          </button>
          <button className={driverTab === "open" ? "active" : ""} onClick={() => onDriverTab("open")}>
            Atanmamış
          </button>
          <button className={driverTab === "completed" ? "active" : ""} onClick={() => onDriverTab("completed")}>
            Tamamlanan
          </button>
          <button className={driverTab === "objected" ? "active" : ""} onClick={() => onDriverTab("objected")}>
            İtirazlı
          </button>
        </div>
      )}

      <div className="request-list" style={requestGridStyle}>
        {!isLoading && requests.length > 0 && (
          isDriver ? (
            <StaticHead order={visibleColumnOrder} />
          ) : (
            <DraggableHead
              order={visibleColumnOrder}
              onChange={onRequestColumnOrder}
              lockedColumns={showActionColumn ? ["action"] : []}
            />
          )
        )}
        {isLoading ? (
          Array.from({ length: 6 }).map((_, index) => <div className="skeleton-card" key={index} />)
        ) : requests.length === 0 ? (
          <div className="empty-state">
            <Inbox size={28} />
            <strong>Kayıt bulunamadı</strong>
            <span>Arama veya filtrelerini gözden geçir.</span>
          </div>
        ) : (
          requests.map((request) => (
            <RequestCard
              key={request.id}
              request={request}
              user={user}
              driverName={driverName}
              columnOrder={visibleColumnOrder}
              busyKey={busyKey}
              onOpen={() => onOpen(request)}
              onTakeOver={() => onTakeOver(request)}
              onDelete={() => onDelete(request)}
            />
          ))
        )}
      </div>
    </>
  );
}

function StaticHead({ order }: { order: RequestColumnKey[] }) {
  return (
    <div className="request-list-head">
      <span></span>
      {order.map((columnKey) => {
        const definition = columnDefinition(columnKey);
        return (
          <span className={`col-${columnKey}`} key={columnKey}>
            {definition.label}
          </span>
        );
      })}
    </div>
  );
}

function DraggableHead({
  order,
  onChange,
  lockedColumns = []
}: {
  order: RequestColumnKey[];
  onChange: (value: RequestColumnKey[]) => void;
  lockedColumns?: RequestColumnKey[];
}) {
  const [dragging, setDragging] = useState<RequestColumnKey | null>(null);
  const isLocked = (key: RequestColumnKey) => lockedColumns.includes(key);

  const moveColumn = (target: RequestColumnKey) => {
    if (!dragging || dragging === target || isLocked(target) || isLocked(dragging)) return;
    const nextOrder = order.filter((column) => column !== dragging);
    const targetIndex = nextOrder.indexOf(target);
    nextOrder.splice(targetIndex, 0, dragging);
    onChange(nextOrder);
    setDragging(null);
  };

  return (
    <div className="request-list-head">
      <span></span>
      {order.map((columnKey) => {
        const definition = columnDefinition(columnKey);
        const locked = isLocked(columnKey);
        return (
          <span
            className={`col-${columnKey}${locked ? "" : " draggable-th"}${dragging === columnKey ? " dragging" : ""}`}
            key={columnKey}
            draggable={!locked}
            onDragStart={locked ? undefined : () => setDragging(columnKey)}
            onDragEnd={locked ? undefined : () => setDragging(null)}
            onDragOver={locked ? undefined : (event) => event.preventDefault()}
            onDrop={locked ? undefined : () => moveColumn(columnKey)}
            title={locked ? undefined : "Sürükleyerek sütun sırasını değiştir"}
          >
            {!locked && <GripVertical size={12} className="th-grip" />}
            {definition.label}
          </span>
        );
      })}
    </div>
  );
}

function RequestCard({
  request,
  user,
  driverName,
  columnOrder,
  busyKey,
  onOpen,
  onTakeOver,
  onDelete
}: {
  request: PurchaseRequest;
  user: User;
  driverName: (id?: string | null) => string;
  columnOrder: RequestColumnKey[];
  busyKey: string | null;
  onOpen: () => void;
  onTakeOver: () => void;
  onDelete: () => void;
}) {
  const displayStatus = displayStatusForRequest(request);
  const StatusIcon = statusMeta[displayStatus].Icon;
  const canTake = user.role === "DRIVER" && canDriverTakeOver(request);
  const driverLabel = assignmentDriverLabel(request, driverName);
  const plate = request.licensePlate?.trim() || "";
  const cells = {
    requestNumber: (
      <div className="request-id">
        {request.requestNumber}
        <small>{urgencyMeta[request.urgencyLevel].label}</small>
      </div>
    ),
    material: (
      <div className="request-item">
        <small className="request-item-company">{request.companyName}</small>
        <strong>{requestItemsPreview(request)}</strong>
      </div>
    ),
    driver: <div className="cell cell-driver cell-muted">{driverLabel}</div>,
    requestDate: <DateTimeCell value={request.createdAt} />,
    plannedDate: <div className="cell cell-date cell-muted">{formatPlannedTr(request.purchaseDate)}</div>,
    actualDate: <DateTimeCell value={request.actualPurchaseDate} />,
    plate: (
      <div className="cell">
        {plate ? (
          <span className="cell-plate">{plate}</span>
        ) : isSpotDriverAssignment(request) ? (
          <span className="cell-plate empty">Spot</span>
        ) : (
          <span className="cell-plate empty">Atanmamış</span>
        )}
      </div>
    ),
    status: (
      <div className="cell cell-status">
        <span className={`status-badge ${statusMeta[displayStatus].className} ${request.hasObjection ? "status-objection" : ""}`}>
          <StatusIcon />
          {statusMeta[displayStatus].label}
        </span>
        {request.hasObjection && <span className="objection-pill">İtirazlı</span>}
      </div>
    ),
    action: (
      <div className="cell cell-action">
        {isSuperAdmin(user) ? (
          <button
            className="danger-button mini"
            onClick={(event) => {
              event.stopPropagation();
              onDelete();
            }}
            disabled={busyKey === `delete-request-${request.id}`}
          >
            Sil
          </button>
        ) : canTake ? (
          <button
            className="primary-button mini"
            onClick={(event) => {
              event.stopPropagation();
              onTakeOver();
            }}
            disabled={busyKey === `take-${request.id}`}
          >
            Üstlen
          </button>
        ) : null}
      </div>
    )
  } satisfies Record<RequestColumnKey, ReactNode>;

  return (
    <article className="request-card" onClick={onOpen} role="button" tabIndex={0}
      aria-label={`${request.requestNumber} talep detayını aç`}
      onKeyDown={event => {
        if (event.target === event.currentTarget && (event.key === "Enter" || event.key === " ")) {
          event.preventDefault(); onOpen();
        }
      }}>
      <span
        className={`urgency-dot ${urgencyMeta[request.urgencyLevel].className}`}
        title={urgencyMeta[request.urgencyLevel].label}
      />

      {columnOrder.map((columnKey) => (
        <div className={`col-${columnKey}`} key={columnKey}>
          {cells[columnKey]}
        </div>
      ))}
    </article>
  );
}

function columnDefinition(columnKey: RequestColumnKey) {
  return requestColumnDefinitions.find((column) => column.key === columnKey) ?? requestColumnDefinitions[0];
}

function normalizeColumnOrder(value: RequestColumnKey[]) {
  const knownColumns = new Set(requestColumnDefinitions.map((column) => column.key));
  const unique = value.filter((column, index) => knownColumns.has(column) && value.indexOf(column) === index);
  const missing = defaultRequestColumnOrder.filter((column) => !unique.includes(column));
  const merged = [...unique, ...missing];
  return merged as RequestColumnKey[];
}

function readStoredColumnOrder(value: string | null | undefined) {
  if (!value) return defaultRequestColumnOrder;
  try {
    const parsed = JSON.parse(value);
    if (!Array.isArray(parsed)) return defaultRequestColumnOrder;
    return normalizeColumnOrder(parsed as RequestColumnKey[]);
  } catch {
    return defaultRequestColumnOrder;
  }
}

function requestGridTemplate(order: RequestColumnKey[]) {
  return ["20px", ...normalizeColumnOrder(order).map((column) => columnDefinition(column).width)].join(" ");
}

function normalizedItems(request: PurchaseRequest): PurchaseRequestItem[] {
  return request.items?.length
    ? request.items
    : [
        {
          itemName: request.itemToPurchase,
          quantity: "1",
          unit: "ADET",
          packageCount: request.packageCount ?? null,
          dimensionsNote: request.productDimensions
        }
      ];
}

function requestItemsText(request: PurchaseRequest) {
  return normalizedItems(request)
    .map((item) => `${item.itemName} ${item.quantity} ${materialUnitLabels[item.unit]}`)
    .join(" ");
}

function requestItemsPreview(request: PurchaseRequest) {
  const items = normalizedItems(request);
  const first = items
    .slice(0, 2)
    .map((item) => `${item.itemName} (${item.quantity} ${materialUnitLabels[item.unit]})`)
    .join(" / ");
  return items.length > 2 ? `${first} +${items.length - 2} daha` : first;
}

function initialDriverSelection(request: PurchaseRequest) {
  return request.assignedDriverId || (isSpotDriverAssignment(request) ? "__spot__" : "");
}

function RequestDetail({
  request,
  user,
  drivers,
  busyKey,
  onBack,
  onEdit,
  onCancel,
  onAssign,
  onAssignSpot,
  onUnassign,
  onTakeOver,
  onStatus,
  onObjection,
  onClearObjection,
  onDelete,
  driverName
}: {
  request: PurchaseRequest;
  user: User;
  drivers: User[];
  busyKey: string | null;
  onBack: () => void;
  onEdit: () => void;
  onCancel: (reason?: string) => void;
  onAssign: (driver: User) => void;
  onAssignSpot: (payload: {
    spotDriverName?: string | null;
    licensePlate?: string | null;
    spotDriverIdentityNumber?: string | null;
    spotDriverCompanyName?: string | null;
    spotDriverPhone?: string | null;
  }) => void;
  onUnassign: () => void;
  onTakeOver: () => void;
  onStatus: (status: MaterialStatus, deliveredTo?: string | null) => void;
  onObjection: (reason: string) => void;
  onClearObjection: () => void;
  onDelete: () => void;
  driverName: (id?: string | null) => string;
}) {
  const [driverId, setDriverId] = useState(initialDriverSelection(request));
  const [cancelReason, setCancelReason] = useState("");
  const [objectionReason, setObjectionReason] = useState("");
  const [spotDriverName, setSpotDriverName] = useState(request.spotDriverName || "");
  const [spotLicensePlate, setSpotLicensePlate] = useState(request.licensePlate || "");
  const [spotDriverIdentityNumber, setSpotDriverIdentityNumber] = useState(request.spotDriverIdentityNumber || "");
  const [spotDriverCompanyName, setSpotDriverCompanyName] = useState(request.spotDriverCompanyName || "");
  const [spotDriverPhone, setSpotDriverPhone] = useState(request.spotDriverPhone || "");
  const [deliveredTo, setDeliveredTo] = useState(request.deliveredTo || "");
  const [recipientOptions, setRecipientOptions] = useState<string[]>([]);
  const selectedDriver = drivers.find((driver) => driver.id === driverId) ?? null;
  const isSpotSelected = driverId === "__spot__";
  const isClosed = ["DELIVERED", "CANCELLED"].includes(request.materialStatus);
  const isPurchasingOwner = user.role === "PURCHASING" && request.assignedPurchasingStaffId === user.id;
  const canEditPurchasingRequest = (isPurchasingOwner || canManagePurchasing(user)) && !isClosed;
  const purchasingOwnerName = request.assignedPurchasingStaffName?.trim() || "Bilinmiyor";
  const isAssignedToMe = request.assignedDriverId === user.id;
  const hasAnyAssignment = hasDriverAssignment(request);
  const hasReadyAssignment = hasReadyTransportAssignment(request);
  const selectedDriverHasPlate = hasText(selectedDriver?.licensePlate);
  const canMoveToOnTheWay =
    ["IN_PROGRESS", "PURCHASED"].includes(request.materialStatus) && !request.hasObjection && hasReadyAssignment;
  const canCompleteDelivery = request.materialStatus === "ON_THE_WAY" && !request.hasObjection && hasReadyAssignment;
  const canReopenDelivered = (user.role === "LOGISTICS_ADMIN" || isSuperAdmin(user)) && request.materialStatus === "DELIVERED";
  const canOverrideClosed = user.role === "LOGISTICS_ADMIN" || isSuperAdmin(user);
  const displayStatus = displayStatusForRequest(request);

  useEffect(() => {
    setDriverId(initialDriverSelection(request));
    setSpotDriverName(request.spotDriverName || "");
    setSpotLicensePlate(request.licensePlate || "");
    setSpotDriverIdentityNumber(request.spotDriverIdentityNumber || "");
    setSpotDriverCompanyName(request.spotDriverCompanyName || "");
    setSpotDriverPhone(request.spotDriverPhone || "");
    setDeliveredTo(request.deliveredTo || "");
  }, [
    request.assignedDriverId,
    request.licensePlate,
    request.spotDriverCompanyName,
    request.spotDriverIdentityNumber,
    request.spotDriverName,
    request.spotDriverPhone,
    request.deliveredTo
  ]);

  useEffect(() => {
    let active = true;
    api
      .getDeliveredRecipients()
      .then((names) => {
        if (active) setRecipientOptions(names);
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, []);

  return (
    <>
      <div className="detail-head">
        <button className="icon-button" onClick={onBack}>
          <ArrowLeft size={20} />
        </button>
        <div>
          <p className="eyebrow">{request.requestNumber}</p>
          <h2>{requestItemsPreview(request)}</h2>
        </div>
        <div className="detail-head-actions">
          <span className={`status-badge ${statusMeta[displayStatus].className}`}>
            {statusMeta[displayStatus].label}
          </span>
          {canEditPurchasingRequest && (
            <button className="ghost-button" onClick={onEdit}>
              <Edit3 size={17} />
              Düzenle
            </button>
          )}
        </div>
      </div>

      <div className="detail-layout">
        <section className="detail-card">
          <h3>Talep Bilgileri</h3>
          <div className="items-detail-table">
            <strong>Kalemler</strong>
            {normalizedItems(request).map((item, index) => (
              <div className="items-detail-row" key={item.id ?? index}>
                <span>{index + 1}. {item.itemName}</span>
                <span>{item.quantity} {materialUnitLabels[item.unit]}</span>
                <span>{item.packageCount ? `${item.packageCount} kap` : "Kap yok"}</span>
                <span>{item.dimensionsNote || "Ölçü notu yok"}</span>
              </div>
            ))}
          </div>
          <div className="info-grid">
            <InfoItem icon={<MapPin size={17} />} label="Firma" value={request.companyName} />
            <InfoItem icon={<UserRound size={17} />} label="İlanı Açan" value={purchasingOwnerName} />
            <InfoItem icon={<MapPin size={17} />} label="Adres" value={request.companyAddress || "Belirtilmedi"} />
            <InfoItem icon={<Phone size={17} />} label="Telefon" value={request.contactNumber || "Belirtilmedi"} />
            <InfoItem icon={<UserRound size={17} />} label="İlgili Kişi" value={request.contactPersonName || "Belirtilmedi"} />
            <InfoItem icon={<CalendarDays size={17} />} label="Talep Tarihi" value={formatStampTr(request.createdAt)} />
            <InfoItem icon={<CalendarDays size={17} />} label="Planlanan Tarih" value={formatPlannedTr(request.purchaseDate)} />
            <InfoItem icon={<CalendarDays size={17} />} label="Gerçekleşen Tarih" value={formatStampTr(request.actualPurchaseDate)} />
            <InfoItem
              icon={<Truck size={17} />}
              label="Şoför"
              value={hasAnyAssignment ? assignmentDriverLabel(request, driverName) : "Atanmamış"}
            />
            <InfoItem icon={<UserRound size={17} />} label="Plaka" value={request.licensePlate || "Yok"} />
            {request.spotDriverIdentityNumber && (
              <InfoItem icon={<Shield size={17} />} label="TC Kimlik No" value={request.spotDriverIdentityNumber} />
            )}
            {request.spotDriverCompanyName && (
              <InfoItem icon={<Users size={17} />} label="Spot Firma" value={request.spotDriverCompanyName} />
            )}
            {request.spotDriverPhone && (
              <InfoItem icon={<Phone size={17} />} label="Spot Telefon" value={request.spotDriverPhone} />
            )}
            {request.deliveredTo && (
              <InfoItem icon={<PackageCheck size={17} />} label="Teslim Edilen Kişi" value={request.deliveredTo} />
            )}
          </div>
          {request.notes && (
            <div className="note-box">
              <strong>Not</strong>
              <p>{request.notes}</p>
            </div>
          )}
          {request.objectionReason && user.role !== "DRIVER" && (
            <div className="note-box objection">
              <strong>İtirazlar</strong>
              <p style={{ whiteSpace: "pre-wrap" }}>{request.objectionReason}</p>
            </div>
          )}
        </section>

        <aside className="action-card">
          <h3>Aksiyonlar</h3>
          <datalist id="delivered-recipient-options">
            {recipientOptions.map((name) => (
              <option key={name} value={name} />
            ))}
          </datalist>

          {isSuperAdmin(user) && (
            <div className="action-stack super-admin-actions">
              {request.hasObjection && (
                <button
                  className="secondary-button"
                  disabled={busyKey === `clear-objection-${request.id}`}
                  onClick={onClearObjection}
                >
                  <X size={18} />
                  İtirazı Sil
                </button>
              )}
              <button
                className="danger-button"
                disabled={busyKey === `delete-request-${request.id}`}
                onClick={onDelete}
              >
                <Trash2 size={18} />
                Talebi Kalıcı Sil
              </button>
            </div>
          )}

          {isPurchasingRole(user.role) && (
            <div className="action-stack">
              {canEditPurchasingRequest ? (
                <>
                  <textarea
                    placeholder="İptal nedeni (opsiyonel)"
                    value={cancelReason}
                    onChange={(event) => setCancelReason(event.target.value)}
                  />
                  <button className="danger-button" onClick={() => onCancel(cancelReason)}>
                    <Ban size={18} />
                    Talebi İptal Et
                  </button>
                </>
              ) : (
                <p className="hint">
                  {isClosed
                    ? "Kapanmış talepler düzenlenemez."
                    : `İlanı açan: ${purchasingOwnerName}. Sadece ilanı açan kişi veya Satın Alma Yöneticisi düzenleyebilir.`}
                </p>
              )}
            </div>
          )}

          {canManageLogistics(user) && (
            <div className="action-stack">
              <p className="hint">Kayıtlı Şoför seçebilir veya spot araç için bilgileri elle girebilirsin.</p>
              <select
                value={driverId}
                onChange={(event) => {
                  setDriverId(event.target.value);
                  if (event.target.value === "__spot__") {
                    setSpotDriverName("");
                    setSpotLicensePlate("");
                    setSpotDriverIdentityNumber("");
                    setSpotDriverCompanyName("");
                    setSpotDriverPhone("");
                  }
                }}
                disabled={isClosed && !canOverrideClosed}
              >
                <option value="">Şoför seç</option>
                <option value="__spot__">Spot Araç</option>
                {drivers.map((driver) => (
                  <option key={driver.id} value={driver.id}>
                    {driver.fullName} {driver.licensePlate ? `- ${driver.licensePlate}` : ""}
                  </option>
                ))}
              </select>
              <button
                className="primary-button"
                disabled={
                  !selectedDriver ||
                  !selectedDriverHasPlate ||
                  isSpotSelected ||
                  (isClosed && !canOverrideClosed) ||
                  busyKey === `assign-${request.id}`
                }
                onClick={() => selectedDriver && onAssign(selectedDriver)}
              >
                <UserPlus size={18} />
                Kayıtlı Şoför Ata
              </button>
              {selectedDriver && !selectedDriverHasPlate && (
                <p className="hint">Kayıtlı şoförün plaka bilgisi olmadan atama yapılamaz.</p>
              )}

              {isSpotSelected && (
                <>
                  <div className="inline-separator">spot araç bilgileri</div>

                  <TextField
                    label="Firma Adı"
                    value={spotDriverCompanyName}
                    onChange={setSpotDriverCompanyName}
                  />
                  <TextField
                    label="Şoför Ad Soyad"
                    value={spotDriverName}
                    onChange={setSpotDriverName}
                  />
                  <TextField
                    label="Plaka"
                    value={spotLicensePlate}
                    onChange={(value) => setSpotLicensePlate(value.toUpperCase())}
                  />
                  <TextField
                    label="TC Kimlik No"
                    value={spotDriverIdentityNumber}
                    onChange={(value) => setSpotDriverIdentityNumber(value.replace(/\D/g, "").slice(0, 11))}
                  />
                  <TextField
                    label="Telefon"
                    value={spotDriverPhone}
                    onChange={setSpotDriverPhone}
                  />
                  <button
                    className="secondary-button"
                    disabled={
                      (isClosed && !canOverrideClosed) ||
                      busyKey === `assign-${request.id}` ||
                      spotDriverCompanyName.trim().length < 2
                    }
                    onClick={() =>
                      onAssignSpot({
                        spotDriverName: spotDriverName.trim() || null,
                        licensePlate: spotLicensePlate.trim() || null,
                        spotDriverIdentityNumber: spotDriverIdentityNumber.trim() || null,
                        spotDriverCompanyName: spotDriverCompanyName.trim() || null,
                        spotDriverPhone: spotDriverPhone.trim() || null
                      })
                    }
                  >
                    <Truck size={18} />
                    Spot Bilgisi Kaydet
                  </button>
                </>
              )}

              {hasAnyAssignment && (!isClosed || canOverrideClosed) && (
                <button className="ghost-button" onClick={onUnassign}>
                  Atamayı Kaldır
                </button>
              )}

              {canReopenDelivered && !request.hasObjection && (
                <>
                  <p className="hint">Tamamlanan talebi tekrar aktif duruma alabilirsin.</p>
                  <button className="secondary-button" onClick={() => onStatus("IN_PROGRESS")}>
                    <Clock size={18} />
                    Devama Al
                  </button>
                  <button
                    className="primary-button"
                    disabled={!hasReadyAssignment}
                    onClick={() => onStatus("ON_THE_WAY")}
                  >
                    <Navigation size={18} />
                    Yolda Yap
                  </button>
                </>
              )}

              {!canReopenDelivered && canMoveToOnTheWay && (
                <button
                  className="primary-button"
                  disabled={busyKey === `status-${request.id}-ON_THE_WAY`}
                  onClick={() => onStatus("ON_THE_WAY")}
                >
                  <Navigation size={18} />
                  Yolda Yap
                </button>
              )}

              {request.materialStatus === "ON_THE_WAY" && !request.hasObjection && (
                <>
                  {!hasReadyAssignment ? (
                    <p className="hint">Tamamlamak için önce şoför adı, plaka, TC ve telefon bilgilerini tamamla.</p>
                  ) : (
                    <>
                      <label className="field">
                        <span>Teslim Edilen Kişi</span>
                        <input
                          list="delivered-recipient-options"
                          value={deliveredTo}
                          onChange={(event) => setDeliveredTo(event.target.value)}
                          placeholder="Teslim alan kişi adı"
                        />
                      </label>
                      <button
                        className="primary-button"
                        disabled={!canCompleteDelivery || deliveredTo.trim().length < 2}
                        onClick={() => window.confirm("Bu işlem geri alınamaz. Devam edilsin mi?") && onStatus("DELIVERED", deliveredTo.trim())}
                      >
                        <PackageCheck size={18} />
                        Tamamlandı Yap
                      </button>
                    </>
                  )}
                </>
              )}

              <textarea
                placeholder="İtiraz nedeni"
                value={objectionReason}
                onChange={(event) => setObjectionReason(event.target.value)}
              />
              <button
                className="secondary-button"
                disabled={objectionReason.trim().length < 3}
                onClick={() => onObjection(objectionReason)}
              >
                <Send size={18} />
                İtiraz Gir
              </button>
            </div>
          )}

          {user.role === "DRIVER" && (
            <div className="action-stack">
              {canDriverTakeOver(request) && !isClosed && (
                <button className="primary-button" onClick={onTakeOver}>
                  <Truck size={18} />
                  Talebi Üstlen
                </button>
              )}

              {isAssignedToMe && ["IN_PROGRESS", "PURCHASED"].includes(request.materialStatus) && (
                <>
                  {!hasReadyAssignment && (
                    <p className="hint">Yola çıkmak için plaka bilgisi gerekli.</p>
                  )}
                  <button
                    className="primary-button"
                    disabled={!canMoveToOnTheWay}
                    onClick={() => onStatus("ON_THE_WAY")}
                  >
                    <Navigation size={18} />
                    Yolda Yap
                  </button>
                </>
              )}

              {isAssignedToMe && request.materialStatus === "ON_THE_WAY" && (
                <>
                  {!hasReadyAssignment ? (
                    <p className="hint">Teslim etmek için plaka bilgisi gerekli.</p>
                  ) : (
                    <>
                      <label className="field">
                        <span>Teslim Edilen Kişi</span>
                        <input
                          list="delivered-recipient-options"
                          value={deliveredTo}
                          onChange={(event) => setDeliveredTo(event.target.value)}
                          placeholder="Teslim alan kişi adı"
                        />
                      </label>
                      <button
                        className="primary-button"
                        disabled={!canCompleteDelivery || deliveredTo.trim().length < 2}
                        onClick={() => window.confirm("Bu işlem geri alınamaz. Devam edilsin mi?") && onStatus("DELIVERED", deliveredTo.trim())}
                      >
                        <PackageCheck size={18} />
                        Teslim Edildi Yap
                      </button>
                    </>
                  )}
                </>
              )}

              {isAssignedToMe && (
                <>
                  <textarea
                    placeholder="İtiraz nedeni"
                    value={objectionReason}
                    onChange={(event) => setObjectionReason(event.target.value)}
                  />
                  <button
                    className="secondary-button"
                    disabled={objectionReason.trim().length < 3}
                    onClick={() => onObjection(objectionReason)}
                  >
                    <Send size={18} />
                    İtiraz Et
                  </button>
                </>
              )}
            </div>
          )}
        </aside>
      </div>
    </>
  );
}

function AdminPanel({
  currentUser,
  users,
  busyKey,
  onNew,
  onEdit,
  onDeactivate,
  onActivate,
  onRemove
}: {
  currentUser: User;
  users: User[];
  busyKey: string | null;
  onNew: () => void;
  onEdit: (user: User) => void;
  onDeactivate: (user: User) => void;
  onActivate: (user: User) => void;
  onRemove: (user: User) => void;
}) {
  const currentUserIsSuperAdmin = isSuperAdmin(currentUser);
  const canCreateUsers = assignableRolesForUser(currentUser).length > 0;

  return (
    <>
      <div className="section-head">
        <div>
          <p className="eyebrow">Yönetim</p>
          <h2>Kullanıcılar</h2>
        </div>
        {canCreateUsers && (
          <button className="primary-button compact" onClick={onNew}>
            <Plus size={18} />
            Kullanıcı Ekle
          </button>
        )}
      </div>

      <div className="user-grid">
        {users.map((user) => (
          <article className="user-card" key={user.id}>
            <ProfileAvatar user={user} />
            <div className="user-main">
              <h3>{user.fullName}</h3>
              <span>@{user.username}</span>
              <div className="user-meta">
                <span style={{ color: "var(--text-strong)", fontWeight: 600 }}>
                  {roleLabels[user.role]} {isSuperAdmin(user) ? "· Süper Admin" : ""}
                </span>
                {user.licensePlate && <span>{user.licensePlate}</span>}
              </div>
            </div>
            <div className="user-actions">
              <span className={user.isActive ? "active-pill" : "passive-pill"}>
                {user.isActive ? "Aktif" : "Pasif"}
              </span>
              <button
                className="icon-button"
                disabled={!canManageUserRecord(currentUser, user)}
                onClick={() => onEdit(user)}
                title={canManageUserRecord(currentUser, user) ? "Düzenle" : "Bu kullanıcı düzenlenemez"}
              >
                <Edit3 size={15} />
              </button>
              {user.isActive ? (
                <button
                  className="icon-button danger-text"
                  disabled={busyKey === `delete-user-${user.id}` || !canManageUserRecord(currentUser, user)}
                  onClick={() => onDeactivate(user)}
                  title={canManageUserRecord(currentUser, user) ? "Pasifleştir" : "Bu kullanıcı pasifleştirilemez"}
                >
                  <Ban size={15} />
                </button>
              ) : (
                canManageUserRecord(currentUser, user) && (
                  <button
                    className="icon-button"
                    disabled={busyKey === `activate-user-${user.id}`}
                    onClick={() => onActivate(user)}
                    title="Aktif et"
                  >
                    <Check size={15} />
                  </button>
                )
              )}
              {currentUserIsSuperAdmin && !isSuperAdmin(user) && (
                <button
                  className="icon-button danger-text"
                  disabled={busyKey === `remove-user-${user.id}`}
                  onClick={() => onRemove(user)}
                  title="Kalıcı sil"
                >
                  <Trash2 size={15} />
                </button>
              )}
            </div>
          </article>
        ))}
      </div>
    </>
  );
}

function AuditPanel({ logs }: { logs: AuditLog[] | null }) {
  return (
    <>
      <div className="section-head">
        <div>
          <p className="eyebrow">Denetim</p>
          <h2>İşlem Geçmişi</h2>
        </div>
      </div>

      <div className="audit-list">
        {logs === null ? (
          <div className="empty-state compact">
            <Shield size={20} />
            <span>İşlem geçmişi servisi henüz alınamadı</span>
          </div>
        ) : logs.length === 0 ? (
          <div className="empty-state compact">
            <Shield size={20} />
            <span>Henüz işlem kaydı yok</span>
          </div>
        ) : (
          logs.map((log) => (
            <article className="audit-row" key={log.id}>
              <div className="audit-main">
                <strong>{formatAuditAction(log.action)}</strong>
                <span>{log.entityLabel || log.entityId || log.entityType}</span>
              </div>
              <div className="audit-meta">
                <span>{log.actorFullName || log.actorUsername || "Sistem"}</span>
                <span>{formatStampTr(log.createdAt)}</span>
                {log.ipAddress && <span>{log.ipAddress}</span>}
              </div>
            </article>
          ))
        )}
      </div>
    </>
  );
}

function RequestFormModal({
  request,
  onClose,
  onSubmit
}: {
  request: PurchaseRequest | null;
  onClose: () => void;
  onSubmit: (payload: CreatePurchaseRequest) => Promise<void>;
}) {
  const [form, setForm] = useState<CreatePurchaseRequest>(() =>
    request
      ? {
          itemToPurchase: request.itemToPurchase,
          items: normalizedItems(request),
          companyName: request.companyName,
          companyAddress: request.companyAddress,
          contactNumber: request.contactNumber,
          contactPersonName: request.contactPersonName ?? "",
          productDimensions: request.productDimensions,
          productWeight: request.productWeight,
          packageCount: request.packageCount ?? null,
          urgencyLevel: request.urgencyLevel,
          purchaseDate: request.purchaseDate,
          notes: request.notes
        }
      : emptyRequestForm
  );
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState("");

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setFormError("");
    const items = (form.items?.length ? form.items : [{ itemName: form.itemToPurchase, quantity: "1", unit: "ADET" as const }])
      .filter((item) => item.itemName.trim())
      .map((item, index) => ({
        ...item,
        itemName: item.itemName.trim(),
        dimensionsNote: item.dimensionsNote?.trim() || null,
        sortOrder: index
      }));
    if (items.length > maxRequestItems) {
      setFormError(`En fazla ${maxRequestItems} kalem eklenebilir`);
      return;
    }
    setSaving(true);
    try {
      const firstItem = items[0];
      await onSubmit({
        ...form,
        items,
        itemToPurchase: (firstItem?.itemName ?? form.itemToPurchase).trim(),
        productDimensions: firstItem?.dimensionsNote || null,
        productWeight: null,
        packageCount: firstItem?.packageCount && firstItem.packageCount > 0 ? firstItem.packageCount : null,
        companyName: form.companyName.trim()
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-backdrop">
      <form className="modal-card wide" onSubmit={submit}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">{request ? "Düzenle" : "Yeni Talep"}</p>
            <h2>{request ? request.requestNumber : "Talep Oluştur"}</h2>
          </div>
          <button type="button" className="icon-button" onClick={onClose}>
            <X size={20} />
          </button>
        </div>

        <div className="form-grid">
          <div className="form-grid-full">
            <RequestItemsEditor items={form.items ?? []} onChange={(items) => setForm({ ...form, items })} />
          </div>
          {formError && <div className="form-error form-grid-full">{formError}</div>}
          <TextField label="Firma" value={form.companyName} onChange={(value) => setForm({ ...form, companyName: value })} required />
          <TextField label="Adres" value={form.companyAddress || ""} onChange={(value) => setForm({ ...form, companyAddress: value })} />
          <TextField label="Telefon" value={form.contactNumber || ""} onChange={(value) => setForm({ ...form, contactNumber: value })} />
          <TextField label="İlgili Kişi" value={form.contactPersonName || ""} onChange={(value) => setForm({ ...form, contactPersonName: value })} />
          <label className="field">
            <span>Aciliyet</span>
            <select
              value={form.urgencyLevel}
              onChange={(event) => setForm({ ...form, urgencyLevel: event.target.value as UrgencyLevel })}
            >
              <option value="LOW">Düşük</option>
              <option value="NORMAL">Normal</option>
              <option value="HIGH">Yüksek</option>
              <option value="URGENT">Acil</option>
            </select>
          </label>
          <label className="field">
            <span>Planlanan Tarih</span>
            <input
              type="date"
              value={form.purchaseDate || ""}
              onChange={(event) => setForm({ ...form, purchaseDate: event.target.value })}
            />
          </label>
        </div>

        <label className="field">
          <span>Not</span>
          <textarea value={form.notes || ""} onChange={(event) => setForm({ ...form, notes: event.target.value })} />
        </label>

        <div className="modal-actions">
          <button type="button" className="ghost-button" onClick={onClose}>
            Vazgeç
          </button>
          <button className="primary-button" disabled={saving}>
            {saving ? "Kaydediliyor..." : "Kaydet"}
          </button>
        </div>
      </form>
    </div>
  );
}

function UserFormModal({
  user,
  currentUser,
  onClose,
  onSubmit
}: {
  user: User | null;
  currentUser: User;
  onClose: () => void;
  onSubmit: (payload: CreateUserRequest | UpdateUserRequest) => Promise<void>;
}) {
  const allowedRoles = useMemo<UserRole[]>(() => {
    const roles = assignableRolesForUser(currentUser);
    if (user?.role === "ADMIN" && !roles.includes("ADMIN")) return ["ADMIN", ...roles];
    if (user?.role && !roles.includes(user.role)) return [user.role, ...roles];
    return roles;
  }, [currentUser, user]);
  const defaultRole = (allowedRoles[0] ?? "PURCHASING") as UserRole;

  const [form, setForm] = useState<CreateUserRequest>(() =>
    user
      ? {
          username: user.username,
          fullName: user.fullName,
          password: "",
          role: user.role,
          licensePlate: user.licensePlate || ""
        }
      : { ...emptyUserForm, role: defaultRole }
  );
  const [saving, setSaving] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    try {
      const payload = user && !form.password.trim() ? { ...form, password: undefined } : form;
      await onSubmit(payload);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-backdrop">
      <form className="modal-card" onSubmit={submit}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">{user ? "Düzenle" : "Yeni Kullanıcı"}</p>
            <h2>{user ? user.fullName : "Kullanıcı Oluştur"}</h2>
          </div>
          <button type="button" className="icon-button" onClick={onClose}>
            <X size={20} />
          </button>
        </div>

        <TextField label="Kullanıcı Adı" value={form.username} onChange={(value) => setForm({ ...form, username: value })} required />
        <TextField label="Ad Soyad" value={form.fullName} onChange={(value) => setForm({ ...form, fullName: value })} required />
        <TextField
          label={user ? "Yeni Şifre" : "Şifre"}
          value={form.password}
          onChange={(value) => setForm({ ...form, password: value })}
          type="password"
          required={!user}
        />
        <label className="field">
          <span>Rol</span>
          <select
            value={form.role}
            onChange={(event) => {
              const role = event.target.value as UserRole;
              if (allowedRoles.includes(role)) setForm({ ...form, role });
            }}
            disabled={allowedRoles.length <= 1}
          >
            {allowedRoles.map((role) => (
              <option value={role} key={role}>
                {roleLabels[role]}
              </option>
            ))}
          </select>
        </label>
        {form.role === "DRIVER" && (
          <TextField label="Plaka" value={form.licensePlate || ""} onChange={(value) => setForm({ ...form, licensePlate: value })} />
        )}

        <div className="modal-actions">
          <button type="button" className="ghost-button" onClick={onClose}>
            Vazgeç
          </button>
          <button className="primary-button" disabled={saving}>
            {saving ? "Kaydediliyor..." : "Kaydet"}
          </button>
        </div>
      </form>
    </div>
  );
}

function ChangePasswordModal({
  onClose,
  onSubmit
}: {
  onClose: () => void;
  onSubmit: (payload: ChangePasswordRequest) => Promise<void>;
}) {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    if (newPassword !== confirmPassword) {
      setError("Yeni şifreler eşleşmiyor");
      return;
    }
    setSaving(true);
    try {
      await onSubmit({ currentPassword, newPassword });
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Şifre değiştirilemedi");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-backdrop">
      <form className="modal-card" onSubmit={submit}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">Hesap</p>
            <h2>Şifre Değiştir</h2>
          </div>
          <button type="button" className="icon-button" onClick={onClose}>
            <X size={20} />
          </button>
        </div>

        <TextField label="Mevcut Şifre" value={currentPassword} onChange={setCurrentPassword} type="password" required />
        <TextField label="Yeni Şifre" value={newPassword} onChange={setNewPassword} type="password" required />
        <TextField label="Yeni Şifre Tekrar" value={confirmPassword} onChange={setConfirmPassword} type="password" required />
        {error && <div className="form-error">{error}</div>}

        <div className="modal-actions">
          <button type="button" className="ghost-button" onClick={onClose}>
            Vazgeç
          </button>
          <button className="primary-button" disabled={saving}>
            {saving ? "Kaydediliyor..." : "Şifreyi Değiştir"}
          </button>
        </div>
      </form>
    </div>
  );
}

function TextField({
  label,
  value,
  onChange,
  type = "text",
  required = false
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  required?: boolean;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <input type={type} value={value} onChange={(event) => onChange(event.target.value)} required={required} />
    </label>
  );
}

function StatCard({
  label,
  value,
  tone,
  selected,
  onClick
}: {
  label: string;
  value: number;
  tone: "warning" | "info" | "orange" | "success";
  selected?: boolean;
  onClick?: () => void;
}) {
  return (
    <button
      type="button"
      className={`stat-card stat-${tone}${selected ? " selected" : ""}`}
      aria-pressed={selected}
      onClick={onClick}
    >
      <span>{label}</span>
      <strong>{value}</strong>
    </button>
  );
}

function InfoItem({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <div className="info-item">
      <span className="info-icon">{icon}</span>
      <div>
        <small>{label}</small>
        <strong>{value}</strong>
      </div>
    </div>
  );
}

function countByStatus(requests: PurchaseRequest[], status: MaterialStatus) {
  return requests.filter((request) => request.materialStatus === status).length;
}

function countPending(requests: PurchaseRequest[]) {
  return requests.filter(
    (request) => !terminalStatuses.includes(request.materialStatus) && !hasDriverAssignment(request)
  ).length;
}

function countInProgress(requests: PurchaseRequest[]) {
  return requests.filter(
    (request) => !terminalStatuses.includes(request.materialStatus) && hasDriverAssignment(request)
  ).length;
}

function countObjected(requests: PurchaseRequest[]) {
  return requests.filter((request) => request.hasObjection).length;
}

function formatTime(value: number) {
  return new Intl.DateTimeFormat("tr-TR", {
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function DateTimeCell({ value }: { value?: number | null }) {
  if (!value) return <div className="cell cell-date cell-muted">—</div>;
  const date = new Date(value);
  const datePart = new Intl.DateTimeFormat("tr-TR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric"
  }).format(date);
  const timePart = new Intl.DateTimeFormat("tr-TR", {
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
  return (
    <div className="cell cell-date cell-muted cell-datetime">
      <span>{datePart}</span>
      <span className="cell-time">{timePart}</span>
    </div>
  );
}

function formatStampTr(value?: number | null) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("tr-TR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function formatAuditAction(action: string) {
  const labels: Record<string, string> = {
    CREATE_REQUEST: "Talep oluşturuldu",
    UPDATE_REQUEST: "Talep güncellendi",
    DELETE_REQUEST: "Talep silindi",
    CANCEL_REQUEST: "Talep iptal edildi",
    ASSIGN_DRIVER: "Şoför atandı",
    ASSIGN_SPOT_DRIVER: "Spot şoför kaydedildi",
    UNASSIGN_DRIVER: "Şoför ataması kaldırıldı",
    TAKE_OVER: "Talep üstlenildi",
    UPDATE_STATUS: "Durum değişti",
    SUBMIT_OBJECTION: "İtiraz girildi",
    CLEAR_OBJECTION: "İtiraz temizlendi",
    CREATE_USER: "Kullanıcı oluşturuldu",
    UPDATE_USER: "Kullanıcı güncellendi",
    DEACTIVATE_USER: "Kullanıcı pasif edildi",
    ACTIVATE_USER: "Kullanıcı aktif edildi",
    DELETE_USER: "Kullanıcı kalıcı silindi"
  };
  return labels[action] ?? action.replace(/_/g, " ");
}

function formatPlannedTr(value?: string | null) {
  if (!value) return "—";
  const parts = value.trim().split("-");
  if (parts.length === 3 && parts[0].length === 4) {
    const [year, month, day] = parts;
    return `${day}.${month}.${year}`;
  }
  return value;
}

export default App;
