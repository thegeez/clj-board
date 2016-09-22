(ns net.thegeez.clj-board.system.sql-database.migrator
  (:require [io.pedestal.log :as log]
            [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]))

(def version-migration
  (let [table :migration_version]
    {:up (fn [db]
           (jdbc/db-do-commands
            db (jdbc/create-table-ddl
                table
                [:id :int]
                [:version :int]))
           (jdbc/insert! db
                         table {:id 0
                                :version 1}))
     :down (fn [db]
             (jdbc/db-do-commands
              db (jdbc/drop-table-ddl
                  table)))}))

(defn sanity-check-migrations [migrations]
  (assert (every? (fn [m]
                    (and (vector? m)
                         (= 2 (count m))
                         (= (number? (first m)))
                         (= #{:up :down} (set (keys (second m))))
                         (fn? (:up (second m)))
                         (fn? (:down (second m)))))
                  migrations)
          "Migrations format is [[<id> {:up (fn [db] ...) :down (fn [db] ...)}")
  (assert
   (and (= 1 (ffirst migrations))
        (apply < (map first migrations)))
   "Migrations should start at id 1 and be increasing"))

(defn current-db-version [spec]
  (or (try (-> (jdbc/query spec ["SELECT * FROM migration_version"])
               first
               :version)
           (catch Exception e
             (log/debug :msg "Current-db-version fail: " :e e)
             nil))
      0))

(defn update-current-version [spec version]
  (try (jdbc/update! spec :migration_version
                     {:version version}
                     ["id = 0"])
       ;; might fail with the latest down migration that drops :migration_version
       (catch Exception e
         (let [msg (string/lower-case (.getMessage e))]
           (when-not (.contains msg "migration_version")
             (throw e))))))

(defn migrate!
  ([spec migrations] (migrate! spec migrations (first (last migrations))))
  ([spec migrations to-version]
     (sanity-check-migrations migrations)
     (let [current-version (current-db-version spec)
           todo (cond
                 (< current-version to-version)
                 (->> migrations
                      (drop-while (fn [[migration-version migration]]
                                    (<= migration-version current-version)))
                      (take-while (fn [[migration-version migration]]
                                    (<= migration-version to-version)))
                      (map (juxt first (comp :up second))))
                 (> current-version to-version)
                 (->> migrations
                      reverse
                      (drop-while (fn [[migration-version migration]]
                                    (< current-version migration-version)))
                      (take-while (fn [[migration-version migration]]
                                    (< to-version migration-version)))
                      (map (juxt first (comp :down second))))
                 :else nil)]
       (log/info :msg "Migrations to execute" :current-version current-version :to-version to-version)
       (doseq [[migration-version migration] todo]
         (log/debug :msg "Run migration" :migration-version migration-version)
         (try (migration spec)
              (update-current-version spec migration-version)
              (catch Exception e
                (log/error :msg "Migration failed" :migration-version migration-version :e e :stacktrace (with-out-str (.printStackTrace e)))
                (throw e))))
       (update-current-version spec to-version))))

(defrecord DevMigrator [database migrations]
  component/Lifecycle
  (start [component]
    (log/info :msg "Migrate database up")
    (let [spec (:spec database)]
      (migrate! spec migrations)
      component))
  (stop [component]
    (log/info :msg "Migrate database down")
    (migrate! (:spec database) migrations 0)
    component))

(defn dev-migrator [migrations]
  (map->DevMigrator {:migrations migrations}))

(defrecord Migrator [database migrations to-version]
  component/Lifecycle
  (start [component]
    (log/info :msg "Migrate database up to version" :to-version to-version)
    (let [spec (:spec database)]
      (if to-version
        (migrate! spec migrations to-version)
        (migrate! spec migrations))
      component))
  (stop [component]
    (log/info :msg "Not migrating down")
    component))

(defn migrator [migrations migrate-to-version]
  (map->Migrator {:migrations migrations :to-version migrate-to-version}))
