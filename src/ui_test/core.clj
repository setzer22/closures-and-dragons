(ns ui-test.core
  (:import [java.net URL]
           [javax.swing JFrame JPanel JLabel SwingUtilities]
           [javax.imageio ImageIO]
           [java.awt BorderLayout Graphics Color BasicStroke Polygon Point Image AlphaComposite RenderingHints]
           [java.awt.event MouseAdapter MouseEvent])
  (:require [ui-test.grid-protocol :as grid]
            [ui-test.hex-drawing :as hex]
            [ui-test.square-grid :as square]
            [ui-test.swing-utils :as swu :refer :all]
            [ui-test.transaction :as transaction :refer [transaction]])
  (:gen-class))

(require 'spyscope.core)

(defmacro with-graphics [g & body]
  `(let [~g (.create ~g)]
     ~@body
     (.dispose ~g)))

(defn id-generator [name]
  (let [counter (atom 0)]
    (fn []
      (swap! counter inc)
      (str name "-" @counter))))

(def gen-token-id (id-generator "token"))

(def images 
  {:knight (ImageIO/read (URL. "file:///home/josep/Code/clojure/ui-test/res/knight.png"))
   :goblin (ImageIO/read (URL. "file:///home/josep/Code/clojure/ui-test/res/goblin.png"))})

(defrecord Token [id x y img])
(defn mk-token [x y img]
  (map->Token {:x x :y y :id (gen-token-id) :img (images img)}))

(defn add-token [{:keys [tile-size dimensions tokens] :as world} tx ty img]
  (let [[x y] (grid/tile->pixel world [tx ty])
        new-token (mk-token x y img)]
    (assoc world :tokens (assoc (:tokens world) [tx ty] new-token))))

(defn draw-circle-centered [g x y d]
  (.drawOval g (- x (/ d 2)) (- y (/ d 2)) d d))

(defn custom-panel [world]
  (proxy [JPanel] [] 
    (paintComponent [^Graphics g] 
      (proxy-super paintComponent g)
      (let [{:keys [dimensions tile-size tokens]} @world
            R (grid/token-radius @world)] 
        (with-graphics g 
          (.setColor g (Color/black))
          (.setStroke g (BasicStroke. 2))
          (.setRenderingHint g RenderingHints/KEY_ANTIALIASING RenderingHints/VALUE_ANTIALIAS_ON)
          (grid/draw-grid! @world g [-10 10] [-10 10])
          (doseq [{:keys [x y img] :as token} (vals tokens)]
            (.drawImage g img (- x R) (- y R) (* 2 R) (* 2 R) nil)
            (comment (draw-circle-centered g x y (* 2 R)))))))))

(defn dist [{x1 :x y1 :y} {x2 :x y2 :y}]
  (let [d1 (- x1 x2)
        d2 (- y1 y2)]
    (Math/sqrt (+ (* d1 d1) (* d2 d2)))))

(defn mouse-position-string [world x y]
  (let [[q r] (grid/pixel->tile @world [x y])] 
    (str "x: " x ", y: " y " | q: " q " r:" r)))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn remove-one-shot! [event-listeners event-name]
  )

(defn mouse-listener [world top-panel event-listeners] 
  (let [remove-one-shot! 
        (fn [event-name]
          (swap! event-listeners assoc event-name 
                 (remove #(= (:event-type %) :one-shot) 
                         (get @event-listeners event-name))))] 
    (proxy [MouseAdapter] []
      ;TODO: Could be macro'd
      (mousePressed [event]
        (doseq [{:keys [callback]} (:mousePressed @event-listeners)]
          (callback event world top-panel))
        (remove-one-shot! :mousePressed))
      (mouseReleased [event]
        (doseq [{:keys [callback]} (:mouseReleased @event-listeners)]
          (callback event world top-panel))
        (remove-one-shot! :mouseReleased))
      (mouseDragged [event]
        (doseq [{:keys [callback]} (:mouseDragged @event-listeners)]
          (callback event world top-panel))
        (remove-one-shot! :mouseDragged))
      (mouseMoved [event]
        (doseq [{:keys [callback]} (:mouseMoved @event-listeners)]
          (callback event world top-panel))
        (remove-one-shot! :mouseMoved)))))

(defn permanent-event [event-callback]
  {:event-type :permanent
   :callback event-callback})

(defn one-shot-event [event-callback]
  {:event-type :one-shot
   :callback event-callback})

(def main-events
  {:mousePressed 
   [(permanent-event 
      (fn [event world top-panel]
        (let [panel (find-component top-panel "grid-panel")
              [x y] [(.getX event) (.getY event)]
              tile-coords (grid/pixel->tile @world [x y])
              {:keys [tile-size]} @world
              token (get (:tokens @world) tile-coords)]
          (when token 
            (swap! world assoc-in [:tokens tile-coords] (assoc token :x x :y y))
            (swap! world assoc :dragged-token-tile tile-coords))
          (.repaint panel))))]

   :mouseReleased
   [(permanent-event 
      (fn [event world top-panel]
        (let [panel (find-component top-panel "grid-panel")
              [x y] [(.getX event) (.getY event)] 
              dragged-token-old-tile (:dragged-token-tile @world)
              dragged-token-new-tile (grid/pixel->tile @world [x y])
              dragged-token (get (:tokens @world) dragged-token-old-tile)
              [sx sy] (grid/snap-to-grid @world [x y])]
          (when dragged-token 
            (swap! world dissoc-in [:tokens dragged-token-old-tile])
            (swap! world assoc-in [:tokens dragged-token-new-tile] (assoc dragged-token :x sx :y sy))
            (swap! world dissoc :dragged-token-tile)
            (.repaint panel)))))]

   :mouseDragged 
   [(permanent-event 
      (fn [event world top-panel]
        (let [label (find-component top-panel "info-label")
              panel (find-component top-panel "grid-panel")
              [x y] [(.getX event) (.getY event)]
              dragged-token-tile (:dragged-token-tile @world)
              dragged-token (get (:tokens @world) dragged-token-tile)]
          (when dragged-token 
            (swap! world assoc-in [:tokens dragged-token-tile] (assoc dragged-token :x x :y y))
            (.repaint panel))
          (.setText label (mouse-position-string world x y)))))]
   :mouseMoved 
   [(permanent-event 
      (fn [event world top-panel]
        (let [label (find-component top-panel "info-label")] 
          (.setText label (mouse-position-string world (.getX event) (.getY event))))))]})

(defn show-gui [] 
  (SwingUtilities/invokeLater 
    (fn [] 
      (def world 
        (atom (-> {:grid-type :pointy-hex
                   :size 30
                   :dimensions [10 10]
                   :transaction nil
                   :tokens {}}
                  (add-token 3 2 :goblin)
                  (add-token 4 5 :goblin)
                  (add-token 1 1 :knight))))
      (def event-listeners 
        (atom main-events))
      (let [label (doto (JLabel. "Hello World!")
                    (.setName "info-label"))
            frame (JFrame. "Hello Swing!")
            top-panel (JPanel.)
            panel (doto (custom-panel world)
                    (.setName "grid-panel"))
            listener (mouse-listener world top-panel event-listeners)]
        (def -top-panel top-panel); TODO
        (.addMouseListener panel listener)
        (.addMouseMotionListener panel listener)
        (.setLayout top-panel (BorderLayout.))
        (.add top-panel panel BorderLayout/CENTER)
        (.add top-panel label BorderLayout/SOUTH)
        (.add frame top-panel)
        (.setVisible top-panel true)
        (.setVisible panel true)
        (.setVisible frame true)))))

(show-gui)

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
