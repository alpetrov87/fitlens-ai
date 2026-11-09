package com.fitlens.ai.data

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

internal fun isUtmbBrowserProfile(url: String, expectedSlug: String): Boolean = runCatching {
    val uri = URI(url)
    val base = URI(uri.scheme, uri.userInfo, uri.host, uri.port, uri.path, null, null).toString()
    utmbProfileSlug(base).substringBefore('.') == expectedSlug.substringBefore('.')
}.getOrDefault(false)

internal data class UtmbBrowserPreview(val score: String?, val results: String?, val categories: Int, val rows: Int)

/** Consumes selected fitness fields only, never full page HTML or browser credentials. */
internal fun parseUtmbBrowserSnapshot(raw: String, page: String, expectedSlug: String): UtmbBrowserPreview {
    require(isUtmbBrowserProfile(page, expectedSlug))
    require(raw.length <= 60_000)
    // The fixed extraction script emits only shallow JSON. Reject arbitrary nested input first.
    var depth = 0; var quoted = false; var escaped = false
    raw.forEach { c ->
        if (quoted) {
            if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
        } else when (c) {
            '"' -> quoted = true
            '{', '[' -> { depth++; require(depth <= 8) }
            '}', ']' -> depth--
        }
    }
    val root = JSONObject(raw)
    val incoming = root.optJSONArray("indexes") ?: JSONArray()
    val safe = JSONArray()
    for (i in 0 until minOf(incoming.length(), 10)) {
        val row = incoming.optJSONObject(i) ?: continue
        val value = (row.opt("index") as? Number)?.toDouble() ?: continue
        val category = row.optString("piCategory")
        if (category in listOf("general", "20k", "50k", "100k", "100m") && value.isFinite() && value in 1.0..1000.0 && value == value.toInt().toDouble()) {
            safe.put(JSONObject().put("piCategory", category).put("index", value.toInt()))
        }
    }
    val score = if (safe.length() > 0) {
        val props = JSONObject().put("performanceIndexes", safe)
        val data = JSONObject().put("props", JSONObject().put("pageProps", props))
        parseUtmbProfile("<script id=__NEXT_DATA__ type=application/json>${data}</script>", expectedSlug).score
    } else null
    val rows = root.optJSONArray("rows") ?: JSONArray()
    val retained = mutableListOf<String>()
    val races = root.optJSONArray("races") ?: JSONArray()
    for (i in 0 until minOf(races.length(), 30)) {
        val row = races.optJSONObject(i)?.let(::utmbRaceRow) ?: continue
        if (retained.sumOf { it.length + 1 } + row.length <= 4000 && row !in retained) retained.add(row)
    }
    for (i in 0 until minOf(rows.length(), 30)) {
        val text = (rows.opt(i) as? String)?.replace(Regex("[\\r\\n\\t]+"), " ")?.trim()?.take(600) ?: continue
        if (text.isNotBlank() && retained.sumOf { it.length + 1 } + text.length <= 4000 && text !in retained) retained.add(text)
    }
    require(score != null || retained.isNotEmpty()) { "No supported UTMB fields" }
    return UtmbBrowserPreview(score, retained.joinToString("\n").takeIf { it.isNotBlank() }, safe.length(), retained.size)
}

