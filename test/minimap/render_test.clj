(ns minimap.render-test
  (:require [minimap.render :as sut]
            [minimap.tile-providers :as tiles]
            [clojure.test :as t]
            [clojure.java.io :as io])
  (:import [javax.imageio ImageIO]))

(t/deftest test-providers
  (let [filename (str "resources/test" (rand-int 1000) ".png")
        provider (-> (keys @tiles/providers) (rand-nth) (tiles/get-provider))]
    (-> (sut/make-image provider 42.37014943565453 -71.14634513854982 (+ (rand-int 5) 13))
        (ImageIO/write "png" (io/file filename)))
    (t/is (.exists (io/file filename)))
    (let [img (ImageIO/read (io/file filename))]
      (t/is (= 512 (.getWidth img)))
      (t/is (= 512 (.getHeight img)))
      (t/is (any? (for [y (range 512) x (range 512)]
                    (not= (.getRGB img x y) 0)))))
    (.delete (io/file filename))))
