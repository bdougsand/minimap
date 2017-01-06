(defproject minimap "0.1.0-SNAPSHOT"
  :description "Generate minimaps for "
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/core.async "0.2.395"]
                 [com.stuartsierra/component "0.3.2"]
                 [compojure "1.6.0-beta2"]
                 [org.clojure/data.json "0.2.6"]
                 [org.immutant/web "2.1.5"]
                 [ring/ring-mock "0.3.0"]]
  :main minimap.system
  :uberjar {:aot :all})
