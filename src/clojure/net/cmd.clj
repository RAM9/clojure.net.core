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
  handshake-complete
  {:type :handshake-complete})

(defcodec
  ctype
  (enum
    :byte
    :handshake
    :handshake-complete))

(defcodec frame
          (header
            ctype
            {:handshake handshake
             :handshake-complete handshake-complete}
            :type))
