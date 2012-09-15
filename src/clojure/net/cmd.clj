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
  handshake
  {:type :handshake
   :node node})

(defcodec
  ctype
  (enum
    :byte
    :handshake))

(defcodec frame
          (header
            ctype
            {:handshake handshake}
            :type))
