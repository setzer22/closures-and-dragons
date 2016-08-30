(ns ui-test.world-actions
  (:require [ui-test.world-commands :as wc :refer :all]
            [ui-test.repaint-channel :refer :all]
            [clojure.core.async :as a :refer [<! >! <!! >!! go chan]]))

(def action-fns
 {"move-token" wc/move-token})

(defn mk-action 
  ([action-name action-args]
   {:action-name action-name
    :action-args action-args})
  ([action-name action-args needs-repaint?]
   (assoc (mk-action action-name action-args) 
          :needs-repaint? needs-repaint?)))

(defn execute-action [world {:keys [action-name, action-args] :as action}]
  (let [action-fn #spy/d (get action-fns action-name)]
    (apply action-fn world action-args)))

(defn start-action-receiver! [world-atom input-channel]
  (go 
    (loop [action (<! input-channel)]
      (when action 
        (swap! world-atom execute-action action)
        (when (:needs-repaint? action)
          (repaint! world-atom))
        (recur (<! input-channel))))))

(def test-channel (chan))
(def world ui-test.core/world)

(def locator (token-locator (get-one-token-at @world [5 5]) [5 5]))

(start-action-receiver! world test-channel)

(>!! test-channel (mk-action "move-token" [locator [1 1]] true))

