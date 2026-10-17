const {test}=require('node:test');
const assert=require('node:assert/strict');
const api=require('./kaoshixing-deepseek.user.js');
const image='https://img1.kaoshixing.com/ksx_prod/536835/image/example.jpg';
const question={text:'题干',images:[image],multiple:true,options:[{id:'A',text:'选项A',images:[image]},{id:'B',text:'选项B',images:[]}]};
test('question and option images stay associated with their text',()=>{
 const content=api.buildContent(question);
 assert.deepEqual(content.map(c=>c.type),['text','image_url','text','image_url','text']);
 assert.match(content[0].text,/题干/);assert.match(content[2].text,/A/);
 assert.equal(content[1].image_url.url,image);assert.equal(content[3].image_url.url,image);
});
test('reject unavailable or unsupported image URLs rather than omit images',()=>{
 for(const url of ['', 'blob:123','http://img1.kaoshixing.com/a.png','https://localhost/a.png','https://example.com/a.png'])
   assert.throws(()=>api.buildContent({...question,images:[url]}));
});
test('text-only choice remains usable',()=>{
 const content=api.buildContent({...question,images:[],options:[{id:'A',text:'选项A',images:[]}]});
 assert.ok(content.every(c=>c.type==='text'));assert.match(content[1].text,/选项A/);
});
