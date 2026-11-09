const fs=require('fs'),vm=require('vm'),assert=require('assert');
const path=require('path');
const kotlin=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/fitlens/ai/data/UtmbBrowserSnapshot.kt'),'utf8');
const source=kotlin.split('internal val UTMB_BROWSER_SCRIPT = """')[1].split('"""')[0].replaceAll("${'$'}",'$');
function node(text='',attrs={}) {return {innerText:text,children:[],parentElement:null,links:[],cells:[],heads:[],nodes:[],table:null,row:null,
 getClientRects:()=>[1],getAttribute:k=>attrs[k]||null,
 querySelectorAll(q){if(q.includes('img[alt]'))return [];if(q.startsWith('th'))return this.heads;if(q.startsWith('tr'))return this.rows||[];if(q.startsWith('td'))return this.cells;if(q.startsWith('a[href'))return this.links;return this.nodes;},
 closest(q){return q.startsWith('table')?this.table:this.row;}};}
function fixture(score=312,mode='table',offsite=false){
 const url=offsite?'https://evil.test/utmb-index/races/123':'https://utmb.world/utmb-index/races/123.example.2026';
 const link=node('Example Trail'), duplicate=node('Example Trail');link.href=url;duplicate.href=url;
 const row=node(`2026-10-03 Example Trail 27 km 7:33:47 place 237 ${mode==='label'?'UTMB Score: ':''}${score}`);
 const table=node(row.innerText); const header=node();
 const heads=['Date','Race','Distance','Time','Place','UTMB Score'].map(t=>node(t,{title:t}));
 row.cells=['2026-10-03','Example Trail','27 km','7:33:47','237',String(score)].map(t=>node(t));
 row.children=row.cells;header.children=heads;table.children=[header,row];table.heads=heads;table.cells=row.cells;
 row.parentElement=table;link.parentElement=row;duplicate.parentElement=row;
 row.links=[link,duplicate];table.links=[link,duplicate];table.rows=[row];header.parentElement=table;
 if(mode==='table'){row.table=table;row.row=row;link.row=row;duplicate.row=row;table.table=table;}
 if(mode==='label')row.nodes=[node(`UTMB Score: ${score}`)];
 if(mode==='overall')row.nodes=[node('UTMB Index: 339')];
 if(mode==='no-link-wrapped-grid'){const outer=node(),headWrap=node(),bodyWrap=node();outer.children=[headWrap,bodyWrap];headWrap.parentElement=outer;bodyWrap.parentElement=outer;headWrap.children=[header];bodyWrap.children=[row];header.parentElement=headWrap;row.parentElement=bodyWrap;}
 if(mode==='no-link-extra-action'){row.children=[...row.children,node('Details')];}
 if(mode==='no-link-no-time'){row.innerText=row.innerText.replace('7:33:47','7h33m47s');row.cells[3].innerText='7h33m47s';}
 const state={props:{pageProps:{performanceIndexes:[{piCategory:'general',index:339}],password:'NEVER_EXPORT'}}};
 if(mode.startsWith('no-link')){row.links=[];table.links=[];}
 const document={baseURI:'https://utmb.world/en/runner/123.sample.runner',querySelector:()=>({textContent:JSON.stringify(state)}),querySelectorAll:q=>q.startsWith('a[href')?(mode.startsWith('no-link')?[]:[link,duplicate]):q.startsWith('table')?(['no-link-table','no-link-no-time'].includes(mode)?[table]:[]):q==='div,section,header'?(['no-link-grid','no-link-wrapped-grid','no-link-extra-action'].includes(mode)?[header]:[]):[]};
 return JSON.parse(vm.runInNewContext(source,{document,URL}));
}
const table=fixture();assert.equal(table.races.length,1);assert.equal(table.races[0].score,312);assert.equal(table.races[0].distance,'27 km');assert.equal(table.races[0].time,'7:33:47');
assert.equal(fixture(288,'label').races[0].score,288);
assert.equal(fixture(312,'grid').races[0].score,312);
assert.equal(fixture(312,'no-link-table').races[0].score,312);
assert.equal(fixture(312,'no-link-table').races[0].url,'');
assert.equal(fixture(312,'no-link-grid').races[0].score,312);
assert.equal(fixture(312,'no-link-wrapped-grid').races[0].score,312);
assert.equal(fixture(312,'no-link-no-time').races[0].score,312);
assert.equal(fixture(312,'no-link-extra-action').races[0].score,312);
assert.equal(fixture('Locked').races[0].score,null);
assert.equal(fixture('Locked','overall').races[0].score,null);
assert.equal(fixture(312,'table',true).races.length,0);
assert(!JSON.stringify(table).includes('NEVER_EXPORT'));
console.log('UTMB race fixtures passed: labeled table scores vs rank, duplicate links, cards, locked scores, off-site rejection, no state secrets');

