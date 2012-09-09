(ns clojure.net.cmd
  (:use [gloss core io]))

(defcodec id (finite-frame :int16
                           (string :utf-8)))
(defcodec host (finite-frame :int16
                             (string :utf-8)))
(defcodec port :int32)

(defcodec ninfo {:host host
                 :port port})

(defcodec ninfos (finite-frame
                   :int32
                   (repeated ninfo)))

(defcodec ctype (enum :byte :join :ok :ping :error))

(defcodec join {:type :join
                :ninfo ninfo})

(defcodec ok {:type :ok
              :ninfo ninfo
              :remote-nodes ninfos})

(defcodec ping {:type :ping})

(defcodec error {:type :error})

(defcodec frame
          (header
            ctype
            {:join join
             :ok ok
             :ping ping
             :error error}
            :type))
