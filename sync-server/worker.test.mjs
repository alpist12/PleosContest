import { test } from 'node:test';
import assert from 'node:assert/strict';
import { timingSafeEqual, createHash } from 'node:crypto';
import worker, { validDocument } from './worker.mjs';
if (!crypto.subtle.timingSafeEqual) Object.defineProperty(crypto.subtle, 'timingSafeEqual', { value: (a,b) => timingSafeEqual(Buffer.from(a),Buffer.from(b)) });
const token = 'a'.repeat(64);
const doc = { id:'trip',title:'가족 캠핑',type:'캠핑',destination:'평창',departAt:'11월 20일 10:00',status:'PREPARING',landmark:'windmill',
  departureEpochMillis:0,reminderMinutes:60,reminderEnabled:false, observations:[],
  items:[{id:'i1',name:'랜턴',owner:'아빠',place:'트렁크',state:'NOT_CHECKED',confirmedIn:'NONE',prepared:false,loaded:false,recoverable:true,dropOff:true,revision:0}] };
function environment() {
  const row={version:0,document:null};
  return { AUTH_SHA256:createHash('sha256').update(token).digest('hex'), DB:{prepare(sql) { return {
    async first(){return {...row}}, bind(document,version){return {async run(){if(row.version!==version)return {meta:{changes:0}};row.document=document;row.version++;return {meta:{changes:1}}}}}
  }}} };
}
const req=(method='GET',body=null,key=token)=>new Request('https://example.com/trip',{method,headers:{Authorization:`Bearer ${key}`},...(body?{body:JSON.stringify(body)}:{})});
test('authentication protects read and write',async()=>{
 const env=environment(); assert.equal((await worker.fetch(req('GET',null,'b'.repeat(64)),env)).status,401);
 assert.equal((await worker.fetch(req('PUT',{version:0,document:doc},'b'.repeat(64)),env)).status,401);
});
test('compare-and-swap prevents lost writes',async()=>{
 const env=environment(); assert.equal((await worker.fetch(req('GET'),env)).status,200);
 assert.equal((await worker.fetch(req('PUT',{version:0,document:doc}),env)).status,200);
 assert.equal((await worker.fetch(req('PUT',{version:0,document:{...doc,title:'stale'}}),env)).status,409);
 const body=await (await worker.fetch(req('GET'),env)).json();assert.equal(body.version,1);assert.equal(body.document.title,doc.title);
});
test('reject malformed, oversized and duplicated rows',async()=>{
 assert.ok(validDocument(doc)); const env=environment();
 for(const invalid of [{...doc,items:[null]},{...doc,items:[...doc.items,...doc.items]},{...doc,title:''}])
  assert.equal((await worker.fetch(req('PUT',{version:0,document:invalid}),env)).status,400);
 const big = new Request('https://example.com/trip',{method:'PUT',headers:{Authorization:`Bearer ${token}`},body:'x'.repeat(70000)});
 assert.equal((await worker.fetch(big,env)).status,413);
});
