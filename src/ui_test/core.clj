(set! *warn-on-reflection* true)

(ns ui-test.core
  (:import [java.net URL]
           [javax.swing JFrame JPanel JLabel SwingUtilities]
           [javax.imageio ImageIO]
           [java.awt BorderLayout Graphics Color BasicStroke Polygon Point Image AlphaComposite RenderingHints]
           [java.awt.event MouseAdapter MouseEvent])
  (:require [clojure.core.async :as a :refer [<! >! <!! >!! go go-loop chan dropping-buffer]]
            [ui-test.grid-protocol :as grid]
            [com.rpl.specter :as s]
            [com.rpl.specter.macros :as sm :refer [transform select select-one]]
            [ui-test.repaint-channel :refer :all]
            [ui-test.utils :as u :refer :all]
            [ui-test.tokens :as t]
            [ui-test.world-commands :as wc :refer :all]
            [ui-test.hex-drawing :as hex]
            [ui-test.square-grid :as square]
            [ui-test.swing-utils :as swu :refer :all]
            [ui-test.transaction :as transaction :refer [transaction]])
  (:gen-class))

(require 'spyscope.core)
;TODO: Differentiate functions taking a world ref and functions taking a world value!

(defmacro with-graphics [g & body]
  `(let [~g (.create ~g)]
     ~@body
     (.dispose ~g)))

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
          (grid/draw-grid! @world g [0 20] [0 20])
          (doseq [{:keys [x y img] :as token} (get-all-tokens @world)]
            (.drawImage g img (- x R) (- y R) (* 2 R) (* 2 R) nil)))))))

(defn mouse-position-string [world x y]
  (let [[q r] (grid/pixel->tile @world [x y])] 
    (str "x: " x ", y: " y " | q: " q " r:" r)))

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
              token (get-one-token-at @world tile-coords)
              loc (token-locator token tile-coords)]
          (when token 
            (swap! world lift-token-at loc [x y]))
          (repaint! world))))]

   :mouseReleased
   [(permanent-event 
      (fn [event world top-panel]
        (let [panel (find-component top-panel "grid-panel")
              [x y] [(.getX event) (.getY event)] 
              lifted-token-locator (:lifted-token-locator @world)
              lifted-token-new-tile (grid/pixel->tile @world [x y])
              new-token-pos (grid/snap-to-grid @world [x y])]
          (when lifted-token-locator 
            (swap! world drop-token-at lifted-token-locator lifted-token-new-tile new-token-pos)
            (repaint! world)))))]

   :mouseDragged 
   [(permanent-event 
      (fn [event world top-panel]
        (let [label (find-component top-panel "info-label")
              panel (find-component top-panel "grid-panel")
              [x y] [(.getX event) (.getY event)]
              lifted-token-locator (:lifted-token-locator @world)]
          (when lifted-token-locator
            (swap! world token-motion lifted-token-locator [x y])
            (repaint! world))
          (.setText label (mouse-position-string world x y)))))]
   :mouseMoved 
   [(permanent-event 
      (fn [event world top-panel]
        (let [label (find-component top-panel "info-label")] 
          (.setText label (mouse-position-string world (.getX event) (.getY event))))))]})

(defn show-gui [] 
  (SwingUtilities/invokeLater 
    (fn [] 
      (let [repaint-channel (make-repaint-channel)
            event-listeners (atom main-events)
            world (atom (-> {:grid-type :pointy-hex
                             :size 30
                             :dimensions [10 10]
                             :transaction nil
                             :tokens {}
                             :repaint-chanel repaint-channel}
                            (add-token 3 2 :goblin)
                            (add-token 4 5 :goblin)
                            (add-token 1 1 :knight)))
            __ (def world world)
            __ (def repaint-channel repaint-channel)
            __ (def event-listeners event-listeners)

            ;; TODO: Swing GUI wrappers...
            label (doto (JLabel. "Hello World!")
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
        (.setVisible frame true)
        (start-repaint-process! repaint-channel top-panel)))))

(show-gui)

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
