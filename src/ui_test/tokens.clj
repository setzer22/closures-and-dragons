(ns ui-test.tokens
  (:import [java.net URL]
           [javax.imageio ImageIO]))

(defn id-generator [name]
  (let [counter (atom 0)]
    (fn []
      (swap! counter inc)
      (str name "-" @counter))))

(def gen-token-id (id-generator "token"))

(def images 
  {:knight (ImageIO/read (URL. "file:///home/josep/code/clojure/closures-and-dragons/res/knight.png"))
   :goblin (ImageIO/read (URL. "file:///home/josep/code/clojure/closures-and-dragons/res/goblin.png"))})

(defrecord Token [id x y img])
(defn mk-token [x y img]
  (map->Token {:x x :y y :id (gen-token-id) :img (images img)}))
