(defproject uncens "0.1.0-SNAPSHOT"
            :description "UnCens lets you uncensor web pages and comment freely."
            :dependencies [
                           [org.clojure/clojure "1.4.0"]
                           [noir "1.3.0-beta2"]
                           ;[noir "1.2.2"]
                           ;[ring "1.1.6"]
                           [enlive "1.0.1"]
                           [hiccup "1.0.1"]
                           [org.clojure/java.jdbc "0.2.3"]
                           [mysql/mysql-connector-java "5.1.6"]
                           [org.xerial/sqlite-jdbc "3.7.2"]
                           [c3p0/c3p0 "0.9.1.2"] ;JDBC DataSources/Resource Pools
                           [korma "0.3.0-beta11"]
                           [net.sourceforge.nekohtml/nekohtml "1.9.15"] ; needed by boilerpipe
                           [xerces/xercesImpl "2.9.1"]
                           [de.l3s.boilerpipe "1.2.0"]
                           [org.jsoup/jsoup "1.7.1"]
                           [commons-validator/commons-validator "1.4.0"]
                           [clj-http "0.5.8"]
                           [org.clojars.scsibug/feedparser-clj "0.4.0"]
                           [com.draines/postal "1.9.0"]
                           [clj-time "0.4.4"]
                           [crypto-random "1.1.0"]
                          ]
            :plugins [[lein-ring "0.7.5"]
                      [lein-beanstalk "0.2.6"]]
            :dev-dependencies [[lein-ring "0.7.5"]]
            :ring {:handler uncens.server/handler})