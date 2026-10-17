const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
function setup(answer, saved='', framed=false){
 const controls={};
 for(const selector of ['.model','.start','.stop','pre']) controls[selector]={value:'',disabled:false,textContent:''};
 const panel={style:{},querySelector:s=>controls[s]};
 const window={};window.top=framed?{}:window;window.self=window;
 let menu;let prompts=0;const values=new Map([['ksx_deepseek_key',saved]]);
 vm.runInNewContext(fs.readFileSync(__dirname+'/kaoshixing-deepseek.user.js','utf8'),{
 window,document:{getElementById:()=>null,createElement:()=>panel,body:{append(){}}},
 GM_getValue:(k,d)=>values.has(k)?values.get(k):d,GM_setValue:(k,v)=>values.set(k,v),
 GM_registerMenuCommand:(name,callback)=>{menu=callback},prompt:()=>{prompts++;return answer}
 });
 return {controls,values,menu,get prompts(){return prompts}};
}
test('automatically prompts and persists missing key',()=>{
 const app=setup('  local-test-key  ');assert.equal(app.prompts,1);assert.equal(app.values.get('ksx_deepseek_key'),'local-test-key');
});
test('saved key survives reload without another prompt',()=>{
 const app=setup(null,'saved');assert.equal(app.prompts,0);assert.equal(app.values.get('ksx_deepseek_key'),'saved');
});
test('cancel menu leaves existing key untouched',()=>{
 const app=setup(null,'existing');app.menu();assert.equal(app.values.get('ksx_deepseek_key'),'existing');
});
test('menu cannot replace key while running',()=>{
 const app=setup('replacement','existing');app.controls['.start'].disabled=true;app.menu();assert.equal(app.prompts,0);assert.equal(app.values.get('ksx_deepseek_key'),'existing');
});
test('missing key prompts inside exam iframe',()=>{
 const app=setup('test-key','',true);assert.equal(app.prompts,1);assert.equal(app.values.get('ksx_deepseek_key'),'test-key');
});
test('blank menu input clears stored key',()=>{
 const app=setup('','saved');app.menu();assert.equal(app.values.get('ksx_deepseek_key'),'');
});
