(ns minimap.tile-providers
  (:require [clojure.java.io :as io]
            [clojure.string :as str]

            [minimap.projection :as proj])
  (:import [javax.imageio ImageIO]
           [java.awt.image BufferedImage]
           [java.awt Color]))


(defprotocol TileProvider
  (get-tile [this x y z])
  (tile-size [this]))

(defrecord DummyTileProvider [color]
  TileProvider
  (get-tile [this _ _ _]
    (let [img (BufferedImage. 256 256 BufferedImage/TYPE_INT_ARGB)]
      (doto (.createGraphics img)
        (.setColor color)
        (.fillRect 0 0 256 256)
        (.dispose))
      img))

  (tile-size [this] 256))

(defn dummy-provider []
  (->DummyTileProvider Color/red))


;; Assign the subdomain to allow caching of URL responses
(defn subdomain [subdomains x y]
  (nth subdomains (mod (+ x y) (count subdomains))))

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

  (tile-size [this] (:tile-size this [512 512]))

  proj/Projection
  (project [this lat lng]
    (proj/project (:projection this proj/web-mercator) lat lng)))


(def ^:private cartodb-light-url
  "https://cartodb-basemaps-{s}.global.ssl.fastly.net/light_all/{z}/{x}/{y}@2x.png")
(def ^:private cartodb-dark-url
  "https://cartodb-basemaps-{s}.global.ssl.fastly.net/dark_all/{z}/{x}/{y}@2x.png")
(def ^:private open-street-map-url
  "http://{s}.tile.osm.org/{z}/{x}/{y}.png")

(def cartodb-light
  (->URLTileProvider cartodb-light-url))

(def cartodb-dark
  (->URLTileProvider cartodb-dark-url))

(defonce providers (atom {}))
(defn add-provider! [k provider]
  (swap! providers assoc k provider))
(defn get-provider [k] (get @providers k))
(defn get-providers [] (keys @providers))

(add-provider! "cartodb-light" cartodb-light)
(add-provider! "cartodb-dark" cartodb-dark)
(add-provider! "dummy" dummy-provider)

(defn find-zoom-for-bounds
  [prj [sw-lat sw-lng] [ne-lat ne-lng] size]
  ;; Find the pixel dimensions at max zoom
  (let [start-zoom 18
        [sw-x sw-y] (proj/lat-lng-to-point prj sw-lat sw-lng start-zoom)
        [ne-x ne-y] (proj/lat-lng-to-point prj ne-lat ne-lng start-zoom)
        ratio (min (/ size (Math/abs (- ne-x sw-x)))
                   (/ size (Math/abs (- ne-y sw-y))))]
    (if (< ratio 1)
      (let [dzoom (/ (Math/log ratio) (Math/log 2))
            new-zoom (min 18 (max 1 (+ dzoom start-zoom)))]
        (int (if (< (- (Math/ceil new-zoom) new-zoom) 0.1)
               (Math/ceil new-zoom)
               new-zoom)))
      start-zoom)))
