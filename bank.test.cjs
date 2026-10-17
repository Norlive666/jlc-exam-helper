const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
function setup(values=new Map()){
const module={exports:{}};
 vm.runInNewContext(fs.readFileSync(__dirname+'/kaoshixing-deepseek.user.js','utf8'),{module,GM_getValue:(k,d)=>values.has(k)?values.get(k):d,GM_setValue:(k,v)=>values.set(k,v)});
 return {api:module.exports,values};
}
const q={text:'题干',images:[],multiple:false,options:[{id:'A',text:'甲',images:[]},{id:'B',text:'乙',images:[]}]};
test('AI miss is cached and next call avoids API',async()=>{
 const {api}=setup();let calls=0;
 const ask=async()=>{calls++;return {answers:['A']}};
 const first=await api.answerQuestion(q,ask,()=>false);
 const second=await api.answerQuestion(q,ask,()=>false);
 assert.equal(first.source,'AI');assert.equal(second.source,'题库');assert.equal(calls,1);
 assert.equal(JSON.stringify(second.answers),'["A"]');
});
test('options, type and image changes cannot reuse cached letters',async()=>{
 const {api}=setup();let calls=0;const ask=async()=>{calls++;return {answers:['A']}};
 await api.answerQuestion(q,ask,()=>false);
 for(const change of [{text:'另一题'},{multiple:true},{images:['image-2']},{options:[...q.options].reverse()}]) await api.answerQuestion({...q,...change},ask,()=>false);
 assert.equal(calls,5);
});
test('invalid AI response is never cached',async()=>{
 const {api,values}=setup();await assert.rejects(()=>api.answerQuestion(q,async()=>({answers:['X']}),()=>false));assert.equal(values.size,0);
});
test('stopped response neither caches nor returns an answer',async()=>{
 const {api,values}=setup();await assert.rejects(()=>api.answerQuestion(q,async()=>({answers:['A']}),()=>true));assert.equal(values.size,0);
});

test('bank persists across independent script loads',async()=>{
 const first=setup();await first.api.answerQuestion(q,async()=>({answers:['B']}),()=>false);
 const next=setup(first.values);
 const result=await next.api.answerQuestion(q,async()=>{throw new Error('must not request')},()=>false);
 assert.equal(result.source,'题库');assert.equal(JSON.stringify(result.answers),'["B"]');
});
test('failed API call never creates a bank entry',async()=>{
 const {api,values}=setup();await assert.rejects(()=>api.answerQuestion(q,async()=>{throw new Error('API failure')},()=>false));assert.equal(values.size,0);
});
