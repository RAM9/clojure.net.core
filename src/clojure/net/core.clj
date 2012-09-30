(ns clojure.net.core
  (:require [clojure.net.cmd :as cmd]
            [aleph.tcp :as tcp]
            [overtone.at-at :as at])
  (:use [lamina.core]
        [clojure.tools.logging :only (debug info warn)]))

(def timeout 2000)

(defn new-ninfo [host port]
  {:host host
   :port port})

(defn new-kernel [ninfo]
  {:kernel! nil
   :ninfo ninfo
   :conn nil
   :connections {}})

(defn new-kernel! [& args]
  (let [kernel! (agent (apply new-kernel args))]
    (send-off kernel! #(assoc % :kernel! kernel!))))

(defn new-connection
  ([kernel! ninfo] (new-connection kernel! ninfo nil))
  ([kernel! ninfo conn]
   {:connection! nil
    :kernel! kernel!
    :ninfo ninfo
    :status :pending
    :conn conn}))

(defn new-connection! [& args]
  (let [connection! (agent (apply new-connection args))]
    (send-off connection! #(assoc % :connection! connection!))))

(defn assoc-connection [{:keys [connections] :as kernel} ninfo connection!]
  (assoc
    kernel
    :connections
    (assoc connections ninfo connection!)))

(defn connected? [kernel ninfo]
  ((:connections kernel) ninfo))

(defn pending? [connection]
  (= (:status connection) :pending))

(defn v-ninfo [ninfo]
  [(:host ninfo) (:port ninfo)])

(defn simultaneous? [{:keys [kernel!] :as connection}]
  (let [kninfo (:ninfo @kernel!)
        cninfo (:ninfo connection)]
    (< 0 (.compareTo (v-ninfo kninfo) (v-ninfo cninfo)))))

(def recv-ninfo
  (pipeline
    #(with-timeout timeout (read-channel %))
    (fn [msg]
      (if (= (:type msg) :ninfo)
        msg
        (throw (Exception. "expected a ninfo"))))))

(def recv-status
  (pipeline
    #(with-timeout timeout (read-channel %))
    (fn [msg]
      (if (= (:type msg) :status)
        msg
        (throw (Exception. "expected a status"))))))

(defn send-conn-status [conn status]
  (enqueue conn {:type :status
                 :status status}))

(defn send-status [{:keys [conn] :as connection} status]
  (send-conn-status conn status)
  connection)

(defn bshake-already-connected [{:keys [connection!] :as connection} conn]
  (if (pending? connection)
    (if (simultaneous? connection)
      (do
        (close (:conn connection))
        (send-off connection! send-status :ok-simultaneous)
        (send-off connection! #(assoc % :status :alive))
        (debug "SIMULTANEOUS")
        (assoc connection :conn conn))
      (do
        (send-conn-status conn :nok)
        (close conn)
        (debug "NOK")
        connection))
    (do
      (send-conn-status conn :alive)
      (close conn)
      (debug "ALIVE")
      connection)))

(defn bshake-recv-ninfo [{:keys [kernel!] :as kernel} conn {ninfo :ninfo}]
  (debug "ninfo is" ninfo)
  (if (connected? kernel ninfo)
    (let [connection! ((:connections kernel) ninfo)]
      (debug "already connected to" ninfo)
      (send-off connection! bshake-already-connected conn)
      kernel)
    (let [connection! (new-connection! kernel! ninfo conn)]
      (send-off connection! send-status :ok)
      (debug "bshake associate" (:ninfo kernel) "with" ninfo)
      (assoc-connection
        kernel
        ninfo
        connection!))))

(defn bshake [kernel! conn]
  (run-pipeline
    conn
    recv-ninfo
    #(send-off kernel! bshake-recv-ninfo conn %)))

(defn accept [kernel!]
  (fn [result-conn client-info]
    (run-pipeline
      result-conn
      #(bshake kernel! %))))

(defn ashake-recv-status [{:keys [conn] :as connection} {status :status}]
  (debug "status is" status)
  (cond
    (or (= :ok status) (= :ok-simultaneous status)) (do
                                                      (debug "ok to continue")
                                                      (assoc connection :status :alive))
    (= :nok status) (do
                      (debug "nok to continue")
                      (close conn)
                      connection)
    (= :alive status) (do
                        ;TODO should be confirming the new conn or not
                        ;if confirmed, replace the existing connection
                        ;with this one, otherwise close this one
                        (debug "connection already alive")
                        (close conn)
                        connection)))

(defn ashake-send-ninfo [{:keys [kernel! connection!] :as connection} conn]
  (enqueue conn {:type :ninfo
                 :ninfo (:ninfo @kernel!)})
  (run-pipeline
    conn
    recv-status
    #(send-off connection! ashake-recv-status %))
  (assoc
    connection
    :conn
    conn))

(defn ashake [connection!]
  (let [ninfo (:ninfo @connection!)
        result-conn (tcp/tcp-client
                      {:host (:host ninfo)
                       :port (:port ninfo)
                       :frame cmd/frame})]
    (run-pipeline
      result-conn
      #(send-off connection! ashake-send-ninfo %))))

(defn connect [kernel! ninfo]
  (send-off
    kernel!
    (fn [kernel]
      (if (connected? kernel ninfo)
        (do
          (debug (:ninfo kernel) "already connected to" ninfo)
          kernel)
        (let [connection! (new-connection! kernel! ninfo)]
          (ashake connection!)
          (debug "connect associate" (:ninfo kernel) "with" ninfo)
          (assoc-connection kernel ninfo connection!))))))

(defn start-kernel [kernel!]
  (let [ninfo (:ninfo @kernel!)
        conn (tcp/start-tcp-server
               (accept kernel!)
               {:port (:port ninfo)
                :frame cmd/frame})]
    (info "started" ninfo)
    (send-off kernel! #(assoc % :conn conn))))

(defn run-test []
  (def kernel1! (new-kernel! (new-ninfo "localhost" 6661)))
  (def kernel2! (new-kernel! (new-ninfo "localhost" 6662)))
  (def kernel3! (new-kernel! (new-ninfo "localhost" 6663)))

  (start-kernel kernel1!)
  (start-kernel kernel2!)
  (await-for timeout kernel1! kernel2!)

  (connect kernel1! (new-ninfo "localhost" 6662))
  (connect kernel2! (new-ninfo "localhost" 6661))
  )

(defn -main [& args]
  (run-test)
  "Running tests")
