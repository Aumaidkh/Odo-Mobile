// Mounts the app under /blog — in production, and locally too.
//
// Two problems, one fix.
//
// The bundle is loaded by a `<script src>` in index.html. Left relative, the
// browser resolves it against whatever path the reader arrived at:
// `/blog/an-article` finds it, `/blog/category/fuel` asks for
// `/blog/category/odo-blog.js` and gets the history fallback's index.html back
// instead of JavaScript. The app then never starts and the page sits on its boot
// placeholder. An absolute src fixes that, but only if it is the same absolute
// src everywhere — which is why the dev server serves from /blog/ as well. Local
// and deployed are then the same URL shape, and the router's base-path handling
// is exercised on every run rather than only in production.
//
// `publicPath` is that same value, because it is where webpack's own runtime
// fetches split chunks from and it has to agree with the script tag.
//
// This lives here rather than in the Kotlin DSL because `devServer` in that DSL
// has no historyApiFallback field and `output.publicPath` is not exposed at all.

// Karma reads this file too, and it serves the bundle from the root. Pointing
// publicPath at /blog/ there makes every .wasm 404 and the suite times out
// waiting for a runner that never started. A dev run has a `devServer` block and
// the distribution is built in production mode; a test run is neither.
if (config.devServer || config.mode === 'production') {
  config.output = config.output || {}
  config.output.publicPath = '/blog/'
}

if (config.devServer) {
  // Every path answers with the app's own index.html, the way the Firebase
  // rewrite will. Without it a reload on any article is a 404 — which is exactly
  // how a reader arrives from a search result.
  config.devServer.historyApiFallback = { index: '/blog/index.html' }
  config.devServer.devMiddleware = { publicPath: '/blog/' }

  // index.html is not an emitted asset — it is a Kotlin resource the dev server
  // copies and serves statically — so moving the compiled bundle to /blog/ leaves
  // the page behind at the root. Both halves have to move together, or the
  // document 404s while its script resolves fine.
  if (Array.isArray(config.devServer.static)) {
    config.devServer.static = config.devServer.static.map((entry) =>
      typeof entry === 'string'
        ? { directory: entry, publicPath: '/blog/' }
        : Object.assign({}, entry, { publicPath: '/blog/' })
    )
  }
}
