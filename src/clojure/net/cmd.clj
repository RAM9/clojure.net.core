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

;(defcodec ninfo {:host host
                 ;:port port})

;(defcodec ninfos (finite-frame
                   ;:int32
                   ;(repeated ninfo)))

;(defcodec ctype (enum :byte :join :ok :ping :error))

;(defcodec join {:type :join
                ;:ninfo ninfo})

;(defcodec ok {:type :ok
              ;:ninfo ninfo
              ;:rninfos ninfos})

;(defcodec ping {:type :ping})

;(defcodec error {:type :error})

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
