(ns minimap.system
  (:require [com.stuartsierra.component :as component]

            [minimap.web :refer [make-server]])
  (:gen-class))


(defn make-system
  []
  (component/system-map
   :webserver (make-server)))

(def system (make-system))

(defn start []
  (alter-var-root #'system component/start-system))


(defn stop []
  (alter-var-root #'system component/stop-system))

(defn -main [] (start))
