// Turns published posts into real HTML pages.
//
// The blog's reading UI is Compose, which draws to a canvas. Googlebot runs the
// JavaScript, but after it runs there is still no text in the DOM — only pixels
// — so there is nothing to index no matter how patient the crawler is. A
// <noscript> block does not rescue it either: noscript content is only parsed
// when scripting is off, and Google renders with it on.
//
// So the article pages are written out as HTML here and Firebase serves them at
// the same URLs. A static file wins over a rewrite, and cleanUrls maps
// blog/<slug>.html onto /blog/<slug>, so the crawler and anyone arriving from
// search get 30 KB of text instead of 12.4 MB of WebAssembly. The Compose app
// still owns /blog, search, categories and all of /blog/admin, and its own
// in-app navigation to an article never touches these files.
//
// Run: deno run -A render-blog.ts        (from the landing directory)

const ORIGIN = "https://odoapp.in"
const PLAY = "https://play.google.com/store/apps/details?id=com.hopcape.odo&referrer=utm_source%3Dblog"

// ── config, from the same file the app is built with ─────────────────────────

// The environment wins, because a CI runner has no local.properties and should
// not be given one — the values arrive as repository secrets. Falling back to
// the file keeps `deno run -A render-blog.ts` working on a laptop with no setup.
const fromFile = async (key: string) => {
  try {
    const properties = await Deno.readTextFile("../local.properties")
    return properties.split("\n").find((line) => line.startsWith(`${key}=`))
      ?.slice(key.length + 1).trim() ?? ""
  } catch {
    return ""
  }
}

const SUPABASE = Deno.env.get("SUPABASE_URL") || await fromFile("supabase.url")
const ANON = Deno.env.get("SUPABASE_ANON_KEY") || await fromFile("supabase.anonKey")
if (!SUPABASE || !ANON) {
  console.error("Set SUPABASE_URL and SUPABASE_ANON_KEY, or run beside a local.properties that has them")
  Deno.exit(1)
}

// ── the posts ────────────────────────────────────────────────────────────────

type Run = { text: string; bold?: boolean; italic?: boolean }
type Block = {
  type: string
  id?: string
  text?: string
  label?: string
  runs?: Run[]
  heading?: string
  body?: string
  cta?: string
  link?: string
  screenshot?: string | null
}
type Post = {
  slug: string
  title: string
  dek: string
  body: Block[]
  seo_title: string
  meta_description: string
  reading_minutes: number
  published_on: string | null
  category_slug: string | null
  author: { name: string; slug: string; bio: string } | null
  category: { name: string; slug: string } | null
}

// Anonymous, deliberately. This reads exactly what a stranger can read, so a
// draft cannot leak into a static page even by mistake — the row never arrives.
const response = await fetch(
  `${SUPABASE}/rest/v1/blog_posts?select=*,author:blog_authors(*),category:blog_categories(*)` +
    `&order=published_on.desc`,
  { headers: { apikey: ANON } },
)
if (!response.ok) {
  console.error(`could not read posts: ${response.status} ${await response.text()}`)
  Deno.exit(1)
}
const posts: Post[] = await response.json()

// ── text to markup ───────────────────────────────────────────────────────────

const escape = (value: string) =>
  value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;")

const runsToHtml = (runs: Run[] = []) =>
  runs.map((run) => {
    let html = escape(run.text)
    if (run.italic) html = `<em>${html}</em>`
    if (run.bold) html = `<strong>${html}</strong>`
    return html
  }).join("")

const runsToText = (runs: Run[] = []) => runs.map((run) => run.text).join("")

