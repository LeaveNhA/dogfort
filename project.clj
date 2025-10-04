(defproject dogfort "0.2.3"
  :description "A web server framework for Clojurescript on Node"
  :url "https://github.com/bodil/dogfort"
  :license {:name "Apache License, version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/clojurescript "1.11.132"]
                 [org.clojure/tools.nrepl "0.2.10"]]
  :plugins [[org.bodil/lein-noderepl "0.1.11"]
            [lein-npm "0.6.1"]
            [com.cemerick/clojurescript.test "0.3.3"]]
  :npm {:dependencies [
                       [nrepl-client "0.2.3"]
                       [ws "0.8.0"]
                       [busboy "0.2.12"]
                       ]}
  :shadow-cljs {:nrepl {:port 7002}}
  :profiles {:dev {:dependencies [[cider/cider-nrepl "0.25.0"]
                                  [thheller/shadow-cljs "2.26.2"]]}}
  :aliases
  {"build" ["with-profile" "dev" "run" "-m" "shadow.cljs.devtools.cli" "release" "server"]}
  )
