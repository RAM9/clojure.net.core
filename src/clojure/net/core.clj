(ns clojure.net.core
  (:require [clojure.net.cmd :as cmd]
            [aleph.tcp :as tcp]
            [overtone.at-at :as at])
  (:use [lamina.core]
        [clojure.tools.logging :only (debug info warn)]))

(def monitor-pool (at/mk-pool))

(defn node-info [host port]
   {:host host
   :port port})

(defn server-node [ninfo]
  {:ninfo ninfo
   :conn nil
   :rnodes {}
   :oconns #{}})

(defn server-node! [ninfo]
  (agent (server-node ninfo)))

(defn remote-node [ninfo conn]
  {:ninfo ninfo
   :conn conn})

(defn remote-node! [ninfo conn]
  (agent (remote-node ninfo conn)))

(defn nkey
  ([ninfo] (nkey (:host ninfo) (:port ninfo)))
  ([host port] [host port]))

(defn rninfos [{:keys [rnodes]}]
  (into [] (map (fn [[_ rnode!]] (:ninfo @rnode!)) rnodes)))

(defn process [rnode! msg]
  (debug "received" msg))

(defn rnode-pipeline [rnode!]
  (pipeline
    read-channel
    #(process rnode! %)
    (fn [_] (restart))))

(defn add-node [{rnodes :rnodes sninfo :ninfo :as server} conn {:keys [ninfo] :as msg}]
  (if (rnodes (nkey ninfo))
    (do (info (nkey ninfo) "failed to join" (nkey sninfo))
      [false server])
    (let [rnode! (remote-node! ninfo conn)]
      (info (nkey ninfo) "joined" (nkey sninfo))
      ((rnode-pipeline rnode!) conn)
      [true (assoc server :rnodes (assoc rnodes (nkey ninfo) rnode!))])))

(defn handle-join2 [server conn msg]
  (let [[joined? server2] (add-node server conn msg)]
    (if joined?
      (enqueue conn {:type :ok
                     :ninfo (:ninfo server)
                     :rninfos (rninfos server)})
      (enqueue-and-close conn {:type :error}))
    server2))

(defn handle-join [server! conn]
  (run-pipeline
    conn
    read-channel
    (fn [msg]
      (if (= (:type msg) :join)
        (send-off server! handle-join2 conn msg)
        (throw (Exception. "first message was not join!"))))))

(defn server-handler [server!]
  (fn [result-conn client-info]
    (run-pipeline
      result-conn
      (fn [conn] (handle-join server! conn)))))

(defn monitor [server!]
  (let [rnodes (:rnodes @server!)]
    (doseq [[_ rnode!] rnodes]
      (enqueue (:conn @rnode!) {:type :ping}))))

(defn start-monitor [server!]
  (send-off
    server!
    (fn [server]
      (let [monitor-task (at/every 2000 #(monitor server!) monitor-pool)]
        (with-meta server {:monitor-task monitor-task})))))

(defn start [ninfo]
  (info "starting server" ninfo)
  (let [server! (server-node! ninfo)
        conn (tcp/start-tcp-server (server-handler server!) {:port (:port ninfo)
                                                             :frame cmd/frame})]
    (send-off server! assoc :conn conn)
    (start-monitor server!)
    server!))

(declare sjoin)

(defn sjoin-all [server! rninfos]
  (doseq
    [ninfo rninfos]
    (sjoin server! (:host ninfo) (:port ninfo))))

(defn sjoin3 [server server! conn msg]
  (let [[joined? server2] (add-node server conn msg)]
    (if joined?
      (do
        (sjoin-all server! (:rninfos msg)))
      (do
        (enqueue conn {:type :error})
        (close conn)))
    server2))

(defn sjoin2 [server! conn]
  (enqueue conn {:type :join
                 :ninfo (:ninfo @server!)})
  (run-pipeline
    conn
    read-channel
    (fn [msg]
      (if (= (:type msg) :ok)
        (do
          (send-off server! sjoin3 server! conn msg))
        (do
          (close conn))))))

(defn sjoin [server! host port]
  (let [result-conn (tcp/tcp-client
               {:host host
                :port port
                :frame cmd/frame})]
    (run-pipeline
      result-conn
      #(sjoin2 server! %))))

(defn run-test []
  (def serv1 (start (node-info "localhost" 6661)))
  (def serv2 (start (node-info "localhost" 6662)))
  (def serv3 (start (node-info "localhost" 6663)))
  ;(def serv4 (start (node-info "node4" "localhost" 6664)))
  ;(def serv5 (start (node-info "node5" "localhost" 6665)))

  (sjoin serv2 "localhost" 6661)
  (sjoin serv3 "localhost" 6661))

;(cjoin! (:server! serv2) "localhost" 6661)
;(cjoin! (:server! serv3) "localhost" 6661)
;(cjoin! (:server! serv4) "localhost" 6661)
;(cjoin! (:server! serv5) "localhost" 6661))

(defn -main
  "I don't do a whole lot."
  [& args]
  (run-test)
  (println "Hello, World!"))
