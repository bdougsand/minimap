(ns minimap.web-handlers
  (:require [compojure.core :refer [defroutes GET]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.io :refer [piped-input-stream]]

            [minimap.api :as api :refer [defapi]]
            [minimap.render :as r :refer [make-image make-bounded-image]]
            [minimap.tile-providers :as tiles]
            [clojure.string :as str])
  (:import [javax.imageio ImageIO]))


(defn- image-response [buf-img]
  {:status 200
   :headers {"content-type" "image/png"
             "cache-control" "max-age=604800"}
   :body (piped-input-stream
          (fn [out]
            (ImageIO/write buf-img "png" out)))})


(defn make-overlays [{:keys [circle lat lng]}]
  (when circle
    [(r/->Circle (some-> lat (Float/parseFloat))
                 (some-> lat (Float/parseFloat))
                 (Integer/parseInt circle))]))


                                        ; Handlers:
(defapi map-handler ::api/map-request
  [{{:keys [tile-provider lat lng zoom] :or {zoom "18"} :as params} :params}]
  (if-let [prov (tiles/get-provider tile-provider)]
    (image-response
     (make-image prov (Float/parseFloat lat) (Float/parseFloat lng)
                 (Integer/parseInt zoom)
                 (make-overlays params)))

    {:status 400
     :body (str "Valid options for tile-provider are: " (str/join ", " (tiles/get-providers)))}))


(defapi bounds-handler ::api/bounds-request
  [{{:keys [clip tile-provider sw-lat sw-lng ne-lat ne-lng] :as params} :params}]
  (if-let [prov (tiles/get-provider tile-provider)]
    (image-response
     (make-bounded-image prov
                         (Float/parseFloat sw-lat) (Float/parseFloat sw-lng)
                         (Float/parseFloat ne-lat) (Float/parseFloat ne-lng)
                         clip
                         (make-overlays params)))
    {:status 400
     :body (str "Valid options for tile-provider are: " (str/join ", " (tiles/get-providers)))}))


(defroutes handlers
  (GET "/" req map-handler)
  (GET "/bounds" req bounds-handler))


(def app
  (-> #'handlers
      (wrap-keyword-params)
      (wrap-params)))
