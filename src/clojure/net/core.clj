(ns clojure.net.core
  (:require [clojure.net.cmd :as cmd]
            [aleph.tcp :as tcp]
            [overtone.at-at :as at])
  (:use [lamina.core]
        [clojure.tools.logging :only (debug info warn)]))

(defn ninfo [host port]
   {:host host
   :port port})

(defn snode [ninfo]
  {:ninfo ninfo
   :conn nil
   :rnodes #{}})

(defn snode! [ninfo]
  (agent (snode ninfo)))

(defn add-node [{:keys [rnodes] :as server} conn {:keys [ninfo] :as msg}])

(defn handle-join [server! conn]
  (run-pipeline
    conn
    read-channel
    (fn [msg]
      (debug "receive" msg)
      (if (= (:type msg) :join)
        (do)
        ;(send-off server! add-node conn msg)
        (throw (Exception. "first message was not join!"))))))

(defn server-handler [server!]
  (fn [result-conn client-info]
    (run-pipeline
      result-conn
      (fn [conn] (handle-join server! conn)))))

(defn start [ninfo]
  (info "starting server" ninfo)
  (let [server! (snode! ninfo)
        conn (tcp/start-tcp-server (server-handler server!) {:port (:port ninfo)
                                                             :frame cmd/frame})]
    (send-off server! assoc :conn conn)
    server!))

(defn sjoin2 [server! conn]
  (enqueue conn {:type :join
                 :ninfo (:ninfo @server!)}))

(defn sjoin [server! host port]
  (let [result-conn (tcp/tcp-client
               {:host host
                :port port
                :frame cmd/frame})]
    (run-pipeline
      result-conn
      #(sjoin2 server! %))))

(defn run-test []
  (def serv1 (start (ninfo "localhost" 6661)))
  (def serv2 (start (ninfo "localhost" 6662)))
  ;(def serv3 (start (ninfo "node3" "localhost" 6663)))
  ;(def serv4 (start (ninfo "node4" "localhost" 6664)))
  ;(def serv5 (start (ninfo "node5" "localhost" 6665)))

  (sjoin serv1 "localhost" 6662))

;(cjoin! (:server! serv2) "localhost" 6661)
;(cjoin! (:server! serv3) "localhost" 6661)
;(cjoin! (:server! serv4) "localhost" 6661)
;(cjoin! (:server! serv5) "localhost" 6661))

(defn -main
  "I don't do a whole lot."
  [& args]
  (run-test)
  (println "Hello, World!"))
