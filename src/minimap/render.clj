(ns minimap.render
  (:require [clojure.java.io :as io]
            [clojure.string :as str]

            [minimap.projection :as proj]
            [minimap.tile-providers :as tiles])

  (:import [java.awt.image BufferedImage]))


(defn calculate-tiles [prj lat lng zoom tile-scale tile-size]
  (let [[cx cy] (proj/lat-lng-to-point prj lat lng zoom)
        d (/ tile-scale 2)
        ctx (/ cx tile-size)
        cty (/ cy tile-size)
        min-tile-x (- ctx d)
        max-tile-x (+ ctx d)
        min-tile-y (- cty d)
        max-tile-y (+ cty d)]
    (for [y (range min-tile-y (inc max-tile-y))
          :let [y-offset (* (max (- (Math/floor y) min-tile-y) 0) tile-size)]]
      (for [x (range min-tile-x (inc max-tile-x))]
        [x y zoom
         ;; pixel offset:
         (int (* (max (- (Math/floor x) min-tile-x) 0) tile-size))
         (int y-offset)]))))

(defn make-image [provider lat lng zoom]
  (let [img (BufferedImage. 256 256 BufferedImage/TYPE_INT_ARGB)
        gfx (.createGraphics img)
        rows (map-indexed vector (calculate-tiles provider lat lng zoom 1 256))]
    (doseq [[i row] rows
            :let [clip-bottom? (zero? i)
                  clip-top? (= i (dec (count rows)))]]
      (doseq [[j [x y z px py]] (map-indexed vector row)
              :let [clip-right? (zero? j)
                    clip-left? (= j (dec (count row)))
                    tile (tiles/get-tile provider (int x) (int y) z)
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
    (.dispose gfx)
    img))
