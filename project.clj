(defproject fanfic-scrape "0.1.0"
  :description "AO3 fanfiction metadata scraper"
  :url "https://github.com/kvn-prhn/fanfic-scrape"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [yaml "1.0.0"]
                 [org.clojars.clizzin/jsoup "1.5.1"]
                 [org.mongodb/mongodb-driver "3.9.1"]]
  :main ^:skip-aot fanfic-scrape.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
