(ns clojure.net.napply
  (:require [clojure.net.util :as util])
  (:use [clojure.net.kernel]
        [lamina.core]
        [clojure.tools.logging :only (debug info warn)]))

(defn ensure-napply [obj]
  (let [cmeta (meta obj)
        napply (:napply cmeta)]
    (if napply
      obj
      (with-meta
        obj
        (assoc
          cmeta
          :napply
          {})))))

(defn get-field [obj field]
  (field (:napply (meta obj))))

(defn set-field [obj field value]
  (let [obj (ensure-napply obj)
        cmeta (meta obj)
        napply (:napply cmeta)]
    (with-meta
      obj
      (assoc
        cmeta
        :napply
        (assoc napply field value)))))

(defn attach-result [connection id result]
  (let [connection (ensure-napply connection)
        cmeta (meta connection)
        results (:results cmeta)]
    (set-field
      connection
      :results
      (assoc results id result))))

(defmacro napply [node fun & args]
  `(-napply ~node '~fun ~@args))

(defn -napply [node fun & args]
  (let [result (result-channel)
        connection! (node-to-connection! node)]
    (if connection!
      (send-off
        connection!
        (fn [{:keys [conn] :as connection}]
          (if (alive? connection)
            (let [[connection id] (util/inc-counter connection :mseq-id)]
              (enqueue conn {:type :napply
                             :id id
                             :fun (serialize fun)
                             :args (serialize args)})
              (attach-result connection id result))
            (do
              (error result :error :not-alive)
              connection))))
      (error result {:error :not-connected}))
    @result))
