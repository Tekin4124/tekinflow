import type {AppNotification,AuditLog,ChangePasswordRequest,CreatePurchaseRequest,CreateUserRequest,MaterialStatus,PurchaseRequest,Session,UpdateUserRequest,User,UserRole} from './types';

export const demoRoles: UserRole[] = ['PURCHASING','LOGISTICS_OFFICE','DRIVER','ADMIN'];
export const demoLabels = ['Satın alma','Sevkiyat','Şoför','Yönetici'];
const STORAGE = 'tekinflow-demo-data-v1';
type DemoState = {users:User[];requests:PurchaseRequest[];notifications:AppNotification[];audit:AuditLog[];preferences:Record<string,string>;sequence:number};
export function seedDemoState():DemoState {
  const now=Date.now();
  const users:User[]=[
    {id:'purchasing',username:'purchasing',fullName:'Satın Alma Kullanıcısı',role:'PURCHASING',isActive:true},
    {id:'logistics',username:'logistics_office',fullName:'Sevkiyat Kullanıcısı',role:'LOGISTICS_OFFICE',isActive:true},
    {id:'driver',username:'driver',fullName:'Şoför Kullanıcısı',role:'DRIVER',licensePlate:'34 TF 101',isActive:true},
    {id:'admin',username:'admin',fullName:'Yönetici',role:'ADMIN',isSuperAdmin:true,isActive:true}
  ];
  function request(id:string,item:string,company:string,status:MaterialStatus,assigned=false):PurchaseRequest {
    return {id,requestNumber:`TLP-2026-${id}`,itemToPurchase:item,items:[{itemName:item,quantity:'4',unit:'ADET'}],companyName:company,companyAddress:'Örnek adres',contactNumber:'',contactPersonName:'Örnek İletişim',productDimensions:'Örnek paket',productWeight:'10 kg',urgencyLevel:'NORMAL',purchaseDate:'2026-10-12',notes:'Örnek kayıt',licensePlate:assigned?'34 TF 101':'',materialStatus:status,assignedDriverId:assigned?'driver':null,assignedDriverName:assigned?'Şoför Kullanıcısı':null,assignedPurchasingStaffId:'purchasing',assignedPurchasingStaffName:'Satın Alma Kullanıcısı',createdAt:now,updatedAt:now,hasObjection:false,seenByUserIds:[]};
  }
  return {users,requests:[request('100','Bağlantı elemanı','Örnek Tedarikçi A','IN_PROGRESS'),request('101','Paketleme malzemesi','Örnek Tedarikçi B','IN_PROGRESS',true),request('102','Elektrik ekipmanı','Örnek Tedarikçi C','ON_THE_WAY',true),{...request('103','Ofis sarf malzemesi','Örnek Tedarikçi D','DELIVERED',true),deliveredTo:'Örnek Alıcı',actualPurchaseDate:now}],notifications:[],audit:[],preferences:{},sequence:104};
}
/** The complete API adapter is local. It contains no fetch, proxy or cloud SDK. */
export function createDemoClient(initial:DemoState=seedDemoState(),persist:(state:DemoState)=>void=()=>{}) {
  let state=initial;
  let session:Session|null=null;
  let idSequence=0;
  const id=()=>`demo-${Date.now()}-${++idSequence}`;
  const save=()=>persist(state);
  const actor=()=>{if(!session)throw new Error('Bir rol seç');return session.user;};
  const allow=(roles:UserRole[])=>{const user=actor();if(!user.isSuperAdmin&&!roles.includes(user.role))throw new Error('Bu rol için işlem yetkisi yok');return user;};
  const find=(id:string)=>{const r=state.requests.find(r=>r.id===id);if(!r)throw new Error('Talep bulunamadı');return r;};
  const record=(action:string,r?:PurchaseRequest)=>{
    const user=actor();state.audit.unshift({id:id(),actorUserId:user.id,actorUsername:user.username,actorFullName:user.fullName,action,entityType:r?'REQUEST':'USER',entityId:r?.id,entityLabel:r?.requestNumber,createdAt:Date.now()});
    if(r)state.users.forEach(u=>state.notifications.unshift({id:id(),message:`${r.requestNumber} • ${action}`,timestamp:Date.now(),isRead:false,targetUserId:u.id,requestId:r.id}));save();
  };
  const update=(requestId:string,action:string,fn:(r:PurchaseRequest)=>PurchaseRequest)=>{const next=fn(find(requestId));next.updatedAt=Date.now();state.requests=state.requests.map(r=>r.id===requestId?next:r);record(action,next);return next;};
  const editable=(r:PurchaseRequest)=>{const u=allow(['PURCHASING','PURCHASING_ADMIN']);if(!u.isSuperAdmin&&u.role==='PURCHASING'&&r.assignedPurchasingStaffId!==u.id)throw new Error('Yalnız kendi talebini düzenleyebilirsin');if(['DELIVERED','CANCELLED'].includes(r.materialStatus))throw new Error('Tamamlanan talep düzenlenemez');};
  const view=(r:PurchaseRequest)=>{const u=actor();if(u.role!=='DRIVER')return structuredClone(r);return {...structuredClone(r),companyAddress:r.assignedDriverId===u.id?r.companyAddress:'',contactNumber:r.assignedDriverId===u.id?r.contactNumber:'',contactPersonName:r.assignedDriverId===u.id?r.contactPersonName:null,spotDriverIdentityNumber:null,spotDriverPhone:null,assignedPurchasingStaffId:'',assignedPurchasingStaffName:null,seenByUserIds:[]};};
  const visible=(r:PurchaseRequest)=>actor().role!=='DRIVER'||r.assignedDriverId===actor().id||(!r.assignedDriverId&&!r.licensePlate&&!['DELIVERED','CANCELLED'].includes(r.materialStatus));
  const validate=(p:CreatePurchaseRequest)=>{if(!p.companyName.trim()||!p.itemToPurchase.trim()&&!p.items?.length)throw new Error('Firma ve ürün gerekli');if(p.items?.some(i=>!i.itemName.trim()||Number(i.quantity.replace(',','.'))<=0||!Number.isFinite(Number(i.quantity.replace(',','.')))))throw new Error('Kalem adı ve miktarı kontrol et');};
  const selectRole=(role:UserRole)=>{const user=state.users.find(u=>u.role===role&&u.isActive);if(!user)throw new Error('Örnek rol bulunamadı');session={accessToken:'local-demo',refreshToken:'local-demo',user:structuredClone(user)};return session;};
  const api={
    selectRole,
    resetDemo:()=>{state=seedDemoState();session=null;save();},
    login:async(username:string,_password:string,_code?:string,_secret?:string|null)=>{const user=state.users.find(u=>u.username===username&&u.isActive);if(!user)throw new Error('Örnek rol bulunamadı');return {...selectRole(user.role)};},
    logout:async()=>{session=null;},
    getRequests:async()=>state.requests.filter(visible).map(view),
    getRequest:async(requestId:string)=>{const r=find(requestId);if(!visible(r))throw new Error('Talep bu role açık değil');return view(r);},
    createRequest:async(payload:CreatePurchaseRequest)=>{const u=allow(['PURCHASING','PURCHASING_ADMIN']);validate(payload);const now=Date.now();const r:PurchaseRequest={...payload,id:id(),requestNumber:`TLP-2026-${state.sequence++}`,itemToPurchase:payload.items?.[0]?.itemName||payload.itemToPurchase,companyAddress:payload.companyAddress||'',contactNumber:payload.contactNumber||'',productDimensions:payload.productDimensions||'',productWeight:payload.productWeight||'',purchaseDate:payload.purchaseDate||'',notes:payload.notes||'',urgencyLevel:payload.urgencyLevel||'NORMAL',licensePlate:'',materialStatus:'IN_PROGRESS',assignedPurchasingStaffId:u.id,assignedPurchasingStaffName:u.fullName,createdAt:now,updatedAt:now};state.requests.unshift(r);record('Talep oluşturuldu',r);return structuredClone(r);},
    updateRequest:async(requestId:string,payload:CreatePurchaseRequest)=>{editable(find(requestId));validate(payload);return update(requestId,'Talep düzenlendi',r=>({...r,...payload,companyAddress:payload.companyAddress||"",contactNumber:payload.contactNumber||"",productDimensions:payload.productDimensions||"",productWeight:payload.productWeight||"",purchaseDate:payload.purchaseDate||"",notes:payload.notes||""}));},
    cancelRequest:async(requestId:string,reason?:string)=>{editable(find(requestId));return update(requestId,'Talep iptal edildi',r=>({...r,materialStatus:'CANCELLED',cancellationReason:reason,cancelledBy:actor().id}));},
    deleteRequest:async(requestId:string)=>{allow(['ADMIN']);find(requestId);state.requests=state.requests.filter(r=>r.id!==requestId);record('Talep silindi');},
    clearObjection:async(requestId:string)=>{allow(['ADMIN']);return update(requestId,'İtiraz temizlendi',r=>({...r,hasObjection:false,objectionReason:null,objectionBy:null}));},
    assignDriver:async(requestId:string,driverId:string,plate?:string|null)=>{allow(['LOGISTICS_OFFICE','LOGISTICS_ADMIN']);const user=state.users.find(u=>u.id===driverId&&u.role==='DRIVER'&&u.isActive);if(!user)throw new Error('Şoför bulunamadı');const r=find(requestId);if(['DELIVERED','CANCELLED'].includes(r.materialStatus))throw new Error('Tamamlanan talep atanamaz');return update(requestId,'Şoför atandı',r=>({...r,assignedDriverId:driverId,assignedDriverName:user.fullName,licensePlate:plate||user.licensePlate||'',spotDriverName:null,spotDriverPhone:null,spotDriverIdentityNumber:null,spotDriverCompanyName:null}));},
    assignSpotDriver:async(requestId:string,payload:{spotDriverName?:string|null;licensePlate?:string|null;spotDriverIdentityNumber?:string|null;spotDriverCompanyName?:string|null;spotDriverPhone?:string|null})=>{allow(['LOGISTICS_OFFICE','LOGISTICS_ADMIN']);if(!payload.licensePlate||!payload.spotDriverName||!payload.spotDriverIdentityNumber||!payload.spotDriverPhone)throw new Error('Spot araç bilgilerini tamamla');if(['DELIVERED','CANCELLED'].includes(find(requestId).materialStatus))throw new Error('Tamamlanan talep atanamaz');return update(requestId,'Spot araç atandı',r=>({...r,...payload,licensePlate:payload.licensePlate!,assignedDriverId:null,assignedDriverName:null}));},
    unassignDriver:async(requestId:string)=>{allow(['LOGISTICS_OFFICE','LOGISTICS_ADMIN']);return update(requestId,'Atama kaldırıldı',r=>({...r,assignedDriverId:null,assignedDriverName:null,licensePlate:'',spotDriverName:null,spotDriverPhone:null,spotDriverIdentityNumber:null,spotDriverCompanyName:null,materialStatus:'IN_PROGRESS'}));},
    takeOver:async(requestId:string)=>{const user=allow(['DRIVER']);const r=find(requestId);if(!visible(r)||['DELIVERED','CANCELLED'].includes(r.materialStatus))throw new Error('Talep üstlenilemez');return update(requestId,'Talep üstlenildi',r=>({...r,assignedDriverId:user.id,assignedDriverName:user.fullName,licensePlate:user.licensePlate||'',materialStatus:'IN_PROGRESS',hasObjection:false}));},
    updateStatus:async(requestId:string,status:MaterialStatus,recipient?:string|null)=>{const user=allow(['LOGISTICS_OFFICE','LOGISTICS_ADMIN','DRIVER']);const r=find(requestId);if(user.role==='DRIVER'&&r.assignedDriverId!==user.id)throw new Error('Talep sana atanmadı');const next=r.materialStatus==='ON_THE_WAY'?'DELIVERED':['IN_PROGRESS','PURCHASED'].includes(r.materialStatus)?'ON_THE_WAY':r.materialStatus;if(status!==r.materialStatus&&status!==next)throw new Error('Durum sırası geçersiz');if(!r.licensePlate)throw new Error('Önce şoför veya spot araç ata');if(status==='DELIVERED'&&!recipient?.trim())throw new Error('Teslim alan kişi gerekli');return update(requestId,'Durum güncellendi',r=>({...r,materialStatus:status,deliveredTo:recipient||r.deliveredTo,actualPurchaseDate:status==='DELIVERED'?Date.now():r.actualPurchaseDate}));},
    getDeliveredRecipients:async()=>{actor();return [...new Set(state.requests.map(r=>r.deliveredTo).filter((r):r is string=>!!r))];},
    submitObjection:async(requestId:string,reason:string)=>{const user=allow(['LOGISTICS_OFFICE','LOGISTICS_ADMIN','DRIVER']);if(user.role==='DRIVER'&&find(requestId).assignedDriverId!==user.id)throw new Error('Talep sana atanmadı');if(!reason.trim())throw new Error('İtiraz nedeni gerekli');return update(requestId,'İtiraz eklendi',r=>({...r,hasObjection:true,objectionReason:reason,objectionBy:user.id}));},
    markSeen:async(requestId:string)=>{const r=find(requestId);if(!visible(r))throw new Error('Talep bu role açık değil');r.seenByUserIds=[...new Set([...(r.seenByUserIds||[]),actor().id])];save();},
    getNotifications:async()=>state.notifications.filter(n=>n.targetUserId===actor().id).map(n=>({...n})),
    readNotification:async(notificationId:string)=>{const n=state.notifications.find(n=>n.id===notificationId&&n.targetUserId===actor().id);if(n)n.isRead=true;save();},
    getUsers:async()=>{allow(['ADMIN']);return structuredClone(state.users);},
    getDrivers:async()=>{actor();return structuredClone(state.users.filter(u=>u.role==='DRIVER'&&u.isActive));},
    getAuditLogs:async()=>{allow(['ADMIN']);return structuredClone(state.audit);},
    createUser:async(payload:CreateUserRequest)=>{allow(['ADMIN']);if(state.users.some(u=>u.username===payload.username))throw new Error('Kullanıcı adı mevcut');const {password:_password,...fields}=payload;const user:User={...fields,id:id(),isActive:true};state.users.push(user);record('Kullanıcı eklendi');return {...user};},
    updateUser:async(userId:string,payload:UpdateUserRequest)=>{allow(['ADMIN']);const user=state.users.find(u=>u.id===userId);if(!user)throw new Error('Kullanıcı yok');if(user.isSuperAdmin&&((payload.role&&payload.role!=='ADMIN')||(payload.username&&payload.username!==user.username)))throw new Error('Yöneticinin rolü ve giriş adı korunur');const {password:_password,...fields}=payload;Object.assign(user,fields);record('Kullanıcı düzenlendi');return {...user};},
    deleteUser:async(userId:string)=>{allow(['ADMIN']);const user=state.users.find(u=>u.id===userId);if(user?.isSuperAdmin)throw new Error('Yönetici hesabı korunur');if(user)user.isActive=false;record('Kullanıcı pasifleştirildi');},
    activateUser:async(userId:string)=>{allow(['ADMIN']);const user=state.users.find(u=>u.id===userId);if(!user)throw new Error('Kullanıcı yok');user.isActive=true;record('Kullanıcı etkinleştirildi');return {...user};},
    deleteUserPermanently:async(userId:string)=>{allow(['ADMIN']);if(state.users.find(u=>u.id===userId)?.isSuperAdmin)throw new Error('Yönetici hesabı korunur');state.users=state.users.filter(u=>u.id!==userId);record('Kullanıcı silindi');},
    changePassword:async(_payload:ChangePasswordRequest)=>{actor();throw new Error('Bu sürüm gerçek hesap veya parola kullanmaz. Rol seçerek giriş yapabilirsin.');},
    updateFcmToken:async(_token:string)=>{},
    getPreference:async(key:string)=>({key,value:state.preferences[key]||'[]'}),
    putPreference:async(key:string,value:string)=>{state.preferences[key]=value;save();return {key,value};}
  };
  return {api,loadSession:()=>session,saveSession:(value:Session|null)=>{session=value;}};
}
let initial=seedDemoState();
try {if(typeof localStorage!=='undefined'){const value=localStorage.getItem(STORAGE);if(value){const parsed=JSON.parse(value);if(Array.isArray(parsed.users)&&Array.isArray(parsed.requests))initial=parsed;}}}catch(_){}
const oldLabels:Record<string,string>={"Demo Satın Alma":"Satın Alma Kullanıcısı","Demo Sevkiyat":"Sevkiyat Kullanıcısı","Demo Şoför":"Şoför Kullanıcısı","Demo Yönetici":"Yönetici","Demo İletişim":"Örnek İletişim","Demo Alıcı":"Örnek Alıcı","Tamamen sentetik demo kaydı":"Örnek kayıt"};
initial.audit=(initial.audit??[]).map(a=>({...a,actorFullName:a.actorFullName?oldLabels[a.actorFullName]??a.actorFullName:a.actorFullName}));
initial.users=initial.users.map(u=>({...u,fullName:oldLabels[u.fullName]??u.fullName}));
initial.requests=initial.requests.map(r=>({...r,assignedDriverName:r.assignedDriverName?oldLabels[r.assignedDriverName]??r.assignedDriverName:r.assignedDriverName,assignedPurchasingStaffName:r.assignedPurchasingStaffName?oldLabels[r.assignedPurchasingStaffName]??r.assignedPurchasingStaffName:r.assignedPurchasingStaffName,contactPersonName:r.contactPersonName?oldLabels[r.contactPersonName]??r.contactPersonName:r.contactPersonName,deliveredTo:r.deliveredTo?oldLabels[r.deliveredTo]??r.deliveredTo:r.deliveredTo,notes:oldLabels[r.notes]??r.notes}));
const client=createDemoClient(initial,state=>{try{localStorage.setItem(STORAGE,JSON.stringify(state));}catch(_){}});
if(typeof window!=='undefined') {
 const requested=new URLSearchParams(window.location.search).get('role') as UserRole|null;
 if (requested && demoRoles.includes(requested)) {
   try { client.api.selectRole(requested); } catch (_) { /* Keep the role picker if this role was deactivated. */ }
 }
}
export const api=client.api;
export const loadSession=client.loadSession;
export const saveSession=client.saveSession;
