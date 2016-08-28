(ns ui-test.grid-protocol
  (:require [clojure.pprint :as pprint :refer [pprint]]))

(defn grid-type [world & _] (:grid-type world))

(defmulti pixel->tile grid-type)
(defmulti tile->pixel grid-type)
(defmulti snap-to-grid grid-type)
(defmulti token-radius grid-type)
(defmulti draw-grid! grid-type)



