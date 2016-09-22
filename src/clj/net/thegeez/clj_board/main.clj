(ns net.thegeez.clj-board.main
  (:require [io.pedestal.log :as log]
            [io.pedestal.http :as http]
            [com.stuartsierra.component :as component]
            [net.thegeez.clj-board.system.server :as server]
            [net.thegeez.clj-board.system.messenger :as messenger]
            [net.thegeez.clj-board.system.websocket :as websocket]
            [net.thegeez.clj-board.system.sql-database :as sql-database]
            [net.thegeez.clj-board.system.sql-database.migrator :as migrator]
            [net.thegeez.clj-board.database.migrations :as migrations]
            [net.thegeez.clj-board.database.fixtures :as fixtures]
            [net.thegeez.clj-board.service :as service]
            [net.thegeez.clj-board.websocket-service :as websocket-service])
  (:gen-class))

(defn -main [port database-url & args]
  (log/info :msg "Hello world this is the production system")
  (let [port (try (Long/parseLong port)
                  (catch Exception _
                    (assert false "Port argument incorrect")))
        db-connect-string (do
                            (assert (.startsWith database-url "postgres:")
                                    (str "Something is wrong with the database argument: " database-url))
                            (sql-database/db-url-for-heroku database-url))

        system (component/system-map
                :server (component/using
                         (server/pedestal-component (assoc service/service
                                                           ::http/port port
                                                           ::server/component->context {:database [:database :spec]}))
                         {:database :db
                          :websocket :websocket})
                :messenger (messenger/messenger-component)
                :websocket (component/using
                            (websocket/websocket-component websocket-service/handler)
                            {:database :db
                             :messenger :messenger})
                :jetty (component/using
                        (server/jetty-component)
                        [:server])
                :db (sql-database/sql-database db-connect-string)
                )]
    (component/start system)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn []
                                 (log/info :main "Shutting down main")
                                 (component/stop system))))))

(defn reset-db []
  ;; run through heroku repl: heroku run java -cp target/clj-board-prod-standalone.jar clojure.main
  ;; nuke db and run migrations & fixtures
  (let [db-url (System/getenv "DATABASE_URL")
        _ (assert (.startsWith db-url "postgres:")
                  (str "Something is wrong with the database argument: " db-url))
        db-connect-string (sql-database/db-url-for-heroku db-url)
        db-spec {:connection-uri db-connect-string}]
    (log/info :msg "Running the database migrator to clean database")
    (migrator/migrate! db-spec migrations/migrations 0)
    (log/info :msg "Running the database migrator to build database")
    (migrator/migrate! db-spec migrations/migrations)
    (log/info :msg "Database migrator done")
    (log/info :msg "Inserting fixtures")
    (fixtures/insert-fixtures! db-spec)
    (log/info :msg "Inserting fixtures done")
    (log/info :msg "Reseting database done")))
