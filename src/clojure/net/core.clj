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

(defn new-connection [kernel! conn node status]
  {:kernel! kernel!
   :conn conn
   :node node
   :status status})

(defn new-connection! [kernel! conn node status]
  (agent (new-connection kernel! conn node status)))

(defn new-status [type]
  {:type type})


(defn nodes [{connections :connections}]
  (into [] (map (fn [[_ connection!]] (:node @connection!)) connections)))

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

(defn process [connection! msg]
  (debug "recieved" msg))

(defn main-loop [connection! conn]
  (debug "main-loop")
  (receive-all
    conn
    #(process connection! %)))

(defn handshake-complete [{connections :connections :as kernel} kernel! conn node status]
  (if (connections node)
    (do
      (info (:node kernel) "failed to join" node)
      [false kernel])
    (let [connection! (new-connection! kernel! conn node status)]
      (info (:node kernel) "joined" node)
      [true
       (assoc
         kernel
         :connections
         (assoc
           connections
           node
           connection!))])))

(defn send-handshake [conn node]
  (enqueue conn {:type :handshake
                 :node node}))

(defn send-handshake-complete [conn]
  (enqueue conn {:type :handshake-complete}))

(defn send-full-join [kernel conn]
  (enqueue conn {:type :join
                 :nodes (nodes kernel)}))

(defn pending-handshake-complete [connection connection! kernel]
  (let [conn (:conn connection)]
    (main-loop connection! conn)
    (send-full-join kernel conn)
    (assoc connection :status (new-status :active))))

(defn handshake-and-respond [kernel kernel! conn node]
  (let [[joined? kernel2] (handshake-complete kernel kernel! conn node (new-status :pending))]
    (if joined?
      (do
        (send-handshake conn (:node kernel))
        (run-pipeline
          conn
          read-handshake-complete
          (fn [_]
            (let [connection! ((:connections kernel2) node)]
              (send-off connection! pending-handshake-complete connection! kernel2)))))
      (close conn))
    kernel2))

(defn handshake-they-started [kernel! conn]
  (run-pipeline
    conn
    read-handshake
    #(send-off kernel! handshake-and-respond kernel! conn (:node %))))

(defn handshake-we-complete [kernel kernel! conn node]
  (let [[joined? kernel2] (handshake-complete kernel kernel! conn node (new-status :active))]
    (if joined?
      (do
        (send-handshake-complete conn)
        (main-loop ((:connections kernel2) node) conn))
      (close conn))
    kernel2))

(defn handshake-we-started [kernel! conn]
  (send-handshake conn (:node @kernel!))
  (run-pipeline
    conn
    read-handshake
    #(send-off kernel! handshake-we-complete kernel! conn (:node %))))

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

(defn connected? [kernel node]
  ((:connections kernel) node))

(defn connect [kernel! host port]
  (if-not (connected? @kernel! (new-node host port))
    (let [result-conn (tcp/tcp-client
                        {:host host
                         :port port
                         :frame cmd/frame})]
      (run-pipeline
        result-conn
        #(handshake-we-started kernel! %)))))

(defn run-test []
  (def serv1 (init (new-node "localhost" 6661)))
  (def serv2 (init (new-node "localhost" 6662)))

  (connect serv1 "localhost" 6662))

(defn -main
  "I don't do a whole lot."
  [& args]
  (run-test)
  (println "Hello, World!"))
