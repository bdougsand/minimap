(ns minimap.web-handlers
  (:require [compojure.core :refer [defroutes GET]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.io :refer [piped-input-stream]]

            [minimap.api :as api :refer [defapi]]
            [minimap.render :refer [make-image]]
            [minimap.tile-providers :as tiles]
            [clojure.string :as str])
  (:import [javax.imageio ImageIO]))


(defapi map-handler ::api/map-request
  [{{:keys [tile-provider lat lng zoom] :or {zoom "18"}} :params}]
  (if-let [prov (tiles/get-provider tile-provider)]
    (let [img (make-image prov (Float/parseFloat lat) (Float/parseFloat lng) (Integer/parseInt zoom))]
      {:status 200
       :headers {"content-type" "image/png"
                 "cache-control" "max-age=604800"}
       :body (piped-input-stream
              (fn [out]
                (ImageIO/write img "png" out)))})

    {:status 400
     :body (str "Valid options for tile-provider are: " (str/join ", " (tiles/get-providers)))}))


(defroutes handlers
  (GET "/" req map-handler))

(def app
  (-> #'handlers
      (wrap-keyword-params)
      (wrap-params)))

