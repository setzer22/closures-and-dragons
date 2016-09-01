(ns ui-test.server-communication
  (:require [ui-test.utils :refer :all]
            [clojure.core.async :as a :refer [<! >! <!! >!! go chan]]
            [net.async.tcp :as tcp :refer :all]))

;; ===SERVER===

(def generate-connection-id (id-generator "connection"))

(defn edn->bytes [data]
    (.getBytes (str data)))

(let [byte-array-type (type (byte-array 0))] 
  (defn bytes-or-edn->edn [data]
    (if (= byte-array-type (type data))
      (binding [*read-eval* false] (read-string (String. data)))
      data)))

;TODO: With the current model, if one hangs, the others will suffer...
; Solution: Spawn a thread for every client and don't wait for them to finish writing. Close a channel when "disconnect" event arrives. The thread waiting to write to the disconnected channel will receive a nil and then close.
(defn start-connection! [connection alive-connections-atom]
  (let [id (generate-connection-id)
        connection (assoc connection :id id)]
    (swap! alive-connections-atom assoc id connection)
    (>!! (:write-chan connection) (edn->bytes {:connection true, :id id}))
    (a/go-loop 
      []
      (when-let [read-data (<! (:read-chan connection))]
        (str "MSG["id"]: " (bytes-or-edn->edn read-data))
        (if-not (keyword? read-data) 
          (do 
            (vals @alive-connections-atom)
            (doseq [other-connection (vals @alive-connections-atom)]
              (>! (:write-chan other-connection) read-data)))
          (println "CONNECTION STATE: " read-data))
        (recur)))))

(defn start-server! [port event-loop alive-connections-atom]
  (let [server (accept event-loop {:port port})]
    (a/go-loop []
      (when-let [connection (<! (:accept-chan server))]
        (println "Connection!")
        (start-connection! connection alive-connections-atom))
      (recur))))

(defn create-server! [port]
  (let [alive-connections-atom (atom {})
        ev-loop (event-loop)]
    (start-server! port ev-loop alive-connections-atom)
    {:shutdown (fn [] (shutdown! ev-loop))
     :event-loop ev-loop
     :alive-connections-atom alive-connections-atom}))

;; ===CLIENT===

(defn create-client! [host port]
  (let [ev-loop (event-loop)
        {:keys [read-chan write-chan]} (connect ev-loop {:host host :port port})
        app->server (chan 32)
        server->app (chan 32)] 
    (a/go-loop []
      (when-let [data (<! app->server)]
        (println "client-sending: " data)
        (>! write-chan (edn->bytes data))
        (recur)))
    (a/go-loop []
      (when-let [data (<! read-chan)]
        (println "client-reciving: " (bytes-or-edn->edn data))
        (let [decoded-data (bytes-or-edn->edn data) ]
          (>! server->app decoded-data))
        (recur)))
    {:write-chan app->server
     :read-chan server->app}))

(defn action? [maybe-action]
  (and (map? maybe-action) (:action-name maybe-action) (:action-args maybe-action)))

(defn bind-client! [client action-channel]
  (a/go-loop []
    (when-let [action (<! (:read-chan client))]
      (println "got-from-server: " action)
      (when (action? action) (>! (:server->app action-channel) action)) ; The action? check shouldn't go here...
      (recur)))
  (a/go-loop 
    []
    (when-let [action (<! (:app->server action-channel))]
      (println "going to server: " action)
      (>! (:write-chan client) action)
      (recur))))


  
