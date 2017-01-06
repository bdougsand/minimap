(ns minimap.tile-providers
  (:require [clojure.java.io :as io]
            [clojure.string :as str]

            [minimap.projection :as proj])
  (:import [javax.imageio ImageIO]))


(defprotocol TileProvider
  (get-tile [this x y z])
  (tile-size [this])
  (get-projection [this]))


(defn subdomain [subdomains x y]
  (nth subdomains (mod (Math/abs (+ x y)) (count subdomains))))

(defn tile-url [pattern x y z]
  (str/replace pattern
               #"\{[szxy]\}"
               #(str (case % "{x}" x "{y}" y "{z}" z "{s}" (subdomain "abc" x y)))))

(defrecord URLTileProvider [url-pattern]
  TileProvider
  (get-tile [this x y z]
    (-> url-pattern
        (tile-url x y z)
        (io/input-stream)
        (ImageIO/read)))

  (tile-size [this] (:tile-size this [256 256]))

  (get-projection [this]
    (:projection this proj/web-mercator))

  proj/Projection
  (project [this lat lng]
    (proj/project (get-projection this) lat lng)))

(def cartodb-light
  (->URLTileProvider "https://cartodb-basemaps-{s}.global.ssl.fastly.net/light_all/{z}/{x}/{y}.png"))

(defmulti get-provider identity)
(defmethod get-provider "cartodb-light" [_] cartodb-light)
