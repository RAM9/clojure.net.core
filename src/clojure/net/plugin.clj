(ns clojure.net.plugin
  (:use [clojure.net.kernel]
        [clojure.tools.logging :only (debug info warn)]))

(defn pensure [{:keys [plugins] :as plugable} pname]
  (let [plugin (plugins pname)]
    (if plugin
      [plugable plugin]
      (let [plugin {}]
        [(assoc
           plugable
           :plugins
           (assoc plugins pname plugin)) plugin]))))

(defn pget [{:keys [plugins] :as plugable} pname field]
  (let [[plugable plugin] (pensure plugable pname)]
    [plugable (plugin field)]))

(defn pset [{:keys [plugins] :as plugable} pname field value]
  (let [[plugable plugin] (pensure plugable pname)]
    (assoc
      plugable
      :plugins
      (assoc
        plugins
        field
        value))))
