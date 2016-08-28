(ns ui-test.transaction)

(defmacro transaction [& {:keys [start end rollback]}]
  `{:start (fn [& _#] ~start)
    :end (fn [& _#] ~end)
    :rollback (fn [& _#] ~rollback)
    :cleanup (constantly true)
    :finished false})

(defn finish! [tr]
  (swap! tr assoc :finished true)
  ((:cleanup @tr)))

(defn start! [tr]
  (assert (not (:finished @tr)))
  ((:start @tr)))

(defn cancel! [tr]
  (when-not (:finished @tr) 
    ((:rollback @tr))
    (finish! tr)))

(defn commit! [tr]
  (when-not (:finished @tr) 
    ((:end @tr))
    (finish! tr)))

(defn start-transaction [world tr]
  (when (:transaction @world) 
    (throw (Exception. "There is a transaction in place already.")))
  (let [bound-transaction (assoc tr :cleanup (fn [] 
                                               (swap! world dissoc :transaction)
                                               (println "cleanup")))
        transaction-atom (atom bound-transaction)] 
    (start! transaction-atom)
    (swap! world assoc :transaction transaction-atom)
    transaction-atom))

(comment 
  (start-transaction 
    fake-world
    (transaction :start (println "start")
                 :end (println "end")
                 :rollback (println "rollback"))))