const blockToHtml = (block: Block): string => {
  switch (block.type) {
    case "paragraph":
      return `<p>${runsToHtml(block.runs)}</p>`
    case "section":
      return `<h2 id="${escape(block.id ?? "")}">${escape(block.text ?? "")}</h2>`
    // Carries nothing, so there is nothing to escape.
    case "divider":
      return `<hr class="rule">`
    case "callout":
      return `<aside class="callout">` +
        `<p class="eyebrow warning">${escape(block.label ?? "")}</p>` +
        `<p>${runsToHtml(block.runs)}</p></aside>`
    case "showcase": {
      // Blank means the Play listing, the same fallback the app applies.
      const href = (block.link ?? "").trim() || PLAY
      const shot = (block.screenshot ?? "").trim()
      return `<div class="action">` +
        `<h3>${escape(block.heading ?? "")}</h3>` +
        `<p>${escape(block.body ?? "")}</p>` +
        `<p><a class="cta" href="${escape(href)}" rel="nofollow">${escape(block.cta ?? "")}</a></p>` +
        (shot ? `<img src="${escape(shot)}" alt="${escape(block.heading ?? "")}" loading="lazy">` : "") +
        `</div>`
    }
    default:
      // Same rule as the app: a block this version does not understand costs the
      // article one block, not the page.
      return ""
  }
}

const wordsOf = (post: Post) =>
  post.body.map((block) =>
    block.type === "section" ? block.text ?? "" :
    block.type === "showcase" ? `${block.heading ?? ""} ${block.body ?? ""}` :
    runsToText(block.runs)
  ).join(" ")

// ── the page ─────────────────────────────────────────────────────────────────

const STYLE = `
:root{--bg:#000;--surface:#0C0C0C;--raised:#141414;--border:#1F1F1F;--text:#fff;
--dim:#9CA3AF;--muted:#6B7280;--warning:#D97706;
--font:Gotham, Inter, 'Helvetica Neue', Arial, sans-serif}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font-family:var(--font);
-webkit-font-smoothing:antialiased;line-height:1.6}
a{color:inherit}
header.site{border-bottom:1px solid var(--border);padding:18px 24px;display:flex;
gap:24px;align-items:center;font-size:15px}
header.site .mark{font-weight:700;letter-spacing:.18em}
header.site nav a{color:var(--dim);text-decoration:none;margin-right:18px}
header.site nav a:hover{color:var(--text)}
main{max-width:720px;margin:0 auto;padding:56px 24px 80px}
.eyebrow{font-size:12px;letter-spacing:.14em;text-transform:uppercase;color:var(--muted);
margin:0 0 10px}
.eyebrow.warning{color:var(--warning)}
.rule{border:0;border-top:1px solid var(--border);margin:28px 0}
h1{font-size:44px;line-height:1.12;letter-spacing:-.02em;margin:0 0 16px}
.dek{font-size:19px;color:var(--dim);margin:0 0 28px}
.byline{display:flex;align-items:center;gap:12px;color:var(--muted);font-size:14px;
padding-bottom:28px;border-bottom:1px solid var(--border);margin-bottom:36px}
.byline .who{color:var(--text)}
article h2{font-size:27px;line-height:1.25;margin:44px 0 14px;letter-spacing:-.01em}
article p{font-size:17.5px;margin:0 0 20px}
.callout{border-left:4px solid var(--warning);background:var(--surface);
padding:18px 22px;margin:32px 0}
.callout p:last-child{margin:0;color:var(--dim);font-size:16px}
.action{background:var(--raised);border-radius:18px;padding:28px;margin:36px 0}
.action h3{font-size:24px;margin:0 0 12px}
.action p{color:var(--dim);font-size:16.5px}
.action img{width:100%;border-radius:12px;margin-top:6px}
.cta{display:inline-block;background:#fff;color:#000;text-decoration:none;
font-weight:600;padding:12px 22px;border-radius:999px;font-size:15px}
.next{border-top:1px solid var(--border);margin-top:56px;padding-top:28px}
.next a{display:block;text-decoration:none;padding:14px 0;border-bottom:1px solid var(--border)}
.next a:hover .t{text-decoration:underline}
.next .t{font-size:19px}
.next .d{color:var(--muted);font-size:15px;margin-top:4px}
footer.site{border-top:1px solid var(--border);padding:24px;color:var(--muted);
font-size:14px;display:flex;justify-content:space-between;flex-wrap:wrap;gap:12px}
footer.site a{color:var(--muted)}
@media (max-width:640px){h1{font-size:33px}main{padding:36px 20px 60px}}
`.trim()