/** Browser extraction is read-only, bound to a profile in native code before and after execution. */
internal val UTMB_BROWSER_SCRIPT = """(function(){
    const found=new Map(), visible=new Map();
    const allowed=['general','20k','50k','100k','100m'];
    const valid=n=>typeof n==='number' && Number.isInteger(n) && n>0 && n<=1000;
    try {
        const data=JSON.parse(document.querySelector('script#__NEXT_DATA__')?.textContent || '{}');
        for(const item of (data.props?.pageProps?.performanceIndexes || [])) {
            if(allowed.includes(item.piCategory) && valid(item.index)) found.set(item.piCategory,item.index);
        }
    } catch(e) {}
    const labels=new Map([['GENERAL','general'],['UTMB INDEX','general'],['UTMB® INDEX','general'],['20K','20k'],['50K','50k'],['100K','100k'],['100M','100m']]);
    for(const el of Array.from(document.querySelectorAll('h1,h2,h3,h4,span,p,div')).slice(0,12000)) {
        if(!el.getClientRects().length) continue;
        const label=(el.innerText || '').trim().toUpperCase();
        if(!labels.has(label)) continue;
        let parent=el.parentElement;
        for(let i=0;parent && i<3;i++,parent=parent.parentElement) {
            const text=(parent.innerText || '').replace(/\s+/g,' ').trim();
            if(text.length>70) break;
            const normalized=text.toUpperCase();
            const suffix=normalized.startsWith(label) ? normalized.slice(label.length).trim().replace(/^[:|]\s*/,'') : normalized.endsWith(label) ? normalized.slice(0,-label.length).trim() : '';
            const match=/^\d{1,4}${'$'}/.test(suffix) ? [suffix,suffix] : null;
            if(match && valid(Number(match[1]))) {
                const key=labels.get(label), value=Number(match[1]);
                if(!visible.has(key)) visible.set(key,new Set());
                visible.get(key).add(value); break;
            }
        }
    }
    for(const [key,values] of visible) if(values.size===1) found.set(key,Array.from(values)[0]);
    const races=new Map();
    const norm=x=>(x || '').replace(/\s+/g,' ').trim();
    const scoreLabel=t=>/^(?:UTMB[®\s]*(?:SCORE|INDEX)|RACE SCORE|PERFORMANCE INDEX)${'$'}/i.test(norm(t));
    const raceScoreLabel=t=>/^(?:UTMB[®\s]*SCORE|RACE SCORE)${'$'}/i.test(norm(t));
    const integer=t=>/^[1-9]\d{0,3}${'$'}/.test(norm(t)) && valid(Number(norm(t))) ? Number(norm(t)) : null;
    const labelFor=el=>norm((el.innerText || '')+' '+(el.getAttribute('aria-label') || '')+' '+(el.getAttribute('title') || '')+' '+Array.from(el.querySelectorAll('img[alt]')).map(i=>i.alt).join(' '));
    const isScoreHeader=el=>[el.innerText,el.getAttribute('aria-label'),el.getAttribute('title'),...Array.from(el.querySelectorAll('img[alt]')).map(i=>i.alt),labelFor(el)].some(scoreLabel);
    const raceUrl=a=>{
        try {
            const u=new URL(a.href,document.baseURI);
            if(u.protocol!=='https:' || !['utmb.world','www.utmb.world'].includes(u.hostname) || u.username || u.password || (u.port && u.port!=='443')) return null;
            if(!/^\/(?:[a-z]{2}\/)?(?:utmb-index\/)?races\/[^/]+\/?${'$'}/.test(u.pathname)) return null;
            return u.origin+u.pathname;
        } catch(e) { return null; }
    };
    const scoreIn=node=>{
        const table=node.closest('table,[role=table],[role=grid]');
        const row=node.closest('tr,[role=row]') || node;
        if(table) {
            const heads=Array.from(table.querySelectorAll('th,[role=columnheader]'));
            const col=heads.findIndex(isScoreHeader);
            const cells=Array.from(row.querySelectorAll('td,[role=cell]'));
            if(col>=0 && col<cells.length) { const n=integer(cells[col].innerText); if(n!==null) return n; }
        }
        for(const el of Array.from(node.querySelectorAll('[title],[aria-label],[data-label],span,p,div')).slice(0,300)) {
            if(!el.getClientRects().length) continue;
            const text=norm(el.innerText);
            const direct=text.match(/^(?:UTMB[®\s]*SCORE|RACE SCORE)\s*[:=]?\s*([1-9]\d{0,3})${'$'}/i);
            if(direct && valid(Number(direct[1]))) return Number(direct[1]);
            const attr=norm((el.getAttribute('title') || '')+' '+(el.getAttribute('aria-label') || '')+' '+(el.getAttribute('data-label') || ''));
            if([attr,el.getAttribute('title'),el.getAttribute('aria-label'),el.getAttribute('data-label')].some(raceScoreLabel)) { const n=integer(text); if(n!==null) return n; }
        }
        // Next.js div grids can have a separate header row with aligned cells.
        const parent=node.parentElement;
        if(parent && node.children.length>1) {
            for(const header of Array.from(parent.children).slice(0,10)) {
                if(header===node || header.children.length!==node.children.length) continue;
                const col=Array.from(header.children).findIndex(isScoreHeader);
                if(col>=0) { const n=integer(node.children[col].innerText); if(n!==null) return n; }
            }
        }
        return null;
    };
    const links=Array.from(document.querySelectorAll('a[href*="/races/"]')).filter(a=>a.getClientRects().length && raceUrl(a)).slice(0,150);
    for(const link of links) {
        const url=raceUrl(link), title=norm(link.innerText).slice(0,160);
        if(!title) continue;
        let best=null, node=link;
        for(let i=0;node && i<9;i++,node=node.parentElement) {
            const text=norm(node.innerText);
            if(text.length>2000) break;
            const urls=new Set(Array.from(node.querySelectorAll('a[href*="/races/"]')).map(raceUrl).filter(Boolean));
            if(urls.size>1) break;
            const date=text.match(/\b(20\d{2}[-/.]\d{1,2}[-/.]\d{1,2}|\d{1,2}[/.-]\d{1,2}[/.-]20\d{2}|20\d{2})\b/);
            const times=text.match(/\b\d{1,3}:[0-5]\d(?::[0-5]\d)?\b/g) || [];
            if(!date) continue;
            const score=scoreIn(node);
            if(times.length===0 && score===null) continue;
            const distance=text.match(/\b\d{1,4}(?:[.,]\d{1,2})?\s*(?:km|км)\b/i);
            best={title,date:date[1],score,time:times.length===1?times[0]:'',distance:distance?distance[0]:'',url};
            if(score!==null || node===link.closest('tr,[role=row]')) break;
        }
        if(best && (!races.has(url) || races.get(url).score===null)) races.set(url,best);
        if(races.size>=30) break;
    }
    // Accessible tables/grids can render race names as buttons or plain cells, with no anchor.
    const tables=Array.from(document.querySelectorAll('table,[role=table],[role=grid]')).slice(0,30);
    let tableRows=0;
    for(const table of tables) {
        const heads=Array.from(table.querySelectorAll('th,[role=columnheader]'));
        const texts=h=>[h.innerText,h.getAttribute('title'),h.getAttribute('aria-label'),labelFor(h)].map(norm);
        const nameCol=heads.findIndex(h=>texts(h).some(t=>/^(?:race(?: name)?|event(?: name)?|course|épreuve|състезание)${'$'}/i.test(norm(t))));
        const dateCol=heads.findIndex(h=>texts(h).some(t=>/^(?:date|race date|дата)${'$'}/i.test(norm(t))));
        const scoreCol=heads.findIndex(isScoreHeader);
        if(nameCol<0 || dateCol<0 || scoreCol<0) continue;
        for(const row of Array.from(table.querySelectorAll('tr,[role=row]')).slice(0,100)) {
            if(!row.getClientRects().length) continue;
            const cells=Array.from(row.querySelectorAll('td,[role=cell]'));
            if(cells.length<=Math.max(nameCol,dateCol,scoreCol)) continue;
            tableRows++;
            const title=norm(cells[nameCol].innerText).slice(0,160);
            const date=norm(cells[dateCol].innerText).match(/\b(20\d{2}[-/.]\d{1,2}[-/.]\d{1,2}|\d{1,2}[/.-]\d{1,2}[/.-]20\d{2}|20\d{2})\b/);
            const text=norm(row.innerText), times=text.match(/\b\d{1,3}:[0-5]\d(?::[0-5]\d)?\b/g) || [];
            const score=integer(cells[scoreCol].innerText);
            if(!title || !date || (!times.length && score===null)) continue;
            const link=Array.from(row.querySelectorAll('a[href*="/races/"]')).find(a=>raceUrl(a));
            const url=link?raceUrl(link):'';
            const distance=text.match(/\b\d{1,4}(?:[.,]\d{1,2})?\s*(?:km|км)\b/i);
            const key=url || date[1]+'|'+title+'|'+(distance?distance[0]:'');
            const next={title,date:date[1],score,time:times.length===1?times[0]:'',distance:distance?distance[0]:'',url};
            if(!races.has(key) || races.get(key).score===null) races.set(key,next);
            if(races.size>=30) break;
        }
    }
    // Responsive Next.js div tables often have neither links nor ARIA roles.
    const headerTexts=h=>[h.innerText,h.getAttribute('title'),h.getAttribute('aria-label'),labelFor(h)].map(norm);
    for(const header of Array.from(document.querySelectorAll('div,section,header')).slice(0,4000)) {
        const heads=Array.from(header.children);
        if(heads.length<3 || heads.length>16 || !header.parentElement) continue;
        const nameCol=heads.findIndex(h=>headerTexts(h).some(t=>/^(?:race(?: name)?|event(?: name)?|course|épreuve|състезание)${'$'}/i.test(t)));
        const dateCol=heads.findIndex(h=>headerTexts(h).some(t=>/^(?:date|race date|year|дата|année)${'$'}/i.test(t)));
        const scoreCol=heads.findIndex(h=>isScoreHeader(h) || headerTexts(h).some(t=>/^score${'$'}/i.test(t)));
        if(nameCol<0 || dateCol<0 || scoreCol<0) continue;
        const scopes=[header.parentElement,header.parentElement.parentElement].filter(Boolean);
        const siblings=Array.from(new Set(scopes.flatMap(e=>Array.from(e.children))));
        const candidates=[...siblings,...siblings.flatMap(e=>Array.from(e.children)),...siblings.flatMap(e=>Array.from(e.children).flatMap(x=>Array.from(x.children)))];
        for(const row of candidates.slice(0,300)) {
            if(row===header || row.children.length<heads.length || row.children.length>heads.length+2 || !row.getClientRects().length) continue;
            const cells=Array.from(row.children), title=norm(cells[nameCol].innerText).slice(0,160);
            const date=norm(cells[dateCol].innerText).match(/\b(20\d{2}[-/.]\d{1,2}[-/.]\d{1,2}|\d{1,2}[/.-]\d{1,2}[/.-]20\d{2}|20\d{2})\b/);
            const text=norm(row.innerText), times=text.match(/\b\d{1,3}:[0-5]\d(?::[0-5]\d)?\b/g) || [];
            const score=integer(cells[scoreCol].innerText);
            if(!title || !date || (!times.length && score===null) || text.length>2000) continue;
            tableRows++;
            const link=Array.from(row.querySelectorAll('a[href*="/races/"]')).find(a=>raceUrl(a)), url=link?raceUrl(link):'';
            const distance=text.match(/\b\d{1,4}(?:[.,]\d{1,2})?\s*(?:km|км)\b/i);
            const key=url || date[1]+'|'+title+'|'+(distance?distance[0]:'');
            const next={title,date:date[1],score,time:times.length===1?times[0]:'',distance:distance?distance[0]:'',url};
            if(!races.has(key) || races.get(key).score===null) races.set(key,next);
            if(races.size>=30) break;
        }
        if(races.size>=30) break;
    }
    // Mobile result list: MONTH DAY | race name/subtitle | score | ellipsis menu.
    // This layout has no table headers, finish time, year or race link.
    const monthDay=/^(JANUARY|FEBRUARY|MARCH|APRIL|MAY|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)\s+([1-9]|[12]\d|3[01])${'$'}/i;
    const shown=e=>e && (e.getClientRects().length || Array.from(e.children || []).some(c=>c.getClientRects().length));
    const dates=Array.from(document.querySelectorAll('div,span,p,time')).slice(0,16000).filter(e=>shown(e) && monthDay.test(norm(e.innerText)));
    const boundsFor=e=>{
        const box=e.getBoundingClientRect();
        if(box.width>0 && box.height>0) return box;
        const boxes=Array.from(e.children).flatMap(c=>[c,...Array.from(c.children)]).slice(0,40).map(c=>c.getBoundingClientRect()).filter(b=>b.width>0 && b.height>0);
        if(!boxes.length) return box;
        const left=Math.min(...boxes.map(b=>b.left)),right=Math.max(...boxes.map(b=>b.right)),top=Math.min(...boxes.map(b=>b.top)),bottom=Math.max(...boxes.map(b=>b.bottom));
        return {left,right,top,bottom,width:right-left,height:bottom-top};
    };
    const rowYear=row=>{
        let block=row;
        for(let depth=0;block && depth<4;depth++,block=block.parentElement) {
            const prev=block.previousElementSibling;
            if(prev && /^(?:H[1-6])${'$'}/.test(prev.tagName) && /^20\d{2}${'$'}/.test(norm(prev.innerText))) return norm(prev.innerText);
        }
        return '';
    };
    const isMenu=e=>{
        if(!e) return false;
        const t=norm(e.innerText);
        return /^[.·•⋯…]+${'$'}/.test(t) || e.matches('button,[role=button],[aria-haspopup]') || e.querySelectorAll('button,[role=button],[aria-haspopup],svg').length>0;
    };
    for(const dateNode of dates) {
        let row=dateNode.parentElement;
        for(let depth=0;row && depth<5;depth++,row=row.parentElement) {
            let cells=Array.from(row.children).filter(shown);
            if(cells.length===2 && cells[0].contains(dateNode)) cells=[cells[0],...Array.from(cells[1].children).filter(shown)];
            if(cells.length<3 || cells.length>5) continue;
            const dateCol=cells.findIndex(c=>c===dateNode || c.contains(dateNode));
            if(dateCol<0 || dateCol>1 || dateCol+2>=cells.length) continue;
            const nameCell=cells[dateCol+1], scoreCell=cells[dateCol+2], menuCell=cells[dateCol+3] || scoreCell;
            if(!isMenu(menuCell) || cells.slice(dateCol+3).some(c=>integer(norm(c.innerText))!==null)) continue;
            const within=Array.from(row.querySelectorAll('div,span,p,time')).filter(e=>shown(e) && monthDay.test(norm(e.innerText)));
            if(new Set(within.map(e=>norm(e.innerText).toUpperCase())).size>1) break;
            const bounds=boundsFor(row), dateBounds=boundsFor(cells[dateCol]), nameBounds=boundsFor(nameCell), scoreBounds=boundsFor(scoreCell);
            if(bounds.width<180 || bounds.height<=0 || nameBounds.left<=dateBounds.left || scoreBounds.left<=nameBounds.left) continue;
            if(Math.abs((scoreBounds.top+scoreBounds.height/2)-(dateBounds.top+dateBounds.height/2))>Math.max(dateBounds.height,scoreBounds.height,80)) continue;
            const title=(nameCell.innerText || '').split(/\n+/).map(norm).filter(Boolean).filter((x,i,a)=>a.indexOf(x)===i).join(' · ').slice(0,160);
            if(!title || /^\d+${'$'}/.test(title) || monthDay.test(title)) continue;
            const text=norm(scoreCell.innerText).replace(/[.·•⋯…]+${'$'}/,'').trim();
            const score=integer(text);
            if(score===null && text && !/^(?:—|-|\?|0|N\/A|unavailable|not available|not valid|unavailable UTMB (?:index|score))${'$'}/i.test(text)) continue;
            const year=rowYear(row), date=norm(dateNode.innerText).toUpperCase()+(year?', '+year:'');
            const link=Array.from(nameCell.querySelectorAll('a[href*="/races/"]')).find(a=>raceUrl(a)), url=link?raceUrl(link):'';
            const distance=title.match(/\b\d{1,4}(?:[.,]\d{1,2})?\s*(?:km|км)\b/i);
            const key=url || date+'|'+title+'|'+(distance?distance[0]:'');
            const next={title,date,score,time:'',distance:distance?distance[0]:'',url};
            if(!races.has(key) || races.get(key).score===null) races.set(key,next);
            tableRows++;
            break;
        }
        if(races.size>=30) break;
    }
    return JSON.stringify({indexes:Array.from(found,([piCategory,index])=>({piCategory,index})),races:Array.from(races.values()).slice(0,30),rows:[],diagnostics:{raceLinks:links.length,tableRows}});
})();"""

