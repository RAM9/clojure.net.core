(ns clojure.net.cmd
  (:use [gloss core io]))

(defcodec
  nstring
  (finite-frame
    :int16
    (string :utf-8)))

(defcodec
  ninfo
  {:type :ninfo
   :ninfo {:host nstring
           :port :int32}})

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
  napply
  {:type :napply
   :id :int32
   :fun nstring
   :args nstring})

(defcodec
  napply-result
  {:type :napply-result
   :id :int32
   :result nstring})

(defcodec
  ctype
  (enum
    :byte
    :ninfo
    :status
    :napply
    :napply-result))

(defcodec
  frame
  (header
    ctype
    {:ninfo ninfo
     :status status
     :napply napply
     :napply-result napply-result}
    :type))
