(ns minimap.web
  (:require [immutant.web :as web]
            [com.stuartsierra.component :as component]

            [minimap.web-handlers :refer [app]]))


(defrecord WebServer [port]
  component/Lifecycle
  (start [this]
    (assoc this
           :webserver (web/run app :port port)))

  (stop [this]
    (when-let [server (:webserver this)]
      (web/stop server))
    (dissoc this :webserver)))

(defn make-server []
  (->WebServer 3142))

