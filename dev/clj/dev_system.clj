(ns dev-system
  (:require [ns-tracker.core :refer [ns-tracker]]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [io.pedestal.http :as http]
            [net.thegeez.clj-board.system.server :as server]
            [net.thegeez.clj-board.system.messenger :as messenger]
            [net.thegeez.clj-board.system.websocket :as websocket]
            [net.thegeez.clj-board.system.sql-database :as sql-database]
            [net.thegeez.clj-board.system.sql-database.migrator :as migrator]
            [net.thegeez.clj-board.database.migrations :as migrations]
            [net.thegeez.clj-board.database.fixtures :as fixtures]
            [net.thegeez.clj-board.service :as service]
            [net.thegeez.clj-board.websocket-service :as websocket-service]))

(def modified-namespaces (ns-tracker "src/clj"))

(defn dev-service [service]
    (-> service ;; start with production configuration
      (merge {:env :dev
              ;; do not block thread that starts web server
              ::http/join? false
              ;; Routes can be a function that resolve routes,
              ;;  we can use this to set the routes to be reloadable
              ::http/routes #(do
                                 (doseq [ns-sym (modified-namespaces)]
                                   (require ns-sym :reload))
                                 (deref #'service/routes))
              ;; all origins are allowed in dev mode
              ::http/allowed-origins {:creds true :allowed-origins (constantly true)}})
      http/dev-interceptors))

(def dev-config {:db-connect-string "jdbc:derby:memory:thecljvector;create=true"
                 :port 8080
                 :migrations migrations/migrations
                 })

(defn dev-system []
  (log/info :msg "Hello world, this is the development system!")
  (let [{:keys [port db-connect-string migrations]} dev-config]
    (component/system-map
     :server (component/using
              (server/pedestal-component (dev-service
                                          (assoc service/service
                                                 ::http/port port
                                                 ::server/component->context {:database [:database :spec]})))
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
     :db-migrator (component/using
                   (migrator/dev-migrator migrations)
                   {:database :db})
     :fixtures (component/using
                (fixtures/fixtures)
                {:database :db
                 :db-migrator :db-migrator})
     )))
