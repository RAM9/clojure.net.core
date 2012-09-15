(ns clojure.net.cmd
  (:use [gloss core io]))

(defcodec
  host
  (finite-frame
    :int16
    (string :utf-8)))

(defcodec port :int32)

(defcodec
  node
   {:host host
   :port port})

(defcodec
  nodes
  (finite-frame
    :int16
    (repeated node)))

(defcodec
  handshake
  {:type :handshake
   :node node})

(defcodec
  handshake-complete
  {:type :handshake-complete})

(defcodec
  join
  {:type :join
   :nodes nodes})

(defcodec
  ctype
  (enum
    :byte
    :handshake
    :handshake-complete
    :join))

(defcodec frame
          (header
            ctype
            {:handshake handshake
             :handshake-complete handshake-complete
             :join join}
            :type))
