(ns ui-test.square-grid
  (:require [ui-test.grid-protocol :as grid-protocol]))

;(defmulti pixel->tile grid-type)
;(defmulti tile->pixel grid-type)
;(defmulti snap-to-grid grid-type)
;(defmulti token-radius grid-type)
;(defmulti draw-grid! grid-type)

(defmethod grid-protocol/pixel->tile :square
  [{:keys [size]} [x y]]
  (let [R (/ size 2)] 
    [(int (/ x size)) (int (/ y size))]))

(defn half [x] (/ x 2))

(defmethod grid-protocol/tile->pixel :square
  [{:keys [size]} [x y]]
  [(+ (* x size) (half size)) (+ (* y size) (half size))])

(defmethod grid-protocol/snap-to-grid :square
  [world pixel]
  (grid-protocol/tile->pixel world (grid-protocol/pixel->tile world pixel)))

(defmethod grid-protocol/token-radius :square
  [{:keys [size]}]
  (/ size 2))

(defmethod grid-protocol/draw-grid! :square
  [{:keys [size]} g [x-min x-max] [y-min y-max]]
  (let [y-min-pos (* size y-min)
        y-max-pos (* size y-max)
        x-min-pos (* size x-min)
        x-max-pos (* size x-max)] 
    (doseq [tx (range x-min x-max) :let [x (* size tx)]]
      (.drawLine g x y-min-pos x y-max-pos))
    (doseq [ty (range y-min y-max) :let [y (* size ty)]]
      (.drawLine g x-min-pos y x-max-pos y))))

