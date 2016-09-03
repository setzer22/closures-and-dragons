(set! *warn-on-reflection* true)
(require 'spyscope.core)

(ns ui-test.core
  (:import [java.net URL]
           [javax.swing JFrame JPanel JLabel SwingUtilities UIManager]
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
            [ui-test.swing-wrappers :refer :all]
            [ui-test.world-commands :as wc :refer :all]
            [ui-test.world-actions :as wa :refer :all]
            [ui-test.server-communication :as sc :refer :all]
            [ui-test.hex-drawing :as hex]
            [ui-test.square-grid :as square]
            [ui-test.swing-utils :as swu :refer :all]
            [ui-test.transaction :as transaction :refer [transaction]])
  (:gen-class))

;TODO: Differentiate functions taking a world ref and functions taking a world value!

;TODO: Things that need to be done
; USER IDENTIFICATION AND THE GAME MASTER: 
;   - Find a way to make a client the "game master" (the one who creates the game)
;   - For now, the first connected id will be recognised as the GM. This will change whenever game creation
;     is implemented.
;   - The server should also be more customisable and launchable as a sepparate program if necessary.
;   - Since there are reasons for client sepparation, a user system should be designed. 
;   - When connecting to the server, a client receives his unique identifier which will act as the user id for all
;     identification purposes. That will change whenever persistent data needs to be saved between sessions, but 
;     for now it's OK.

; WORLD SYNCHRONISATION AND CONSISTENCY ENFORCEMENT:
;   - Whenever an additional client besides the master connects to the game, he receives the world
;     as an update. From that point, all actions should be synced with the server.
;   - Each action must incorporate a validator. For example, it "move-token" moves a token with a 
;     given locator from there to another tile, the token at the locator must exist and the new location
;     must be free. If the validator fails, that means the world was out-of-sync and must be resync'd
;   - Whenever a disconnect event is received, the client becomes disconnected and all his actions one-shot-event
;     the board have no effect. Whenever he gets a connected event back again, he receives the GM's state of the
;     world, and all his changes during desconnexion are discarded.

;   - From all the behavior above, the following functions will need to be implemented:
;    
;  send-world :: [] -> stripped-world
;  replicate-world :: stripped-world -> world
;  
;   - Some things need to be stripped from the world and will need to be replicated at the client's site. i.e: 
;     the channels, and anything that's a java object.
;   - For the reason above, images should be kept in a local database and not as a reference in the token. The tokens
;     should only store the keyword for such database.

; GUI EXPANSION AND THE CHAT:
;   - Once we have users, the GUI should be expanded, adding a chat, a GM panel and a user panel.
;   - A user may select his username in the user panel so the GM can refer to him with the user and not the ID.
;   - The chat must accept normal text and commands/macros.

; TOWARDS A BETTER GUI LIBRARY:
;   - All those UI changes will probably require a rework of the GUI so it's more easily constructed.
;   - A good initial approach for that should be statically building a Swing Object from clojure maps/arrays but Once
;     we have that, use the Swing object to perform updates/lookups with maybe some clojure wrapper function just
;     for the sake of simplicity.

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
            (>!! (:app->server (:action-channels @world)) (mk-action "move-token" [lifted-token-locator lifted-token-new-tile]))
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

(comment (>!! (:app->server (:action-channels @-world)) (mk-action "move-token" [{:tx 10 :ty 10 :id "token-1"} [5 10]]))
         (alter-var-root #'*out* (constantly *out*))
         (swap! -world execute-action (mk-action "move-token" [{:tx 4 :ty 5 :id "token-2"} [0 0]]))
         (repaint! -world)
         (pprint @-world)
         (:client @-world)
         (>!! (:write-chan (:client @-world)) "pet")
         (<!! (:read-chan (:client @-world))))

(defn run-the-server! []
  (def server (create-server! 8080)))

(comment ((:shutdown server)))

(defn start-gui! [] 
  (SwingUtilities/invokeLater 
    (fn [] 
      (let [repaint-channel (make-repaint-channel)
            action-channels (make-action-channels)
            event-listeners (atom main-events)
            client (create-client! "127.0.0.1" 8080)
            id-gen (t/make-token-id-generator)
            id-atom (atom nil)
            world (atom (-> {:grid-type :pointy-hex
                             :size 30
                             :dimensions [10 10]
                             :transaction nil
                             :tokens {}
                             :repaint-channel repaint-channel
                             :action-channels action-channels
                             :client client
                             :id-atom id-atom}
                            (add-token id-gen 3 2 :goblin)
                            (add-token id-gen 4 5 :knight)
                            (add-token id-gen 1 1 :knight)))
            __ (def -world world)
            ;__ (def repaint-channel repaint-channel)
            ;__ (def action-channel action-channel)
            ;__ (def event-listeners event-listeners)

            ;; TODO: Swing GUI wrappers...
            info-label (label "info-label" "Hello World!") 
            panel (grid-panel "grid-panel" world)
            top-panel (grid-bag-layout 
                        [panel
                         [:position 0 0]
                         [:span 5 5]
                         ]
                        [info-label
                         [:position 0 10]]
                        [(label "Grid dimensions:")
                         [:position 5 0]]
                        [(label "Tile size:")
                         [:position 5 1]]
                        [(label "Another field:")
                         [:position 5 2]]
                        [(text-field "25x25")
                         [:position 6 0]]
                        [(text-field "32px")
                         [:position 6 1]]
                        [(text-field "foo()")
                         [:position 6 2]])
            listener (mouse-listener world top-panel event-listeners)
            ]
        (.addMouseListener panel listener)
        (.addMouseMotionListener panel listener)
        (make-window! top-panel "Closures & Dragons")
        
        (UIManager/setLookAndFeel (UIManager/getSystemLookAndFeelClassName))
        (start-repaint-process! repaint-channel top-panel)
        (start-action-receiver! action-channels world)
        (bind-client! client action-channels id-atom)))))

(start-gui!)

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
