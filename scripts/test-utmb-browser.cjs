const fs=require('fs'),vm=require('vm'),assert=require('assert');
const path=require('path');
const kotlin=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/fitlens/ai/data/UtmbBrowserSnapshot.kt'),'utf8');
const source=kotlin.split('internal val UTMB_BROWSER_SCRIPT = """')[1].split('"""')[0].replaceAll("${'$'}",'$');
function node(text='',attrs={}) {return {innerText:text,children:[],parentElement:null,links:[],cells:[],heads:[],nodes:[],table:null,row:null,
 getClientRects:()=>[1],getAttribute:k=>attrs[k]||null,
 querySelectorAll(q){if(q.includes('img[alt]'))return [];if(q.startsWith('th'))return this.heads;if(q.startsWith('td'))return this.cells;if(q.startsWith('a[href'))return this.links;return this.nodes;},
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
 row.links=[link,duplicate];table.links=[link,duplicate];
 if(mode==='table'){row.table=table;row.row=row;link.row=row;duplicate.row=row;table.table=table;}
 if(mode==='label')row.nodes=[node(`UTMB Score: ${score}`)];
 if(mode==='overall')row.nodes=[node('UTMB Index: 339')];
 const state={props:{pageProps:{performanceIndexes:[{piCategory:'general',index:339}],password:'NEVER_EXPORT'}}};
 const document={baseURI:'https://utmb.world/en/runner/123.sample.runner',querySelector:()=>({textContent:JSON.stringify(state)}),querySelectorAll:q=>q.startsWith('a[href')?[link,duplicate]:[]};
 return JSON.parse(vm.runInNewContext(source,{document,URL}));
}
const table=fixture();assert.equal(table.races.length,1);assert.equal(table.races[0].score,312);assert.equal(table.races[0].distance,'27 km');assert.equal(table.races[0].time,'7:33:47');
assert.equal(fixture(288,'label').races[0].score,288);
assert.equal(fixture(312,'grid').races[0].score,312);
assert.equal(fixture('Locked').races[0].score,null);
assert.equal(fixture('Locked','overall').races[0].score,null);
assert.equal(fixture(312,'table',true).races.length,0);
assert(!JSON.stringify(table).includes('NEVER_EXPORT'));
console.log('UTMB race fixtures passed: labeled table scores vs rank, duplicate links, cards, locked scores, off-site rejection, no state secrets');
