(ns net.thegeez.clj-board.system.sql-database
  (:require [io.pedestal.log :as log]
            [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as string])
  (:import [java.net URI]
           [com.mchange.v2.c3p0 ComboPooledDataSource]))

(defn db-url-for-heroku [db-url]
  (let [db-uri (URI. db-url)
        host (.getHost db-uri)
        port (.getPort db-uri)
        path (.getPath db-uri)
        [user password] (string/split (.getUserInfo db-uri) #":")]
    (str "jdbc:postgresql://" host ":" port path "?user=" user "&password=" password "&ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory")))


(defrecord SQLDatabase [db-connect-string]
  component/Lifecycle
  (start [component]
    (log/info :msg "Starting sql database")
    (let [cpds (doto (ComboPooledDataSource.)
                 (.setJdbcUrl db-connect-string))
          spec {:datasource cpds
                :connection-uri db-connect-string}]
      (try (jdbc/query spec ["VALUES 1"]) ;; derbydb
           (catch Exception e
             (try (jdbc/query spec ["SELECT NOW()"]) ;; postgres
                  (catch Exception e
                    (log/info :msg "DB connection failed:" :e e :stack-trace (with-out-str (.printStackTrace e)))))))
      (assoc component :spec spec)))

  (stop [component]
    (log/info :msg "Stopping sql database")
    (.close (:datasource (:spec component)))
    component))

(defn sql-database [db-connect-string]
  (map->SQLDatabase {:db-connect-string db-connect-string}))
