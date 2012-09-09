(ns clojure.net.cmd
  (:use [gloss core io]))

(defcodec id (finite-frame :int16
                                (string :utf-8)))
(defcodec host (finite-frame :int16
                             (string :utf-8)))
(defcodec port :int32)

(defcodec node-info {:id id
                :host host
                :port port})

(defcodec node-infos (finite-frame
                       :int32
                       (repeated node-info)))

(defcodec ctype (enum :byte :join :ok :ping :error))

(defcodec join {:type :join
                :node-info node-info})

(defcodec ok {:type :ok
              :node-info node-info
              :remote-nodes node-infos})

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
