[![Clojars Project](https://img.shields.io/clojars/v/aerial.hanami.svg)](https://clojars.org/aerial.hanami)

# Hanami

Interactive arts and charts visualizations with Clojure(Script), Vega-lite, and Vega. Flower viewing 花見 (hanami)

<a href="https://jsa-aerial.github.io/aerial.hanami/index.html"><img src="https://github.com/jsa-aerial/hanami/blob/master/resources/public/Himeji_sakura.jpg" align="left" hspace="10" vspace="6" alt="hanami logo" width="150px"></a>

**Hanami** is a Clojure(Script) library and framework for creating interactive visualization applications based in [Vega-Lite](https://vega.github.io/vega-lite/) (VGL) and/or [Vega](https://vega.github.io/vega/) (VG) specifications. These specifications are declarative and completely specified by _data_ (JSON maps). VGL compiles into the lower level grammar of VG which in turn compiles to a runtime format utilizting lower level runtime environments such as [D3](https://d3js.org/), HTML5 Canvas, and [WebGL](https://github.com/vega/vega-webgl-renderer). In addition to VGL and VG, Hanami is built on top of [Reagent](http://reagent-project.github.io/) and [Re-Com](https://github.com/Day8/re-com).

