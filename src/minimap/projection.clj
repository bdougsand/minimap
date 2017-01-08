(ns minimap.projection)

(defn ->degrees [rads]
  (/ rads (/ Math/PI 180)))

(defn ->radians [deg]
  (* deg (/ Math/PI 180)))

(defprotocol Projection
  (project [this lat lng]))


;; Web Mercator
(def web-radius 6378137)
(def web-max-lat 85.0511287798)
(def web-min-lat (- web-max-lat))
(def web-mercator
  (reify Projection
    (project [this lat lng]
      (let [lat (max (min lat web-max-lat) web-min-lat)
            ls (Math/sin (->radians lat))]
        [(* web-radius (->radians lng))
         (* web-radius (Math/log (/ (+ 1 ls) (- 1 ls))) 0.5)]))))

(defn scale-crs [n]
  (* 256 (Math/pow 2 n)))

(defn make-transform [a b c d]
  (fn transform
    ([x y scale]
     [(* scale (+ (* a x) b))
      (* scale (+ (* c y) d))])
    ([x y] (transform x y 1))))


;; Everything from here down needs a lot of work.
(def epsg-3857-transform
  (let [scale (/ 0.5 (* Math/PI web-radius))]
    (make-transform scale 0.5 (- scale) 0.5)))


(defn lat-lng-to-point
  ([prj lat lng zoom]
   (let [[x y] (project prj lat lng)]
     (epsg-3857-transform x y (scale-crs zoom))))
  ([lat lng zoom]
   (lat-lng-to-point web-mercator lat lng zoom)))

