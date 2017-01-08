(ns minimap.render
  (:require [clojure.java.io :as io]
            [clojure.string :as str]

            [minimap.projection :as proj]
            [minimap.tile-providers :as tiles])

  (:import [java.awt.image BufferedImage]
           [java.awt Color]))


(defn calculate-tiles [prov lat lng zoom tile-scale tile-size]
  (let [[cx cy] (proj/lat-lng-to-point prov lat lng zoom)
        d (/ tile-scale 2)
        ctx (/ cx tile-size)
        cty (/ cy tile-size)
        min-tile-x (- ctx d)
        max-tile-x (+ ctx d)
        min-tile-y (- cty d)
        max-tile-y (+ cty d)]
    (for [y (range min-tile-y (inc max-tile-y))
          :let [y-offset (* (max (- (Math/floor y) min-tile-y) 0) tile-size)]]
      (pmap (fn [x]
              [(tiles/get-tile prov (int x) (int y) zoom)
               x y zoom
               ;; pixel offset:
               (int (* (max (- (Math/floor x) min-tile-x) 0) tile-size))
               (int y-offset)])
            (range min-tile-x (inc max-tile-x))))))


(defn do-draw-tiles [gfx tiles]
  (doseq [[i row] (map-indexed vector tiles)
          [j [tile x y z px py]] (map-indexed vector row)
          :let [clip-bottom? (zero? i)
                clip-top? (= i (dec (count tiles)))
                clip-right? (zero? j)
                clip-left? (= j (dec (count row)))
                img-x (int (* (mod x 1) (.getWidth tile)))
                img-y (int (* (mod y 1) (.getHeight tile)))
                sub-img (if (or clip-bottom? clip-top?
                                clip-left? clip-right?)
                          (.getSubimage tile
                                        (if clip-right? img-x 0)
                                        (if clip-bottom? img-y 0)
                                        (if clip-left?
                                          img-x
                                          (- (.getWidth tile) img-x))
                                        (if clip-top?
                                          img-y
                                          (- (.getHeight tile) img-y)))
                          tile)]]
    (when sub-img
      (.drawImage gfx sub-img nil px py))))


(defn make-image [provider lat lng zoom]
  (let [img (BufferedImage. 256 256 BufferedImage/TYPE_INT_ARGB)]
    (doto (.createGraphics img)
      (do-draw-tiles (calculate-tiles provider lat lng zoom 1 256))
      (.dispose))
    img))


(defn make-bounded-image [provider sw-lat sw-lng ne-lat ne-lng & [clip?]]
  (let [tile-size 256
        zoom (tiles/find-zoom-for-bounds provider [sw-lat sw-lng] [ne-lat ne-lng] tile-size)
        c-lat (+ sw-lat (/ (- ne-lat sw-lat) 2))
        c-lng (+ sw-lng (/ (- ne-lng sw-lng) 2))
        img (BufferedImage. 256 256 BufferedImage/TYPE_INT_ARGB)
        tiles (calculate-tiles provider c-lat c-lng zoom 1 tile-size)
        gfx (.createGraphics img)]
    (when clip?
      ;; Calculate the pixel coordinates of the bounds within the image
      (let [[_ img-left img-top] (ffirst tiles) ; tile coordinates
            [img-rclip img-bclip] (proj/lat-lng-to-point
                                   provider sw-lat ne-lng zoom)
            [img-lclip img-tclip] (proj/lat-lng-to-point
                                   provider ne-lat sw-lng zoom)]
        (doto gfx
          (.setBackground (Color. 0 0 0 1))
          (.clipRect (- img-lclip (* tile-size img-left))
                     (- img-tclip (* tile-size img-top))
                     (- img-rclip img-lclip) (- img-bclip img-tclip)))))
    (doto gfx
      (do-draw-tiles tiles)
      (.dispose))
    img))






