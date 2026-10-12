import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createDemoClient,seedDemoState} from '../src/api.ts';
const payload={itemToPurchase:'Test ürün',items:[{itemName:'Test ürün',quantity:'2',unit:'ADET'}],companyName:'Örnek Firma',companyAddress:'Örnek adres',contactNumber:'',productDimensions:'',productWeight:'',urgencyLevel:'NORMAL',purchaseDate:'2026-10-12',notes:''};
test('purchase, assignment, delivery and audit work across roles',async()=>{
 const {api}=createDemoClient();api.selectRole('PURCHASING');const created=await api.createRequest(payload);
 api.selectRole('LOGISTICS_OFFICE');await api.assignDriver(created.id,'driver','34 TF 101');
 api.selectRole('DRIVER');await api.updateStatus(created.id,'ON_THE_WAY');await api.updateStatus(created.id,'DELIVERED','Demo Alıcı');
 assert.equal((await api.getRequest(created.id)).materialStatus,'DELIVERED');assert.equal((await api.getRequest(created.id)).deliveredTo,'Demo Alıcı');
 api.selectRole('ADMIN');assert.equal((await api.getAuditLogs()).length,4);
});
test('driver sees only own jobs or the unassigned pool and cannot create requests',async()=>{
 const state=seedDemoState();state.requests.push({...state.requests[0],id:'other-job',assignedDriverId:'another-driver',licensePlate:'34 TF 202'});
 const {api}=createDemoClient(state);api.selectRole('DRIVER');assert.ok(!(await api.getRequests()).some(r=>r.id==='other-job'));
 await assert.rejects(()=>api.createRequest(payload),/yetkisi/);const pool=(await api.getRequests()).find(r=>!r.assignedDriverId);assert.equal(pool.companyAddress,'');await api.takeOver(pool.id);assert.equal((await api.getRequest(pool.id)).assignedDriverId,'driver');
});
test('failed validation leaves records unchanged and can be corrected',async()=>{
 const {api}=createDemoClient();api.selectRole('PURCHASING');const count=(await api.getRequests()).length;
 await assert.rejects(()=>api.createRequest({...payload,companyName:''}),/gerekli/);assert.equal((await api.getRequests()).length,count);await api.createRequest(payload);assert.equal((await api.getRequests()).length,count+1);
});
test('state survives a role change; reset clears mutations',async()=>{
 const {api}=createDemoClient();api.selectRole('PURCHASING');await api.createRequest(payload);api.selectRole('LOGISTICS_OFFICE');assert.equal((await api.getRequests()).length,5);api.resetDemo();api.selectRole('PURCHASING');assert.equal((await api.getRequests()).length,4);
});
test('read receipt, objection and password action never call the network',async()=>{
 const previous=globalThis.fetch;globalThis.fetch=()=>{throw new Error('Network forbidden')};
 try {const {api}=createDemoClient();api.selectRole('DRIVER');const request=(await api.getRequests()).find(r=>r.assignedDriverId==='driver'&&r.materialStatus==='IN_PROGRESS');await api.markSeen(request.id);await api.submitObjection(request.id,'Örnek itiraz');assert.equal((await api.getRequest(request.id)).hasObjection,true);await assert.rejects(()=>api.changePassword({currentPassword:'demo',newPassword:'demo'}),/gerçek hesap/);}finally{globalThis.fetch=previous;}
});
