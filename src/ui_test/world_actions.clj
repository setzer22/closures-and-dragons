(ns ui-test.world-actions
  (:require [ui-test.world-commands :as wc :refer :all]
            [ui-test.repaint-channel :refer :all]
            [clojure.core.async :as a :refer [<! >! <!! >!! go chan]]))

(def action-fns
 {"move-token" wc/move-token})

;TODO: NEEDS REPAINT IS NOT IMPLEMENTED RIGHT
(defn mk-action 
  ([action-name action-args]
   {:action-name action-name
    :action-args action-args
    :needs-repaint? true; Temporary patch
    })
  ([action-name action-args needs-repaint?]
   (assoc (mk-action action-name action-args) 
          :needs-repaint? needs-repaint?)))

(defn execute-action [world {:keys [action-name, action-args] :as action}]
  (let [action-fn (get action-fns action-name)]
    (apply action-fn world action-args)))

(defn start-action-receiver! [{:keys [server->app]} world-atom]
  (a/go-loop []
             (do (println "iteration wat")
                 (when-let [action (<! server->app)] 
                   (println "action-receiver: " action)
                   (swap! world-atom execute-action action)
                   (println "updated world.")
                   (when (:needs-repaint? action)
                     (repaint! world-atom))
                   (println "repainted!")
                   (recur)))))

(defn make-action-channels []
  {:app->server (chan 32)
   :server->app (chan 32)})

;(def -world ui-test.core/-world)
;(def test-channel (:app->server (:action-channels @world)))

;(def locator (token-locator (get-one-token-at @world [1 1]) [1 1]))

;(start-action-receiver! world test-channel)

;(>!! test-channel (mk-action "move-token" [locator [5 5]] true))

