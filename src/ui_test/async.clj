(ns ui-test.async
  (:require [clojure.core.async :as a :refer [<! >! <!! >!! go chan]]))

(def http-channel (chan))

(def log (atom ""))

(defn print-to-log [s]
  (swap! log (constantly (str @log s))))

(defn start-message-receiver! 
  [in]
  (go (while true (print-to-log (<! in)))))

(start-message-receiver! http-channel)

