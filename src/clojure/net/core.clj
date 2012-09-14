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

(defn new-connection [node]
  {:node node
   :status {}})

(defn new-connection! [node]
  (agent (new-connection node)))

(def read-handshake
  (pipeline
    #(with-timeout timeout (read-channel %))
    (fn [msg]
      (if (= (:type msg) :handshake)
        msg
        (throw (Exception. "expected a handshake"))))))

(defn handshake-complete [{connections :connections :as kernel} node]
  (if (connections node)
    (do
      (info (:node kernel) "failed to join" node)
      [false kernel])
    (do
      (info (:node kernel) "joined" node)
      [true
       (assoc
         kernel
         :connections
         (assoc
           connections
           node
           (new-connection! node)))])))

(defn send-handshake [conn node]
  (enqueue conn {:type :handshake
                 :node node}))

(defn handshake-and-respond [kernel conn node]
  (let [[joined? kernel2] (handshake-complete kernel node)]
    (if joined?
      (send-handshake conn (:node kernel))
      (close conn))
    kernel2))

(defn handshake-they-started [kernel! conn]
  (run-pipeline
    conn
    read-handshake
    #(send-off kernel! handshake-and-respond conn (:node %))))

(defn handshake-we-complete [kernel conn node]
  (let [[joined? kernel2] (handshake-complete kernel node)]
    (if-not joined?
      (close conn))))

(defn handshake-we-started [kernel! conn]
  (send-handshake conn (:node @kernel!))
  (run-pipeline
    conn
    read-handshake
    #(send-off kernel! handshake-we-complete conn (:node %))))

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

(defn connect [kernel! host port]
  (let [result-conn (tcp/tcp-client
                      {:host host
                       :port port
                       :frame cmd/frame})]
    (run-pipeline
      result-conn
      #(handshake-we-started kernel! %))))

(defn run-test []
  (def serv1 (init (new-node "localhost" 6661)))
  (def serv2 (init (new-node  "localhost" 6662)))

  (connect serv1 "localhost" 6662))

(defn -main
  "I don't do a whole lot."
  [& args]
  (run-test)
  (println "Hello, World!"))
