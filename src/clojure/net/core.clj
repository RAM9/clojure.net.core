(ns clojure.net.core
  (:require [clojure.net.cmd :as cmd]
            [aleph.tcp :as tcp]
            [overtone.at-at :as at])
  (:use [lamina.core]
        [clojure.tools.logging :only (debug info warn)]))

(def timeout 2000)
(def monitor-pool (at/mk-pool))

(defn new-node [host port]
  {:host host
   :port port})

(defn new-kernel [node]
  {:node node
   :connections {}})

(defn new-kernel! [node]
  (agent (new-kernel node)))

(defn new-connection [conn node status]
  {:conn conn
   :node node
   :status status})

(defn new-connection! [conn node status]
  (agent (new-connection conn node status)))

(defn new-kernel-connection [kernel! connection!]
  {:kernel! kernel!
   :connection! connection!})

(defn active-connections [{connections :connections}]
  (filter
    #(= (:type (:status %)) :active)
    (map (fn [[_ connection!]] @connection!) connections)))

(defn active-nodes-vector [kernel]
  (into [] (map :node (active-connections kernel))))

(def read-handshake
  (pipeline
    #(with-timeout timeout (read-channel %))
    (fn [msg]
      (if (= (:type msg) :handshake)
        msg
        (throw (Exception. "expected a handshake"))))))

(def read-handshake-complete
  (pipeline
    #(with-timeout timeout (read-channel %))
    (fn [msg]
      (if (= (:type msg) :handshake-complete)
        msg
        (throw (Exception. "expected a handshake-complete"))))))

(declare connect)
(defn handle-connect [kernel-connection {:keys [nodes]}]
  (doseq [node nodes]
    (connect (:kernel! kernel-connection) node)))

(def handlers {:connect handle-connect})

(defn process [kernel-connection msg]
  (debug (:node @(:kernel! kernel-connection)) "receive" msg)
  ((handlers (:type msg)) kernel-connection msg))

(defn main-loop [kernel-connection conn]
  (receive-all
    conn
    #(process kernel-connection %)))

(defn send-handshake [conn node]
  (enqueue conn {:type :handshake
                 :node node}))

(defn send-handshake-complete [conn]
  (enqueue conn {:type :handshake-complete}))

(defn send-full-connect [kernel conn]
  (enqueue conn {:type :connect
                 :nodes (active-nodes-vector kernel)}))

(defn notify-active [kernel msg]
  (doseq [connection (active-connections kernel)]
    (enqueue (:conn connection) msg)))

(defn notify-connect [kernel connection!]
  (send-full-connect kernel (:conn @connection!))
  (notify-active kernel {:type :connect
                         :nodes [(:node @connection!)]})
  kernel)

(defn unjoin-node [{:keys [connections] :as kernel} node]
  ()
  (assoc
    kernel
    :connections
    (dissoc connections node)))

(defn connected? [kernel node]
  (or ((:connections kernel) node) (= (:node kernel) node)))

(defn join-node [kernel node]
  (if (connected? kernel node)
    [false kernel]
    (do
      (debug (:node kernel) "joining" node)
      [true
       (assoc
         kernel
         :connections
         (assoc
           (:connections kernel)
           node
           (new-connection! nil node :pending-connection)))])))

(defn handshake-complete [connection conn]
  (send-handshake-complete conn)
  (->
    connection
    (assoc :conn conn)
    (assoc :status :connected)))

(defn complete-handshake-they-started [kernel-connection conn]
  (send-handshake conn (:node @(:kernel! kernel-connection)))
  (run-pipeline
    conn
    read-handshake-complete
    (fn [_]
      (send-off (:connection! kernel-connection) handshake-complete conn))))

(defn handshake-and-respond [kernel kernel! conn node]
  (let [[joined? kernel2] (join-node kernel node)]
    (if joined?
      (do
        (complete-handshake-they-started (new-kernel-connection kernel! ((:connections kernel2) node)) conn))
      (close conn))
    kernel2))

(defn handshake-they-started [kernel! conn]
  (run-pipeline
    conn
    read-handshake
    #(send-off kernel! handshake-and-respond kernel! conn (:node %))))

(defn handshake-we-complete [kernel conn node handshake-node]
  (if (= node handshake-node)
    (do
      (send-off ((:connections kernel) node) handshake-complete conn)
      kernel)
    (do
      (info "handshake node does not match joined node")
      (close conn)
      (unjoin-node kernel node))))

(defn handshake-we-started [kernel! conn node]
  (send-handshake conn (:node @kernel!))
  (run-pipeline
    conn
    read-handshake
    #(send-off kernel! handshake-we-complete conn node (:node %))))

(defn network-handler [kernel!]
  (fn [result-conn client-info]
    (run-pipeline
      result-conn
      #(handshake-they-started kernel! %))))

(defn init [node]
  (let [kernel! (new-kernel! node)
        conn (tcp/start-tcp-server
               (network-handler kernel!)
               {:port (:port node)
                :frame cmd/frame})]
    kernel!))

(defn do-connect [kernel kernel! node]
  (let [[joined? kernel2] (join-node kernel node)]
    (if joined?
      (let [result-conn (tcp/tcp-client
                          {:host (:host node)
                           :port (:port node)
                           :frame cmd/frame})]
        (run-pipeline
          result-conn
          #(handshake-we-started kernel! % node))))
    kernel2))

(defn connect
  ([kernel! host port] (connect kernel! (new-node host port)))
  ([kernel! node]
   (send-off kernel! do-connect kernel! node)))

(defn run-test []
  (def serv1 (init (new-node "localhost" 6661)))
  (def serv2 (init (new-node "localhost" 6662)))
  (def serv3 (init (new-node "localhost" 6663)))

  (connect serv2 "localhost" 6661)
  (connect serv3 "localhost" 6661))

(defn -main
  "I don't do a whole lot."
  [& args]
  (run-test)
  (println "Hello, World!"))