const page = (post: Post, others: Post[]) => {
  const url = `${ORIGIN}/blog/${post.slug}`
  const title = (post.seo_title || `${post.title} — Odo`).trim()
  const description = (post.meta_description || post.dek).trim()
  const author = post.author?.name ?? "Odo"
  const published = post.published_on ?? ""

  const readNext = others.filter((other) => other.slug !== post.slug).slice(0, 3)

  const schema = {
    "@context": "https://schema.org",
    "@type": "Article",
    headline: post.title,
    description,
    datePublished: published,
    author: { "@type": "Person", name: author },
    publisher: { "@type": "Organization", name: "Odo" },
    mainEntityOfPage: url,
    wordCount: wordsOf(post).split(/\s+/).filter(Boolean).length,
  }

  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>${escape(title)}</title>
<meta name="description" content="${escape(description)}">
<link rel="canonical" href="${url}">
<meta property="og:type" content="article">
<meta property="og:site_name" content="Odo">
<meta property="og:title" content="${escape(post.title)}">
<meta property="og:description" content="${escape(description)}">
<meta property="og:url" content="${url}">
<meta name="twitter:card" content="summary">
<meta name="twitter:title" content="${escape(post.title)}">
<meta name="twitter:description" content="${escape(description)}">
${published ? `<meta property="article:published_time" content="${published}">` : ""}
<meta property="article:author" content="${escape(author)}">
<meta name="theme-color" content="#000000">
<script type="application/ld+json">${JSON.stringify(schema)}</script>
<style>${STYLE}</style>
</head>
<body>
<header class="site">
  <a class="mark" href="/" style="text-decoration:none">O D O</a>
  <nav><a href="/blog">Blog</a><a href="/">Get Odo</a></nav>
</header>
<main>
  ${post.category ? `<p class="eyebrow">${escape(post.category.name)}</p>` : ""}
  <h1>${escape(post.title)}</h1>
  <p class="dek">${escape(post.dek)}</p>
  <p class="byline"><span class="who">${escape(author)}</span>
    <span>${escape(published)} · ${post.reading_minutes} min read</span></p>
  <article>
${post.body.map(blockToHtml).filter(Boolean).map((html) => `    ${html}`).join("\n")}
  </article>
${readNext.length ? `  <section class="next">
    <p class="eyebrow">Read next</p>
${readNext.map((other) =>
  `    <a href="/blog/${other.slug}"><div class="t">${escape(other.title)}</div>` +
  `<div class="d">${escape(other.dek)}</div></a>`).join("\n")}
  </section>` : ""}
</main>
<footer class="site">
  <span>Odo — your car's whole record, in one place.</span>
  <span><a href="/legal/privacy">Privacy</a> · <a href="/legal/terms">Terms</a></span>
</footer>
</body>
</html>
`
}

// ── write ────────────────────────────────────────────────────────────────────

for (const post of posts) {
  await Deno.writeTextFile(`public/blog/${post.slug}.html`, page(post, posts))
  console.log(`  /blog/${post.slug}`)
}

const sitemap = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url><loc>${ORIGIN}/</loc><priority>1.0</priority></url>
  <url><loc>${ORIGIN}/blog</loc><priority>0.8</priority></url>
${posts.map((post) =>
  `  <url><loc>${ORIGIN}/blog/${post.slug}</loc>` +
  (post.published_on ? `<lastmod>${post.published_on}</lastmod>` : "") +
  `<priority>0.7</priority></url>`).join("\n")}
</urlset>
`
await Deno.writeTextFile("public/sitemap.xml", sitemap)

// The CMS is not for readers and not for crawlers. It is behind a sign-in
// either way, but a crawler wasting requests on it helps nobody.
await Deno.writeTextFile(
  "public/robots.txt",
  `User-agent: *\nAllow: /\nDisallow: /blog/admin\n\nSitemap: ${ORIGIN}/sitemap.xml\n`,
)

console.log(`\n${posts.length} pages, sitemap.xml, robots.txt`)
