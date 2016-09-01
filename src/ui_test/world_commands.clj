(ns ui-test.world-commands
  (:require [ui-test.utils :refer :all]
            [ui-test.tokens :as t :refer :all]
            [com.rpl.specter :as s]
            [ui-test.grid-protocol :as grid]
            [com.rpl.specter.macros :as sm :refer [transform select select-one]]))


(defn add-token [{:keys [tile-size dimensions tokens] :as world} id-gen tx ty img]
  (let [[x y] (grid/tile->pixel world [tx ty])
        new-token (mk-token id-gen x y img)
        tokens-at-pos (get world [tx ty] {})]
    (assoc-in world [:tokens [tx ty]] (assoc tokens-at-pos (:id new-token) new-token))))

(defn get-tokens-at [world pos]
  (let [tokens (get (:tokens world) pos)]
    (if tokens 
      (vals tokens)
      [])))

(defn get-all-tokens [world]
  (select [:tokens s/MAP-VALS s/MAP-VALS] world))

; WIL: Specter is apparently not working with precompiled keypaths containing tuples. It seems to be a bug so I should be dropping specter altogether for now.


;(clojure.pprint/pprint @ui-test.core/world )
;(clojure.pprint/pprint (lift-token-at @ui-test.core/world {:id "token-28" :tx 3 :ty 2}))

(defn token-locator [token [tx ty]]
  {:id (:id token)
   :tx tx
   :ty ty})

(defn get-locator-coordinates [world {:keys [tx ty]}]
  (grid/tile->pixel world [tx ty]))

(defn get-one-token-at [world pos]
  (first (get-tokens-at world pos)))

(defn get-token-at [world {:keys [id tx ty] :as locator}]
  (select-one [s/ALL #(= (:id %) id)] (get-tokens-at world [tx ty])))

(defn assoc-token-at [world {:keys [tx ty id] :as locator} token]
  (assoc-in world [:tokens [tx ty] id] token))

(defn dissoc-token-at [world {:keys [tx ty id :as locator]}]
  (dissoc-in world [:tokens [tx ty] id]))

(defn move-token [world current-locator new-tile]
  (let [token (get-token-at world current-locator)
        new-locator (token-locator token new-tile)
        [x y] (get-locator-coordinates world new-locator)
        moved-token (assoc token :x x :y y)]
    (-> world
        (dissoc-token-at current-locator)
        (assoc-token-at new-locator moved-token))))

(defn get-locator-tile [{:keys [tx ty] :as locator}]
  [tx ty])

(defn token-motion [world {:keys [tx ty id] :as locator} [new-x new-y]]
  (let [token (get-token-at world locator)
        moved-token (assoc token :x new-x :y new-y)]
    (assoc-token-at world locator moved-token)))

(defn get-lifted-token [world]
  (get-token-at world (:lifted-token-locator world)))

(defn lift-token-at [world {:keys [tx ty id] :as locator} mouse-pos]
  (-> world 
      (token-motion locator mouse-pos)
      (assoc :lifted-token-locator locator)))

(defn drop-token-at [world locator new-tile [nx ny]]
  (let [lifted-token (get-token-at world locator)
        new-locator (token-locator lifted-token new-tile)
        moved-token (assoc lifted-token :x nx :y ny)] 
    (-> world 
        (dissoc-token-at locator)
        (assoc-token-at new-locator moved-token))))
