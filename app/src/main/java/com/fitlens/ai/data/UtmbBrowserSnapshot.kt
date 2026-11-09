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
        const table=node.closest('table,[role=table]');
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
            if(!date || times.length===0) continue;
            const score=scoreIn(node);
            const distance=text.match(/\b\d{1,4}(?:[.,]\d{1,2})?\s*(?:km|км)\b/i);
            best={title,date:date[1],score,time:times.length===1?times[0]:'',distance:distance?distance[0]:'',url};
            if(score!==null || node===link.closest('tr,[role=row]')) break;
        }
        if(best && (!races.has(url) || races.get(url).score===null)) races.set(url,best);
        if(races.size>=30) break;
    }
    return JSON.stringify({indexes:Array.from(found,([piCategory,index])=>({piCategory,index})),races:Array.from(races.values()),rows:[]});
})();"""

internal fun mergeUtmbCategoryScore(previous: String, incoming: String?): String? {
    if (incoming == null) return previous.takeIf { it.isNotBlank() }
    val labels = listOf("UTMB Index", "20K", "50K", "100K", "100M")
    fun value(score: String, label: String): String? = Regex("(?:^| · )${Regex.escape(label)}: ([1-9][0-9]{0,3})(?= · |${'$'})")
        .find(score)?.groupValues?.get(1)
    return labels.joinToString(" · ") { label -> "$label: ${value(incoming, label) ?: value(previous, label) ?: "—"}" }
}
