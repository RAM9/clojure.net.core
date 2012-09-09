(defproject clojure.net "0.1.0-SNAPSHOT"
            :description "FIXME: write description"
            :url "http://example.com/FIXME"
            :license {:name "Eclipse Public License"
                      :url "http://www.eclipse.org/legal/epl-v10.html"}
            :dependencies [[org.clojure/clojure "1.4.0"]
                           [aleph "0.3.0-alpha2"]
                           [overtone/at-at "1.0.0"]
                           [org.clojure/tools.logging "0.2.3"]
                           [log4j "1.2.16" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]]
            :main clojure.net.core)

