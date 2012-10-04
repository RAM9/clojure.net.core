(ns clojure.net.napply
  (:use [lamina.core]
        [clojure.net.kernel]
        [clojure.tools.logging :only (debug info warn)]))

(defn -napply [node fun & args]
  (node-send
    node
    (fn [{:keys [conn op-seq] :as connection}]
      (let [result (result-channel)]
        (enqueue conn {:type :napply
                       :id op-seq
                       :fun (serialize fun)
                       :args (serialize args)})
        [(assoc-result connection op-seq result) result]))))

(defmacro napply [node fun & args]
  `('~-napply ~node '~fun ~@args))

(register-handler
  :napply
  (fn [connection! {:keys [id fun args]}]
    (csend
      connection!
      (fn [{:keys [conn] :as connection}]
        (let [dfun (eval (deserialize fun))
              dargs (deserialize args)
              result (serialize (apply dfun dargs))]
                (enqueue conn {:type :napply-result
                               :id id
                               :result result})
                [connection :ok])))))

(register-handler
  :napply-result
  (fn [connection! {:keys [id result]}]
    (csend
      connection!
      (fn [connection]
        [(enqueue-result connection id result) :ok]))))