// Mobile layout matching the submitted screenshot: no year, time, header or anchor.
function mobileFixture(withYear=false,ambiguous=false,placeholder='') {
 const dates=['NOVEMBER 7','OCTOBER 4','SEPTEMBER 26','SEPTEMBER 13','JULY 19','JUNE 6'];
 const scores=[349,305,333,310,337,null], elements=[], container=node();
 const year=node('2026');year.tagName='H3';
 function attach(parent,children){parent.children=children;children.forEach(c=>c.parentElement=parent);return parent;}
 function all(e){return e.children.flatMap(c=>[c,...all(c)]);}
 function enhance(e,left=0,width=100) {
  e.tagName=e.tagName||'DIV';e.getBoundingClientRect=()=>({left,width,height:100,top:0,right:left+width,bottom:100});
  e.contains=x=>e===x || all(e).includes(x);e.matches=q=>e.tagName==='BUTTON' && q.includes('button');
  const old=e.querySelectorAll.bind(e);
  e.querySelectorAll=q=>q==='div,span,p,time'?all(e):q.includes('button')?all(e).filter(c=>c.tagName==='BUTTON'||c.tagName==='SVG'):old(q);
  return e;
 }
 const rows=dates.map((date,i)=>{
  const d=enhance(node(date),10,160), leaf=enhance(node(date),10,160);attach(d,[leaf]);elements.push(d,leaf);
  const name=enhance(node(`Example trail ${i+1}\nExample event`),200,300);
  const score=enhance(node(scores[i]===null?placeholder:String(scores[i])),580,60);
  const menu=enhance(node('⋯'),660,30);menu.tagName='BUTTON';
  const row=enhance(node(`${date} ${name.innerText} ${score.innerText} ⋯`),0,720);
  attach(row,[d,name,score,menu]);if(withYear)row.previousElementSibling=year;
  if(ambiguous){const distance=enhance(node('25'),520,40);attach(row,[d,name,distance,score,menu]);}
  return row;
 });
 attach(container,rows);
 const state={props:{pageProps:{performanceIndexes:[{piCategory:'general',index:339}]}}};
 const document={baseURI:'https://utmb.world/en/runner/123.sample.runner',querySelector:()=>({textContent:JSON.stringify(state)}),querySelectorAll:q=>q==='div,span,p,time'?elements:[]};
 return JSON.parse(vm.runInNewContext(source,{document,URL}));
}
const mobile=mobileFixture();
assert.equal(mobile.races.length,6);
assert.deepStrictEqual(mobile.races.map(r=>r.score),[349,305,333,310,337,null]);
assert.equal(mobile.races[0].date,'NOVEMBER 7');
assert.equal(mobile.races[0].time,'');
assert.equal(mobileFixture(true).races[0].date,'NOVEMBER 7, 2026');
assert.equal(mobileFixture(false,true).races.length,0);
assert.equal(mobileFixture(false,false,'?').races.length,6);
assert.equal(mobileFixture(false,false,'0').races[5].score,null);
console.log('UTMB mobile screenshot-layout fixtures passed: month/day, five scores and one unavailable, no invented year/time, ambiguous numeric columns rejected');
