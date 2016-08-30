(ns ui-test.repaint-channel
  (:require [clojure.core.async :as a :refer [<! >! <!! >!! go go-loop chan dropping-buffer]]))

(defn make-repaint-channel []
  (chan (dropping-buffer 1)))

(defn start-repaint-process! [repaint-channel top-panel]
  (go-loop 
    [repaint? (<! repaint-channel)]
    (when repaint?
      (.repaint top-panel)
      (recur (<! repaint-channel)))) )

(defn repaint! [world]
  (>!! (:repaint-chanel @world) true))