internal fun mergeUtmbCategoryScore(previous: String, incoming: String?): String? {
    if (incoming == null) return previous.takeIf { it.isNotBlank() }
    val labels = listOf("UTMB Index", "20K", "50K", "100K", "100M")
    fun value(score: String, label: String): String? = Regex("(?:^| · )${Regex.escape(label)}: ([1-9][0-9]{0,3})(?= · |${'$'})")
        .find(score)?.groupValues?.get(1)
    return labels.joinToString(" · ") { label -> "$label: ${value(incoming, label) ?: value(previous, label) ?: "—"}" }
}


/** Scroll only after an explicit read request; do not click account actions or fetch private endpoints. */
internal val UTMB_RESULTS_SCROLL_SCRIPT = """(function(){
    const titles=Array.from(document.querySelectorAll('h2,h3,h4'));
    const heading=titles.find(e=>e.getClientRects().length && /^(?:race results|results|résultats|race history|състезания|резултати)${'$'}/i.test((e.innerText || '').trim()));
    if(heading) heading.scrollIntoView({block:'start',behavior:'instant'});
})();"""

internal val UTMB_RESULTS_NEXT_SCROLL_SCRIPT = """(function(){
    const candidates=Array.from(document.querySelectorAll('main,section,div')).slice(0,3000).filter(e=>{
        const style=getComputedStyle(e);
        return e.getClientRects().length && /auto|scroll/.test(style.overflowY) && e.clientHeight>150 && e.scrollHeight>e.clientHeight+10;
    }).sort((a,b)=>b.clientHeight-a.clientHeight);
    if(candidates[0]) candidates[0].scrollTop+=Math.max(150,candidates[0].clientHeight*0.8);
})();"""

internal fun utmbReadCounts(raw: String): Pair<Int, Int> {
    require(raw.length <= 60_000)
    fun count(key: String): Int = Regex("\"$key\"\\s*:\\s*([0-9]{1,5})(?![0-9])").find(raw)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0,10000) ?: 0
    return count("raceLinks") to count("tableRows")
}
