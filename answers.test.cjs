const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const context={module:{exports:{}}};
const source=fs.existsSync(__dirname+'/kaoshixing-deepseek.user.js')?fs.readFileSync(__dirname+'/kaoshixing-deepseek.user.js','utf8'):'';
vm.runInNewContext(source,context);
const validate=(...args)=>context.module.exports.validate(...args);
test('multiple answers map to available option identifiers',()=>assert.equal(JSON.stringify(validate({answers:['A','C']},['A','B','C'],true)), '["A","C"]'));
test('reject unknown option instead of clicking',()=>assert.throws(()=>validate({answers:['E']},['A','B'],true)));
test('reject several answers on single choice',()=>assert.throws(()=>validate({answers:['A','B']},['A','B'],false)));
test('reject empty, duplicate and malformed answers',()=>{
 for(const result of [{answers:[]},{answers:['A','A']},{answers:'A'},{answers:[1]},{}]) assert.throws(()=>validate(result,['A','B'],true));
});
