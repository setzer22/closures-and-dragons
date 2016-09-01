(ns ui-test.tokens
  (:import [java.net URL]
           [javax.imageio ImageIO])
  (:require [ui-test.utils :refer :all]))

(defn make-token-id-generator [] (id-generator "token"))

(def images 
  {:knight (ImageIO/read (URL. "file:///home/josep/code/clojure/closures-and-dragons/res/knight.png"))
   :goblin (ImageIO/read (URL. "file:///home/josep/code/clojure/closures-and-dragons/res/goblin.png"))})

(defrecord Token [id x y img])
(defn mk-token [id-gen x y img]
  (map->Token {:x x :y y :id (id-gen) :img (images img)}))
