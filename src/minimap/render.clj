(ns minimap.render
  (:require [clojure.java.io :as io]
            [clojure.string :as str]

            [minimap.projection :as proj]
            [minimap.tile-providers :as tiles])

  (:import [java.awt.image BufferedImage]
           [java.awt Color RenderingHints]))


(defn make-transform [prov clat clng zoom tile-scale tile-size output-size]
  (let [[cx cy] (proj/lat-lng-to-point prov clat clng zoom)
        d (/ tile-scale 2)
        ctx (/ cx tile-size)
        cty (/ cy tile-size)
        min-tile-x (- ctx d)
        min-tile-y (- cty d)]
    (fn
      ([lat lng]
       (let [[x y] (proj/lat-lng-to-point prov lat lng zoom)]
         [(* (- (/ x tile-size) min-tile-x) output-size )
          (* (- (/ y tile-size) min-tile-y) output-size)]))
      ([]
       (let [c (/ output-size 2)]
         [c c])))))


(defn calculate-tiles [prov lat lng zoom tile-scale tile-size & [output-size]]
  (let [output-size (or output-size tile-size)
        [cx cy] (proj/lat-lng-to-point prov lat lng zoom)
        d (/ tile-scale 2)
        ctx (/ cx tile-size)
        cty (/ cy tile-size)
        min-tile-x (- ctx d)
        max-tile-x (+ ctx d)
        min-tile-y (- cty d)
        max-tile-y (+ cty d)]
    (for [y (range min-tile-y (inc max-tile-y))
          :let [y-offset (* (max (- (Math/floor y) min-tile-y) 0) output-size)]]
      ;; Array of [BufferedImage x y zoom ]
      (pmap (fn [x]
              [(tiles/get-tile prov (int x) (int y) zoom)
               x y zoom
               ;; pixel offset:
               (int (* (max (- (Math/floor x) min-tile-x) 0) output-size))
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

(defprotocol Drawable
  (draw [x gfx transform scale-m]))
(defprotocol Styleable
  (apply-style [x gfx]))

(defrecord Circle [lat lng r]
  Drawable
  (draw [this gfx transform scale-m]
    (let [[x y] (if (and lat lng) (transform lat lng) (transform))
          scale (if (:pixel-scale this) identity (partial * (/ 1 scale-m)))
          xr (scale (:xr this r))
          yr (scale (:yr this r))]
      (.drawOval gfx (- x xr) (- y yr) (* 2 xr) (* 2 yr)))))

(defn as-color [x]
  (cond (instance? Color x) x

        (string? x) (try
                      (if (str/starts-with? x "#")
                        (Color/decode x)

                        (.get (.getField Color x) Color))
                      (catch NoSuchFieldException _ nil)
                      (catch NumberFormatException _ nil))))

(defn style [gfx x]
  (if (satisfies? Styleable x)
    (apply-style x gfx)

    (.setColor gfx (or (some-> (:color x) (as-color))
                       (Color. 60 124 61)))))

(defn do-draw-overlays [gfx transform scale-m overlays]
  (doto gfx
    (.setStroke (java.awt.BasicStroke. 2)))
  (doseq [overlay overlays]
    (style gfx overlay)
    (draw overlay gfx transform scale-m)))

(defn draw-overlays [gfx prj lat lng zoom overlays]
  (when overlays
    (doto gfx (.setRenderingHint java.awt.RenderingHints/KEY_ANTIALIASING
                                 java.awt.RenderingHints/VALUE_ANTIALIAS_ON)
          (do-draw-overlays (make-transform prj lat lng zoom 1 256 512)
                            (proj/pixel-scale prj lat lng zoom)
                            overlays))))

(defn make-image [provider lat lng zoom & [overlays]]
  (let [img (BufferedImage. 512 512 BufferedImage/TYPE_INT_ARGB)]
    (doto (.createGraphics img)
      (do-draw-tiles (calculate-tiles provider lat lng zoom 1 256 512))
      (draw-overlays provider lat lng zoom overlays)
      (.dispose))
    img))


(defn make-bounded-image [provider sw-lat sw-lng ne-lat ne-lng clip? & [overlays]]
  (let [zoom (tiles/find-zoom-for-bounds provider [sw-lat sw-lng] [ne-lat ne-lng] 256)
        c-lat (+ sw-lat (/ (- ne-lat sw-lat) 2))
        c-lng (+ sw-lng (/ (- ne-lng sw-lng) 2))
        img (BufferedImage. 512 512 BufferedImage/TYPE_INT_ARGB)
        tiles (calculate-tiles provider c-lat c-lng zoom 1 256 512)
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
          (.clipRect (- img-lclip (* 256 img-left))
                     (- img-tclip (* 256 img-top))
                     (* 2 (- img-rclip img-lclip))
                     (* 2 (- img-bclip img-tclip))))))
    (doto gfx
      (do-draw-tiles tiles)
      (draw-overlays provider c-lat c-lng zoom overlays)
      (.dispose))
    img))





(comment
  ; bounds?tile-provider=&sw-lat=&sw-lng=&ne-lat=&ne-lng=&clip=1
  (javax.imageio.ImageIO/write
   (make-bounded-image (tiles/get-provider "cartodb-light")
                       42.37014943565453 -71.14634513854982 42.42079540736353 -71.06102943420412 true)
   "png"
   (io/file "resources/test.png"))

  )
