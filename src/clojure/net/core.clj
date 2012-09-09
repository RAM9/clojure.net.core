(ns clojure.net.core
  (:require [clojure.net.cmd :as cmd]
            [aleph.tcp :as tcp]
            [overtone.at-at :as at])
  (:use [lamina.core]
        [clojure.tools.logging :only (debug info warn)]))

(defrecord ServerNode [ninfo conn rnodes open-conns])
(defrecord NodeInfo [id host port])

(defn ninfo [id host port]
  (NodeInfo.
    id
    host
    port))

(defn snode [ninfo]
  (ServerNode.
    ninfo
    nil
    #{}
    #{}))

(defn snode! [ninfo]
  (agent (snode ninfo)))

(defn server-handler [server!]
  (fn [result-conn client-info]))

(defn start [ninfo]
  (info "starting server" ninfo)
  (let [server! (snode! ninfo)
        conn (tcp/start-tcp-server (server-handler server!) {:port (:port ninfo)
                                                              :frame cmd/frame})]
    (send-off server! assoc :conn conn)
    server!))

(defn -main
  "I don't do a whole lot."
  [& args]
  (println "Hello, World!"))
