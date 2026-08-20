// The dev server has to answer every path with index.html, the way Firebase
// Hosting will once `/blog` is wired up. Without it the app works while you click
// but 404s the moment you reload on an article — which is exactly the case worth
// testing, because that is how a reader arrives from a search result.
//
// Written as a raw webpack fragment because the Kotlin DSL's `devServer` block has
// no historyApiFallback field. `webpack.config.d/*.js` is the supported way in;
// the file is merged into the generated config. Production bundling reads the same
// file and ignores `devServer`, so there is nothing to guard.
config.devServer = config.devServer || {};
config.devServer.historyApiFallback = true;
