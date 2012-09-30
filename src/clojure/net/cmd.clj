(ns clojure.net.cmd
  (:use [gloss core io]))

(defcodec
  host
  (finite-frame
    :int16
    (string :utf-8)))

(defcodec port :int32)

(defcodec
  ninfo
  {:type :ninfo
   :ninfo {:host host
           :port port}})

(defcodec
  status
  {:type :status
   :status (enum
             :byte
             :ok
             :ok-simultaneous
             :nok
             :not-allowed
             :alive)})

(defcodec
  ctype
  (enum
    :byte
    :ninfo
    :status))

(defcodec frame
          (header
            ctype
            {:ninfo ninfo
             :status status}
            :type))
