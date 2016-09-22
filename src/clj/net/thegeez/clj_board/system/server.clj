(ns net.thegeez.clj-board.system.server
  (:require [io.pedestal.log :as log]
            [io.pedestal.http.cors :as cors]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.jetty.websockets :as ws]
            [io.pedestal.http.ring-middlewares :as middlewares]
            [io.pedestal.http.secure-headers :as secure-headers]
            [io.pedestal.interceptor :as interceptor]
            [com.stuartsierra.component :as component]))

(defn add-component-interceptors [service-map component]
  (update-in service-map [::http/interceptors]
             into (for [[k path] (::component->context service-map)]
                    (when-let [part (get-in component path)]
                      (interceptor/interceptor
                       {:enter (fn [context]
                                 (assoc context k part))})))))

(defn add-default-interceptors [service-map]
  ;; TODO copy all the config options and defaults from pedestal
  (let [{:keys [::http/interceptors
                ::http/default-interceptors
                ::http/allowed-origins
                ::http/resource-path
                ::http/router
                ::http/routes]} service-map]
    (assoc service-map ::http/interceptors
           (cond-> (or interceptors [])
                   (not (nil? allowed-origins))
                   (conj (cors/allow-origin allowed-origins))
                   true
                   (into [http/not-found
                          (body-params/body-params)
                          (middlewares/nested-params)
                          middlewares/keyword-params
                          route/query-params
                          (route/method-param [:form-params "_method"])
                          (middlewares/content-type)])
                   (not (nil? resource-path))
                   (conj (middlewares/resource resource-path))
                   default-interceptors
                   (into default-interceptors)
                   true
                   (into [(secure-headers/secure-headers {})
                          (route/router routes router)])
                   ))))

(defn add-websocket-component-to-service [service component]
  (let [ws-paths (:ws-paths (:websocket component))]
    (assoc service ::http/container-options
           {:context-configurator #(ws/add-ws-endpoints % ws-paths)})))

(defrecord PedestalComponent [service]
  component/Lifecycle
  (start [component]
    (log/info :msg "Starting Pedestal component" :service-env (:env service))
    (let [server (-> service
                     (update-in [::http/interceptors] (fnil vec []))
                     (add-component-interceptors component)
                     (add-default-interceptors)
                     (cond-> (:websocket component)
                       (add-websocket-component-to-service component))
                     http/create-server)]
      (assoc component :server server)))
  (stop [component]
    (log/info :msg "Stopping Pedestal production component")
    (dissoc component :server)))

(defn pedestal-component [service]
  (map->PedestalComponent {:service service}))

(defrecord JettyComponent [server]
  component/Lifecycle
  (start [component]
    (log/info :msg "Starting jetty component")
    (let [server (-> (:server server)
                     http/start)]
      (assoc component :server server)))
  (stop [component]
    (log/info :msg "Stopping jetty component")
    (when-let [server (:server component)]
      (http/stop (:server server)))
    (dissoc component :server)))

(defn jetty-component []
  (map->JettyComponent {}))
