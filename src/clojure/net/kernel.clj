(ns clojure.net.kernel
  (:require [clojure.net.cmd :as cmd]
            [aleph.tcp :as tcp]
            [overtone.at-at :as at])
  (:use [lamina.core]
        [clojure.tools.logging :only (debug info warn)]))

(def timeout 2000)

(defn serialize [form]
  (let [writer (java.io.StringWriter.)]
    (print-dup form writer)
    (.toString writer)))

(defn deserialize [string]
  (let [reader (java.io.PushbackReader. (java.io.StringReader. string))]
    (read reader)))

(defn dbg-connection [{:keys [kernel!] :as connection}]
  (debug (:ninfo @kernel!) "->" (:ninfo connection) ":" (closed? (:conn connection))))

(defn new-node [kernel! ninfo]
  {:kernel! kernel!
   :ninfo ninfo})

(defn new-ninfo [host port]
  {:host host
   :port port})

(defn new-kernel [ninfo]
  {:kernel! nil
   :ninfo ninfo
   :conn nil
   :connections {}
   :plugins {}})

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
    :conn conn
    :plugins {}}))

(defn new-connection! [& args]
  (let [connection! (agent (apply new-connection args))]
    (send-off connection! #(assoc % :connection! connection!))))

(defn assoc-connection [{:keys [connections] :as kernel} ninfo connection!]
  (assoc
    kernel
    :connections
    (assoc connections ninfo connection!)))

(defn dissoc-connection [{:keys [connections] :as kernel} ninfo]
  (assoc
    kernel
    :connections
    (dissoc connections ninfo)))

(defn connected? [kernel ninfo]
  ((:connections kernel) ninfo))

(defn pending? [connection]
  (= (:status connection) :pending))

(defn alive? [connection]
  (= (:status connection) :alive))

(defn v-ninfo [ninfo]
  [(:host ninfo) (:port ninfo)])

(defn simultaneous? [{:keys [kernel!] :as connection}]
  (let [kninfo (:ninfo @kernel!)
        cninfo (:ninfo connection)]
    (< 0 (.compareTo (v-ninfo kninfo) (v-ninfo cninfo)))))

(def message-handlers! (agent {}))
(defn register-handler [mtype handler]
  (send-off
    message-handlers!
    (fn [message-handlers]
      (let [handlers (message-handlers mtype)]
        (if handlers
          (assoc message-handlers mtype (conj handlers handlers))
          (assoc message-handlers mtype #{handler}))))))

(defn process [connection! {mtype :type :as msg}]
  (debug "received" msg)
  (let [message-handlers @message-handlers!
        handlers (message-handlers mtype)]
    (if handlers
      (doseq [handler handlers]
        (handler connection! msg)))))

(defn handle-disconnect [connection!]
  (send-off
    connection!
    #(assoc % :status :disconnected)))

(defn main-loop [{:keys [connection! conn] :as connection}]
  (receive-all
    conn
    #(process connection! %))
  (on-closed
    conn
    (fn []
      (debug "disconnected")
      (handle-disconnect connection!)
      (process connection! {:type :disconnect}))))

(defn alive [{:keys [kernel! connection!] :as connection}]
  (info (:ninfo @kernel!) "->" (:ninfo connection) "is live")
  (process connection! {:type :alive})
  (main-loop connection)
  (assoc connection :status :alive))

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
        (send-off connection! alive)
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

(defn start-kernel [kernel!]
  (let [ninfo (:ninfo @kernel!)
        conn (tcp/start-tcp-server
               (accept kernel!)
               {:port (:port ninfo)
                :frame cmd/frame})]
    (info "started" ninfo)
    (send-off kernel! #(assoc % :conn conn))))

(defn ashake-recv-status [{:keys [conn] :as connection} {status :status}]
  (debug "status is" status)
  (cond
    (or (= :ok status) (= :ok-simultaneous status)) (do
                                                      (debug "ok to continue")
                                                      (alive connection))
    (= :nok status) (do
                      (debug "nok to continue")
                      connection)
    (= :alive status) (do
                        ;TODO should be confirming the new conn or not
                        ;if confirmed, replace the existing connection
                        ;with this one, otherwise close this one
                        (debug "connection already alive")
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
  (let [result (promise)]
    (send-off
      kernel!
      (fn [kernel]
        (if (connected? kernel ninfo)
          (do
            (debug (:ninfo kernel) "already connected to" ninfo)
            (deliver result ((:connections kernel) ninfo))
            kernel)
          (let [connection! (new-connection! kernel! ninfo)]
            (deliver result connection!)
            (ashake connection!)
            (debug "connect associate" (:ninfo kernel) "with" ninfo)
            (assoc-connection kernel ninfo connection!)))))
    result))

(defn node-to-connection! [{:keys [kernel! ninfo]}]
  ((:connections @kernel!) ninfo))

(defn disconnect [node]
  (let [connection! (node-to-connection! node)]
    (if connection!
      (close (:conn @connection!)))))
