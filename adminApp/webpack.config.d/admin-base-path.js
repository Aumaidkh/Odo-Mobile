// Mounts the app under /admin — in production, and locally too.
//
// The same fix `:webApp` applies for /blog, and it is here for the same reasons.
// See webApp/webpack.config.d/blog-base-path.js for the long version; the short
// one is that the bundle is loaded by an absolute `<script src>`, so the dev
// server has to serve from the same prefix or local and deployed stop being the
// same URL shape and the router's base-path handling is only ever exercised in
// production.
//
// This lives here rather than in the Kotlin DSL because `devServer` in that DSL
// has no historyApiFallback field and `output.publicPath` is not exposed at all.

// Karma reads this file too, and it serves the bundle from the root. Pointing
// publicPath at /admin/ there makes every .wasm 404 and the suite times out
// waiting for a runner that never started. A dev run has a `devServer` block and
// the distribution is built in production mode; a test run is neither.
if (config.devServer || config.mode === 'production') {
  config.output = config.output || {}
  config.output.publicPath = '/admin/'
}

if (config.devServer) {
  // Every path answers with the app's own index.html, the way the Firebase
  // rewrite will. Without it a reload on /admin/users is a 404.
  config.devServer.historyApiFallback = { index: '/admin/index.html' }
  config.devServer.devMiddleware = { publicPath: '/admin/' }

  // index.html is not an emitted asset — it is a Kotlin resource the dev server
  // copies and serves statically — so moving the compiled bundle to /admin/
  // leaves the page behind at the root. Both halves have to move together, or
  // the document 404s while its script resolves fine.
  if (Array.isArray(config.devServer.static)) {
    config.devServer.static = config.devServer.static.map((entry) =>
      typeof entry === 'string'
        ? { directory: entry, publicPath: '/admin/' }
        : Object.assign({}, entry, { publicPath: '/admin/' })
    )
  }
}
