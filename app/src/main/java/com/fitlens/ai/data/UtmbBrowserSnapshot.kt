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
    const rows=[];
    const links=Array.from(document.querySelectorAll('a[href*="/utmb-index/races/"]')).filter(a=>a.getClientRects().length).slice(0,100);
    for(const link of links) {
        let node=link;
        for(let i=0;node && i<6;i++,node=node.parentElement) {
            const text=(node.innerText || '').replace(/\s+/g,' ').trim();
            if(text.length>900) break;
            if(node.querySelectorAll('a[href*="/utmb-index/races/"]').length>1) break;
            const table=node.closest('table,[role=table]');
            const header=table ? Array.from(table.querySelectorAll('th,[role=columnheader]')).map(x=>x.innerText).join(' | ').slice(0,250) : '';
            const hasScore=/utmb.*(?:score|index)|performance.*index|race.*score/i.test(text+' '+header);
            if(hasScore && /\b20\d{2}\b/.test(text) && /\b\d{1,3}:\d{2}(?::\d{2})?\b/.test(text)) {
                const row=(header ? header+' — ' : '')+text;
                if(row.length<=600 && !rows.includes(row) && rows.join('\n').length+row.length<4000) rows.push(row);
                break;
            }
        }
        if(rows.length>=30) break;
    }
    return JSON.stringify({indexes:Array.from(found,([piCategory,index])=>({piCategory,index})),rows});
})();"""

internal fun mergeUtmbCategoryScore(previous: String, incoming: String?): String? {
    if (incoming == null) return previous.takeIf { it.isNotBlank() }
    val labels = listOf("UTMB Index", "20K", "50K", "100K", "100M")
    fun value(score: String, label: String): String? = Regex("(?:^| · )${Regex.escape(label)}: ([1-9][0-9]{0,3})(?= · |${'$'})")
        .find(score)?.groupValues?.get(1)
    return labels.joinToString(" · ") { label -> "$label: ${value(incoming, label) ?: value(previous, label) ?: "—"}" }
}
